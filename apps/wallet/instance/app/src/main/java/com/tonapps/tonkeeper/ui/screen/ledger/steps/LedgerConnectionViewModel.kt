package com.tonapps.tonkeeper.ui.screen.ledger.steps

import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.tonapps.log.L
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tonapps.ledger.ble.BleManager
import com.tonapps.ledger.ble.BleManagerFactory
import com.tonapps.blockchain.ton.TonNetwork
import com.tonapps.blockchain.ton.contract.WalletVersion
import com.tonapps.blockchain.ton.extensions.equalsRawAddress
import com.tonapps.blockchain.ton.extensions.toAccountId
import com.tonapps.blockchain.ton.extensions.toWalletAddress
import com.tonapps.core.helper.TransactionSentAnalytics
import com.tonapps.extensions.MutableEffectFlow
import com.tonapps.icu.Coins
import com.tonapps.icu.CurrencyFormatter
import com.tonapps.ledger.LedgerException
import com.tonapps.ledger.ble.BleTransport
import com.tonapps.ledger.ble.ConnectedDevice
import com.tonapps.ledger.devices.DeviceModelId
import com.tonapps.ledger.devices.Devices
import com.tonapps.ledger.ton.AccountPath
import com.tonapps.ledger.ton.KnownJetton
import com.tonapps.ledger.ton.LedgerAccount
import com.tonapps.ledger.ton.LedgerConnectData
import com.tonapps.ledger.ton.LedgerKnownJettons
import com.tonapps.ledger.ton.OpenAppResult
import com.tonapps.ledger.ton.TonPayloadFormat
import com.tonapps.ledger.ton.TonTransport
import com.tonapps.ledger.ton.Transaction
import com.tonapps.ledger.transport.TransportStatusException
import com.tonapps.ledger.usb.LedgerUsb
import com.tonapps.tonkeeper.extensions.isVersionLowerThan
import com.tonapps.tonkeeper.ui.screen.init.WalletImportAnalytics
import com.tonapps.tonkeeper.ui.screen.init.list.AccountItem
import com.tonapps.tonkeeper.ui.screen.ledger.steps.list.Item
import com.tonapps.tonkeeper.ui.screen.ledger.toTransactionSentLedgerModel
import com.tonapps.tonkeeper.ui.screen.ledger.toWalletFlowLedgerModel
import com.tonapps.uikit.list.ListCell
import com.tonapps.wallet.api.API
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.wallet.data.collectibles.CollectiblesRepository
import com.tonapps.wallet.data.collectibles.entities.NftEntity
import com.tonapps.wallet.data.settings.SettingsRepository
import com.tonapps.wallet.data.token.TokenRepository
import com.tonapps.wallet.data.token.entities.AccountTokenEntity
import com.tonapps.wallet.localization.Localization
import io.ktor.util.reflect.instanceOf
import io.tonapi.models.Account
import io.tonapi.models.AccountStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retry
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ton.block.AddrStd
import uikit.extensions.collectFlow
import uikit.extensions.context
import java.math.BigDecimal
import java.math.BigInteger

private const val TAG = "LEDGER"

class LedgerConnectionViewModel(
    app: Application,
    private val accountRepository: AccountRepository,
    private val tokenRepository: TokenRepository,
    private val collectiblesRepository: CollectiblesRepository,
    private val settingsRepository: SettingsRepository,
    private val api: API,
    private val importAnalytics: WalletImportAnalytics,
) : AndroidViewModel(app) {
    private val bleManager: BleManager = BleManagerFactory.newInstance(context)
    private val usbLedger = LedgerUsb(context)
    private var pollTonAppJob: Job? = null
    private var disconnectTimeoutJob: Job? = null

    private var _walletId: String? = null
    private var _transaction: Transaction? = null
    private var _proofData: ProofData? = null
    private var _connectedDevice: ConnectedDevice? = null
    private var _tonTransport: TonTransport? = null
    private var _transactionIndex: Int = 0
    private var _transactionCount: Int = 0

    private val _connectionType = MutableStateFlow(
        if (settingsRepository.ledgerConnectUsb) {
            LedgerConnectionType.USB
        } else {
            LedgerConnectionType.BLUETOOTH
        }
    )
    val connectionType = _connectionType.asStateFlow()

    private val _connectionState = MutableEffectFlow<ConnectionState>()

    private val _eventFlow = MutableEffectFlow<LedgerEvent>()
    val eventFlow = _eventFlow.asSharedFlow()

    val currentStepFlow = _connectionState.map { state ->
        when (state) {
            ConnectionState.Idle -> LedgerStep.CONNECT
            ConnectionState.Scanning -> LedgerStep.CONNECT
            is ConnectionState.Connected -> LedgerStep.OPEN_TON_APP
            is ConnectionState.OpeningTonApp -> LedgerStep.APPROVE_TON_APP
            is ConnectionState.TonAppOpened -> if (_walletId != null) {
                LedgerStep.CONFIRM_TX
            } else {
                LedgerStep.DONE
            }
            is ConnectionState.Disconnected -> LedgerStep.CONNECT
            ConnectionState.Signed -> LedgerStep.DONE
        }
    }

    val uiItemsFlow = currentStepFlow.map { step ->
        createList(step)
    }

    val displayTextFlow = currentStepFlow.map { state ->
        when {
            _walletId != null && (state == LedgerStep.CONFIRM_TX || state == LedgerStep.DONE) -> "Review"
            else -> "TON ready"
        }
    }

    private val _bluetoothState = MutableSharedFlow<Int>(replay = 1, extraBufferCapacity = 1)
    val bluetoothState = _bluetoothState.asSharedFlow()

    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (BluetoothAdapter.ACTION_STATE_CHANGED == intent.action) {
                val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                _bluetoothState.tryEmit(state)
            }
        }
    }

    private var ledgerUsbJob: Job? = null

    private val isBluetooth: Boolean
        get() = _connectionType.value == LedgerConnectionType.BLUETOOTH

    private val isPairing: Boolean
        get() = _walletId == null

    val connectedLedgerModel: DeviceModelId?
        get() = _connectedDevice?.model?.id

    init {
        collectFlow(connectionType) { type ->
            if (type == LedgerConnectionType.USB) {
                stopBle()
                startWaitUsbDevice()
                settingsRepository.ledgerConnectUsb = true
            } else {
                stopUsb()
                startWaitBleDevice()
                settingsRepository.ledgerConnectUsb = false
            }
        }
    }

    private fun startWaitUsbDevice() {
        _connectionState.tryEmit(ConnectionState.Scanning)
        ledgerUsbJob = usbLedger
            .deviceFlow()
            .distinctUntilChanged()
            .map(usbLedger::connectDevice)
            .map { transport ->
                _connectionState.tryEmit(ConnectionState.Connected)
                setConnectedDevice(
                    ConnectedDevice(
                        transport.serialNumber,
                        Devices.fromUSBProductId(transport.productId)
                    )
                )
                usbLedger.createTonTransport(transport)
            }
            .filterNotNull()
            .map {
                delay(1000)
                waitForTonAppOpen(it, LedgerConnectionType.USB)
            }
            .retry { cause ->
                disconnectUsb()

                cause is LedgerException.USBWriteException || cause is LedgerException.USBReadException || cause is LedgerException
            }
            .catch {
                disconnectUsb()

                if (it is SecurityException) {
                    _eventFlow.tryEmit(LedgerEvent.Error(context.getString(Localization.ledger_usb_permission_error)))
                }
            }
            .launchIn(viewModelScope)
    }

    private fun stopUsb() {
        ledgerUsbJob?.cancel()
        ledgerUsbJob = null
    }

    private fun disconnectUsb() {
        pollTonAppJob?.cancel()
        setConnectedDevice(null)
        setTonTransport(null, LedgerConnectionType.USB)
        _connectionState.tryEmit(ConnectionState.Disconnected())
    }

    private fun startWaitBleDevice() {
        val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        context.registerReceiver(bluetoothReceiver, filter)

        // Get BluetoothManager and BluetoothAdapter
        val bluetoothManager =
            context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter

        // Emit initial state
        val initialState = bluetoothAdapter?.state ?: BluetoothAdapter.ERROR
        _bluetoothState.tryEmit(initialState)
        _connectionState.tryEmit(ConnectionState.Idle)
    }

    private fun unregisterReceiverBle() {
        try {
            context.unregisterReceiver(bluetoothReceiver)
        } catch (ignored: Throwable) {
        }
    }

    private fun stopBle() {
        unregisterReceiverBle()

        try {
            bleManager.stopScanning()
        } catch (_: Exception) {
        }

        disconnectBle()
    }

    fun disconnectBle() {
        disconnectTimeoutJob?.cancel()
        pollTonAppJob?.cancel()
        bleManager.disconnect()
        setConnectedDevice(null)
        setTonTransport(null, LedgerConnectionType.BLUETOOTH)
        _connectionState.tryEmit(ConnectionState.Disconnected())
    }

    fun onBleReady() {
        if (_connectionType.value != LedgerConnectionType.BLUETOOTH) {
            return
        }

        if (_walletId != null) {
            viewModelScope.launch {
                try {
                    val ledgerConfig = getLedgerConfig()
                    connectBle(ledgerConfig.deviceId)
                } catch (e: Exception) {
                    _eventFlow.tryEmit(
                        LedgerEvent.Error(
                            e.message ?: context.getString(Localization.error)
                        )
                    )
                }
            }
        } else {
            _connectionState.tryEmit(ConnectionState.Scanning)

            val started = bleManager.startScanning { devices ->
                val device = devices.firstOrNull() ?: return@startScanning

                try {
                    bleManager.stopScanning()
                } catch (_: Exception) {
                }

                connectBle(device.id)
            }

            if (!started) {
                _connectionState.tryEmit(ConnectionState.Idle)
                _eventFlow.tryEmit(
                    LedgerEvent.Error(context.getString(Localization.something_went_wrong))
                )
            }
        }
    }

    private fun connectBle(deviceId: String) {
        if (_connectionType.value != LedgerConnectionType.BLUETOOTH) {
            return
        }

        L.d(TAG, "Connecting to device $deviceId")

        bleManager.connect(address = deviceId, onConnectError = {
            pollTonAppJob?.cancel()

            viewModelScope.launch {
                delay(1000)
                L.d(TAG, "Reconnecting to device $deviceId")
                connectBle(deviceId)
            }

            disconnectTimeoutJob?.cancel()
            disconnectTimeoutJob = viewModelScope.launch {
                delay(4000)
                setTonTransport(null, LedgerConnectionType.BLUETOOTH)
                setConnectedDevice(null)
                _connectionState.tryEmit(ConnectionState.Disconnected())
            }
        }, onConnectSuccess = {
            disconnectTimeoutJob?.cancel()
            val device = ConnectedDevice(deviceId, Devices.fromServiceUuid(it.serviceId!!))
            setConnectedDevice(device)
            _connectionState.tryEmit(ConnectionState.Connected)
            if (isPairing) {
                val ledgerModel = device.model.id.toWalletFlowLedgerModel()
                viewModelScope.launch { importAnalytics.trackLedgerBluetoothConnected(ledgerModel) }
            }

            val tonTransport = TonTransport(BleTransport(bleManager))

            waitForTonAppOpen(tonTransport, LedgerConnectionType.BLUETOOTH)
        })
    }

    fun setConnectionType(type: LedgerConnectionType) {
        _connectionType.value = type
    }

    override fun onCleared() {
        super.onCleared()
        unregisterReceiverBle()
        ledgerUsbJob?.cancel()
        _tonTransport?.close()
    }

    fun setSignData(
        transaction: Transaction,
        walletId: String,
        transactionIndex: Int,
        transactionCount: Int
    ) {
        _transaction = transaction
        _walletId = walletId
        _transactionIndex = transactionIndex
        _transactionCount = transactionCount
    }

    fun setProofData(domain: String, timestamp: BigInteger, payload: String, walletId: String) {
        _proofData = ProofData(domain, timestamp, payload)
        _walletId = walletId
    }

    private fun hasConnectedDevice(): Boolean {
        return connectionType.value == LedgerConnectionType.USB || _connectedDevice != null
    }

    private fun setConnectedDevice(device: ConnectedDevice?) {
        _connectedDevice = device
    }

    private fun setTonTransport(transport: TonTransport?, type: LedgerConnectionType) {
        if (_connectionType.value != type) {
            return
        }

        if (transport != null) {
            _connectionState.tryEmit(ConnectionState.TonAppOpened)
            if (isPairing) {
                val ledgerModel = connectedLedgerModel?.toWalletFlowLedgerModel()
                viewModelScope.launch { importAnalytics.trackLedgerAppOpened(ledgerModel) }
            }
        }
        _tonTransport = transport

        _eventFlow.tryEmit(LedgerEvent.Ready(_tonTransport != null))
    }

    suspend fun getConnectData() {
        if (_tonTransport == null || !hasConnectedDevice()) {
            return
        }
        try {
            _eventFlow.tryEmit(LedgerEvent.Loading(true))
            val accounts = mutableListOf<LedgerAccount>()
            for (i in 0 until 20) {
                val account = _tonTransport!!.getAccount(AccountPath(i))
                accounts.add(account)
            }

            val ledgerConnectData = LedgerConnectData(
                accounts = accounts,
                deviceId = _connectedDevice!!.deviceId,
                model = _connectedDevice!!.model
            )

            try {
                resolveWallets(ledgerConnectData)
            } catch (e: Exception) {
                _eventFlow.tryEmit(LedgerEvent.Loading(false))
            }
        } catch (e: Exception) {
            _eventFlow.tryEmit(LedgerEvent.Error(context.getString(Localization.error)))
            _eventFlow.tryEmit(LedgerEvent.Loading(false))
        }
    }

    suspend fun signDomainProof() {
        viewModelScope.launch {
            try {
                if (_tonTransport == null || !hasConnectedDevice() || _walletId == null || _proofData == null) {
                    throw IllegalStateException("Cannot sign domain proof: Ledger transport, connected device, wallet id or proof data is missing")
                }

                val version = _tonTransport!!.getVersion()
                val requiredVersion = "2.1.0"

                if (version.isVersionLowerThan(requiredVersion)) {
                    _eventFlow.tryEmit(LedgerEvent.WrongVersion(requiredVersion))
                    return@launch
                }

                val ledgerConfig = getLedgerConfig()

                val proof = _tonTransport!!.signAddressProof(
                    AccountPath(ledgerConfig.accountIndex),
                    _proofData!!.domain,
                    _proofData!!.timestamp,
                    _proofData!!.payload,
                )

                _connectionState.tryEmit(ConnectionState.Signed)
                _eventFlow.tryEmit(LedgerEvent.SignedProof(proof))
            } catch (e: Exception) {
                L.d(TAG, "Error signing transaction", e)
                if (e.instanceOf(TransportStatusException.DeniedByUser::class)) {
                    _eventFlow.tryEmit(LedgerEvent.Rejected)
                } else {
                    _eventFlow.tryEmit(
                        LedgerEvent.Error(
                            e.message ?: context.getString(Localization.error)
                        )
                    )
                }

            }
        }
    }

    suspend fun signTransaction() {
        viewModelScope.launch {
            try {
                if (_tonTransport == null || !hasConnectedDevice() || _walletId == null || _transaction == null) {
                    throw IllegalStateException("Cannot sign transaction: Ledger transport, connected device, wallet id or transaction is missing")
                }

                val version = _tonTransport!!.getVersion()
                val requiredVersion = getRequiredVersion(_transaction!!, version)

                if (version.isVersionLowerThan(requiredVersion)) {
                    _eventFlow.tryEmit(LedgerEvent.WrongVersion(requiredVersion))
                    return@launch
                }

                val wallet = getWallet()
                val ledgerConfig = getLedgerConfig()
                val path = AccountPath(ledgerConfig.accountIndex)
                val transaction = withKnownJetton(_transaction!!, wallet, version)

                val signedBody = try {
                    _tonTransport!!.signTransaction(path, transaction)
                } catch (e: TransportStatusException.TxParsingFailed) {
                    if (transaction === _transaction) {
                        throw e
                    }
                    _tonTransport!!.signTransaction(path, _transaction!!)
                }

                TransactionSentAnalytics.rememberLedgerModel(
                    walletId = wallet.id,
                    ledgerModel = connectedLedgerModel?.toTransactionSentLedgerModel(),
                )
                _connectionState.tryEmit(ConnectionState.Signed)
                _eventFlow.tryEmit(LedgerEvent.SignedTransaction(signedBody))
            } catch (e: Exception) {
                L.d(TAG, "Error signing transaction", e)
                if (e.instanceOf(TransportStatusException.DeniedByUser::class)) {
                    _eventFlow.tryEmit(LedgerEvent.Rejected)
                } else if (e.instanceOf(TransportStatusException.BlindSigningDisabled::class)) {
                    _eventFlow.tryEmit(
                        LedgerEvent.Error(context.getString(Localization.ledger_blind_signing_error))
                    )
                } else {
                    _eventFlow.tryEmit(
                        LedgerEvent.Error(e.message ?: context.getString(Localization.error))
                    )
                }

            }
        }
    }

    private fun getRequiredVersion(transaction: Transaction, currentVersion: String): String {
        val defaultVersion = "2.0.0"

        if (transaction.payload == null) {
            return defaultVersion
        }

        if (currentVersion == "2.7.0") {
            return "2.8.1"
        }

        return when (transaction.payload!!::class) {
            TonPayloadFormat.JettonTransfer::class -> {
                defaultVersion
            }

            TonPayloadFormat.Comment::class -> {
                defaultVersion
            }

            else -> {
                "2.1.0"
            }
        }
    }

    private suspend fun getWallet(): WalletEntity {
        return accountRepository.getWallets().find { it.id == _walletId }
            ?: throw IllegalStateException("Wallet not found")
    }

    private suspend fun getLedgerConfig(): WalletEntity.Ledger {
        return getWallet().ledger
            ?: throw IllegalStateException("Ledger data not found")
    }

    private suspend fun withKnownJetton(
        transaction: Transaction,
        wallet: WalletEntity,
        appVersion: String
    ): Transaction {
        val payload = transaction.payload as? TonPayloadFormat.JettonTransfer ?: return transaction
        if (payload.knownJetton != null) {
            return transaction
        }
        if (_connectedDevice?.model?.id == DeviceModelId.NANO_S) {
            return transaction
        }
        val jettonWalletAddress = transaction.destination as? AddrStd ?: return transaction
        val jettonId = try {
            resolveKnownJettonId(wallet, jettonWalletAddress)
        } catch (e: Exception) {
            L.d(TAG, "Failed to resolve known jetton", e)
            null
        } ?: return transaction
        if (appVersion.isVersionLowerThan(LedgerKnownJettons.minAppVersion(jettonId))) {
            return transaction
        }
        val knownJetton = KnownJetton(jettonId, wallet.contract.workchain)
        return transaction.copy(payload = payload.copy(knownJetton = knownJetton))
    }

    private suspend fun resolveKnownJettonId(wallet: WalletEntity, jettonWalletAddress: AddrStd): Int? {
        val jettonWalletId = jettonWalletAddress.toAccountId()
        val currency = settingsRepository.currency
        val tokens = tokenRepository.getLocal(currency, wallet.accountId, wallet.network).ifEmpty {
            tokenRepository.get(currency, wallet.accountId, wallet.network) ?: emptyList()
        }
        val token = tokens.firstOrNull {
            !it.isTon && it.balance.walletAddress.equalsRawAddress(jettonWalletId)
        } ?: return null
        return LedgerKnownJettons.jettonId(AddrStd.parse(token.address))
    }

    private suspend fun resolveWallets(ledgerData: LedgerConnectData) =
        withContext(Dispatchers.IO) {
            val addedDeviceAccountIndexes = accountRepository.getWallets()
                .filter { wallet -> wallet.ledger?.deviceId == ledgerData.deviceId }
                .map { it.ledger!!.accountIndex }

            val deferredAccounts = mutableListOf<Deferred<Account?>>()
            for (account in ledgerData.accounts) {
                deferredAccounts.add(async {
                    api.resolveAccount(account.address.toAccountId(), TonNetwork.MAINNET)
                })
            }

            val deferredTokens = mutableListOf<Deferred<List<AccountTokenEntity>>>()
            for (account in ledgerData.accounts) {
                deferredTokens.add(async {
                    tokenRepository.get(
                        settingsRepository.currency,
                        account.address.toAccountId(),
                        TonNetwork.MAINNET
                    ) ?: emptyList()
                })
            }

            val deferredCollectibles = mutableListOf<Deferred<List<NftEntity>>>()
            for (account in ledgerData.accounts) {
                deferredCollectibles.add(async {
                    collectiblesRepository.get(
                        account.address.toAccountId(),
                        TonNetwork.MAINNET
                    ) ?: emptyList()
                })
            }

            val items = mutableListOf<AccountItem>()
            for ((index, ledgerAccount) in ledgerData.accounts.withIndex()) {
                val account = deferredAccounts[index].await()
                val balance = Coins.of(account.let { it?.balance } ?: 0)
                val tokens = deferredTokens[index].await()
                    .filter { it.balance.value.value > BigDecimal.ZERO }
                val hasTokens = tokens.isNotEmpty()
                val hasCollectibles = deferredCollectibles[index].await().isNotEmpty()
                val alreadyAdded = addedDeviceAccountIndexes.contains(ledgerAccount.path.index)
                val item = AccountItem(
                    address = ledgerAccount.address.toWalletAddress(false),
                    name = account?.name,
                    walletVersion = WalletVersion.V4R2,
                    balanceFormat = CurrencyFormatter.format("TON", balance),
                    tokens = hasTokens,
                    collectibles = hasCollectibles,
                    selected = false,
                    position = ListCell.getPosition(ledgerData.accounts.size, index),
                    ledgerIndex = ledgerAccount.path.index,
                    ledgerAdded = alreadyAdded,
                    initialized = account != null && (account.status == AccountStatus.active || account.status == AccountStatus.frozen)
                )
                items.add(item)
            }

            _eventFlow.tryEmit(LedgerEvent.Next(connectData = ledgerData, accounts = items))
        }

    private fun waitForTonAppOpen(tonTransport: TonTransport, type: LedgerConnectionType) {
        setTonTransport(null, type)
        pollTonAppJob?.cancel()
        pollTonAppJob = viewModelScope.launch {
            try {
                var quitAttempted = false
                var openAttempted = false
                while (true) {
                    val app = try {
                        tonTransport.currentApp()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        null
                    }
                    if (app == null) {
                        L.d(TAG, "Waiting for device to respond")
                    } else if (app.isTonApp) {
                        setTonTransport(tonTransport, type)
                        return@launch
                    } else if (app.isDashboard) {
                        if (!openAttempted) {
                            openAttempted = requestOpenTonApp(tonTransport)
                        }
                    } else if (!quitAttempted) {
                        quitAttempted = true
                        quitForeignApp(tonTransport)
                    }
                    delay(1000)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                L.d(TAG, "Error waiting for TON app", e)
                _connectionState.tryEmit(ConnectionState.Disconnected())
            }
        }
    }

    private suspend fun requestOpenTonApp(tonTransport: TonTransport): Boolean {
        _connectionState.tryEmit(ConnectionState.OpeningTonApp)
        return try {
            val result = tonTransport.openTonApp()
            if (result != OpenAppResult.Opened) {
                _connectionState.tryEmit(ConnectionState.Connected)
            }
            true
        } catch (e: CancellationException) {
            throw e
        } catch (_: TransportStatusException.LockedDevice) {
            _connectionState.tryEmit(ConnectionState.Connected)
            false
        } catch (e: Exception) {
            L.d(TAG, "Error opening TON app", e)
            _connectionState.tryEmit(ConnectionState.Connected)
            true
        }
    }

    private suspend fun quitForeignApp(tonTransport: TonTransport) {
        try {
            tonTransport.quitApp()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            L.d(TAG, "Error quitting current app", e)
        }
    }

    private fun createList(currentStep: LedgerStep): List<Item> {
        val uiItems = mutableListOf<Item>()

        if (isBluetooth) {
            uiItems.add(
                Item.Step(
                    context.getString(Localization.ledger_connect),
                    currentStep !== LedgerStep.CONNECT,
                    currentStep == LedgerStep.CONNECT
                )
            )
        } else {
            uiItems.add(
                Item.Step(
                    context.getString(Localization.ledger_usb_connect),
                    currentStep !== LedgerStep.CONNECT,
                    currentStep == LedgerStep.CONNECT
                )
            )
        }

        uiItems.add(
            Item.Step(
                context.getString(
                    if (currentStep == LedgerStep.APPROVE_TON_APP) {
                        Localization.ledger_approve_ton_app
                    } else {
                        Localization.ledger_open_ton_app
                    }
                ),
                currentStep == LedgerStep.DONE || currentStep == LedgerStep.CONFIRM_TX,
                currentStep == LedgerStep.OPEN_TON_APP || currentStep == LedgerStep.APPROVE_TON_APP,
                _walletId == null
            )
        )

        if (_walletId == null) {
            return uiItems
        }

        if (_transactionCount > 1) {
            for (i in 0 until _transactionCount) {
                uiItems.add(
                    Item.Step(
                        context.getString(Localization.ledger_confirm_tx_step, i + 1),
                        _transactionIndex > i,
                        currentStep == LedgerStep.CONFIRM_TX && _transactionIndex == i
                    )
                )
            }
        } else {
            uiItems.add(
                Item.Step(
                    if (_proofData !== null) {
                        context.getString(Localization.ledger_confirm_proof)
                    } else {
                        context.getString(Localization.ledger_confirm_tx)
                    },
                    currentStep == LedgerStep.DONE,
                    currentStep == LedgerStep.CONFIRM_TX
                )
            )
        }

        return uiItems
    }
}