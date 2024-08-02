package com.tonapps.tonkeeper.ui.screen.init

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.tonapps.blockchain.ton.TonMnemonic
import com.tonapps.blockchain.ton.TonNetwork
import com.tonapps.blockchain.ton.contract.w5.WalletV5R1Contract
import com.tonapps.blockchain.ton.contract.WalletVersion
import com.tonapps.blockchain.ton.extensions.toRawAddress
import com.tonapps.blockchain.ton.extensions.toWalletAddress
import com.tonapps.emoji.Emoji
import com.tonapps.extensions.MutableEffectFlow
import com.tonapps.icu.Coins
import com.tonapps.icu.CurrencyFormatter
import com.tonapps.ledger.ton.LedgerConnectData
import com.tonapps.tonkeeper.core.AnalyticsHelper
import com.tonapps.tonkeeper.extensions.toast
import com.tonapps.tonkeeper.ui.screen.init.list.AccountItem
import com.tonapps.uikit.list.ListCell
import com.tonapps.wallet.api.API
import com.tonapps.wallet.api.entity.AccountDetailsEntity
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.account.Wallet
import com.tonapps.wallet.data.account.WalletColor
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.backup.BackupRepository
import com.tonapps.wallet.data.backup.entities.BackupEntity
import com.tonapps.wallet.data.passcode.PasscodeManager
import com.tonapps.wallet.data.passcode.dialog.PasscodeDialog
import com.tonapps.wallet.data.rn.RNLegacy
import com.tonapps.wallet.data.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ton.api.pk.PrivateKeyEd25519
import org.ton.api.pub.PublicKeyEd25519
import org.ton.block.AddrStd
import org.ton.crypto.hex
import org.ton.mnemonic.Mnemonic
import uikit.extensions.context
import uikit.navigation.Navigation

@OptIn(FlowPreview::class)
class InitViewModel(
    private val scope: CoroutineScope,
    private val type: InitArgs.Type,
    application: Application,
    private val passcodeManager: PasscodeManager,
    private val accountRepository: AccountRepository,
    private val settingsRepository: SettingsRepository,
    private val api: API,
    private val backupRepository: BackupRepository,
    private val rnLegacy: RNLegacy,
    savedStateHandle: SavedStateHandle
): AndroidViewModel(application) {

    private val passcodeAfterSeed = false // type == InitArgs.Type.Import || type == InitArgs.Type.Testnet
    private val savedState = InitModelState(savedStateHandle)
    private val testnet: Boolean = type == InitArgs.Type.Testnet

    private val tonNetwork: TonNetwork
        get() = if (testnet) TonNetwork.TESTNET else TonNetwork.MAINNET

    private val _uiTopOffset = MutableStateFlow(0)
    val uiTopOffset = _uiTopOffset.asStateFlow()

    private val _eventFlow = MutableEffectFlow<InitEvent>()
    val eventFlow = _eventFlow.asSharedFlow().filterNotNull()

    private val _routeFlow = MutableEffectFlow<InitRoute>()
    val routeFlow = _routeFlow.asSharedFlow().filterNotNull()

    private val _watchAccountResolveFlow = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val watchAccountFlow = _watchAccountResolveFlow.asSharedFlow()
        .debounce(1000)
        .filter { it.isNotBlank() }
        .map {
            val account = api.resolveAddressOrName(it, testnet)
            if (account == null || !account.active) {
                setWatchAccount(null)
                return@map null
            }
            setWatchAccount(account)
            account
        }.flowOn(Dispatchers.IO)

    private val _accountsFlow = MutableEffectFlow<List<AccountItem>?>()
    val accountsFlow = _accountsFlow.asSharedFlow().filterNotNull()

    private val hasPushPermission: Boolean
        get() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                return ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            }
            return true
        }

    init {
        viewModelScope.launch {
            if (!passcodeManager.hasPinCode()) {
                routeTo(InitRoute.CreatePasscode)
            } else {
                startWalletFlow()
            }
        }
    }

    private fun routeTo(route: InitRoute) {
        _routeFlow.tryEmit(route)
    }

    fun toggleAccountSelection(address: String) {
        val items = getAccounts().toMutableList()
        val oldItem = items.find { it.address.toRawAddress() == address } ?: return
        val newItem = oldItem.copy(selected = !oldItem.selected)
        items[items.indexOf(oldItem)] = newItem
        setAccounts(items)
    }

    private fun startWalletFlow() {
        if (type == InitArgs.Type.Watch) {
            routeTo(InitRoute.WatchAccount)
        } else if (type == InitArgs.Type.New) {
            routeTo(InitRoute.LabelAccount)
        } else if (type == InitArgs.Type.Import || type == InitArgs.Type.Testnet) {
            routeTo(InitRoute.ImportWords)
        } else if (type == InitArgs.Type.Signer || type == InitArgs.Type.SignerQR) {
            _eventFlow.tryEmit(InitEvent.Loading(true))
            scope.launch(Dispatchers.IO) {
                resolveWallets(savedState.publicKey!!)
                _eventFlow.tryEmit(InitEvent.Loading(false))
            }
        } else if (type == InitArgs.Type.Ledger) {
            routeTo(InitRoute.SelectAccount)
        }
    }

    fun setUiTopOffset(offset: Int) {
        _uiTopOffset.value = offset
    }

    fun routePopBackStack() {
        _eventFlow.tryEmit(InitEvent.Back)
    }

    fun resolveWatchAccount(value: String) {
        _watchAccountResolveFlow.tryEmit(value)
    }

    fun setPasscode(passcode: String) {
        savedState.passcode = passcode

        routeTo(InitRoute.ReEnterPasscode)
    }

    fun reEnterPasscode(passcode: String) {
        val valid = savedState.passcode == passcode
        if (!valid) {
            routePopBackStack()
            return
        }
        startWalletFlow()
    }

    suspend fun setMnemonic(words: List<String>) {
        savedState.mnemonic = words
        resolveWallets(words)
    }

    fun setPublicKey(publicKey: PublicKeyEd25519?) {
        savedState.publicKey = publicKey
    }

    fun setLedgerConnectData(connectData: LedgerConnectData) {
        savedState.ledgerConnectData = connectData
        setLabelName(connectData.model.productName)
    }

    private suspend fun resolveWallets(mnemonic: List<String>) = withContext(Dispatchers.IO) {
        val seed = Mnemonic.toSeed(mnemonic)
        val privateKey = PrivateKeyEd25519(seed)
        val publicKey = privateKey.publicKey()
        resolveWallets(publicKey)
    }

    private suspend fun resolveWallets(publicKey: PublicKeyEd25519) = withContext(Dispatchers.IO) {
        Log.d("InitViewModelLog", "resolveWallets publicKey: $publicKey")
        try {
            val accounts = api.resolvePublicKey(publicKey, testnet).filter {
                it.isWallet && it.walletVersion != WalletVersion.UNKNOWN && it.active
            }.sortedByDescending { it.walletVersion.index }.toMutableList()

            Log.d("InitViewModelLog", "resolveWallets accounts: $accounts")

            if (accounts.count { it.walletVersion == WalletVersion.V5R1 } == 0) {
                val contract = WalletV5R1Contract(publicKey, tonNetwork)
                accounts.add(0, AccountDetailsEntity(contract, testnet))
            }

            val list = accounts.mapIndexed { index, account ->
                getAccountItem(account, ListCell.getPosition(accounts.size, index))
            }

            val items = mutableListOf<AccountItem>()
            for (account in list) {
                items.add(account)
            }
            setAccounts(items)

            if (items.size > 1) {
                routeTo(InitRoute.SelectAccount)
            } else {
                routeTo(InitRoute.LabelAccount)
            }
        } catch (e: Throwable) {
            Log.e("InitViewModelLog", "error", e)
        }
    }

    private suspend fun getAccountItem(
        account: AccountDetailsEntity,
        position: ListCell.Position,
    ): AccountItem = withContext(Dispatchers.IO) {
        val tokens = api.getJettonsBalances(account.address, testnet)
        val nftItems = api.getNftItems(account.address, testnet, 1)
        val balance = Coins.of(account.balance)
        val hasTokens = tokens.isNotEmpty()
        val hasNftItems = nftItems.isNotEmpty()
        AccountItem(
            address = AddrStd(account.address).toWalletAddress(testnet),
            name = account.name,
            walletVersion = account.walletVersion,
            balanceFormat = CurrencyFormatter.format("TON", balance),
            tokens = hasTokens,
            collectibles = hasNftItems,
            selected = account.walletVersion == WalletVersion.V5R1 || (account.balance > 0 || hasTokens || hasNftItems),
            position = position,
        )
    }

    private fun setWatchAccount(account: AccountDetailsEntity?) {
        val oldAccount = getWatchAccount()
        if (oldAccount?.equals(account) == true) {
            return
        }

        savedState.watchAccount = account
        setLabelName(account?.name ?: "Wallet")
    }

    fun getWatchAccount(): AccountDetailsEntity? {
        return savedState.watchAccount
    }

    private fun getAccounts(): List<AccountItem> {
        return savedState.accounts ?: emptyList()
    }

    fun setAccounts(accounts: List<AccountItem>) {
        savedState.accounts = accounts
        _accountsFlow.tryEmit(accounts)
    }

    private fun getSelectedAccounts(): List<AccountItem> {
        return getAccounts().filter { it.selected }
    }

    fun setLabel(name: String, emoji: String, color: Int) {
        setLabel(Wallet.Label(name, emoji, color))
    }

    fun setLabel(label: Wallet.Label) {
        savedState.label = label
    }

    fun getLabel(): Wallet.Label {
        return savedState.label ?: Wallet.Label(
            accountName = "",
            emoji = Emoji.WALLET_ICON,
            color = WalletColor.all.first()
        )
    }

    fun setLabelName(name: String) {
        val oldLabel = getLabel()
        val emoji = Emoji.getEmojiFromPrefix(name) ?: oldLabel.emoji

        setLabel(oldLabel.copy(
            accountName = name.replace(emoji.toString(), "").trim(),
            emoji = emoji
        ))
    }

    fun setPush(context: Context) {
        nextStep(context, InitRoute.Push)
    }

    fun nextStep(context: Context, from: InitRoute) {
        if (from == InitRoute.WatchAccount) {
            routeTo(InitRoute.LabelAccount)
        } else if (from == InitRoute.SelectAccount) {
            applyNameFromSelectedAccounts()
            routeTo(InitRoute.LabelAccount)
        } else if (from == InitRoute.Push) {
            execute(context)
        } else if (!hasPushPermission) {
            routeTo(InitRoute.Push)
        } else if (passcodeAfterSeed && from == InitRoute.ImportWords) {
            routeTo(InitRoute.CreatePasscode)
        } else {
            execute(context)
        }
    }

    private fun applyNameFromSelectedAccounts() {
        val selected = getSelectedAccounts().filter { !it.name.isNullOrBlank() }
        if (selected.isEmpty()) {
            return
        }
        val name = selected.first().name ?: return
        setLabelName(name)
    }

    private fun execute(context: Context) {
        _eventFlow.tryEmit(InitEvent.Loading(true))

        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (!passcodeManager.hasPinCode()) {
                    passcodeManager.save(savedState.passcode!!)
                }

                val wallets = mutableListOf<WalletEntity>()
                when (type) {
                    InitArgs.Type.Watch -> wallets.add(saveWatchWallet())
                    InitArgs.Type.New -> wallets.add(newWallet(context))
                    InitArgs.Type.Import, InitArgs.Type.Testnet -> wallets.addAll(importWallet(context))
                    InitArgs.Type.Signer -> wallets.addAll(signerWallets(false))
                    InitArgs.Type.SignerQR -> wallets.addAll(signerWallets(true))
                    InitArgs.Type.Ledger -> wallets.addAll(ledgerWallets())
                }

                if (type == InitArgs.Type.Import || type == InitArgs.Type.Testnet) {
                    for (wallet in wallets) {
                        backupRepository.addBackup(wallet.id, BackupEntity.Source.LOCAL)
                    }
                }

                for (wallet in wallets) {
                    settingsRepository.setPushWallet(wallet.id, true)
                }

                accountRepository.setSelectedWallet(wallets.first().id)

                _eventFlow.tryEmit(InitEvent.Finish)
            } catch (e: Throwable) {
                _eventFlow.tryEmit(InitEvent.Loading(false))
                Navigation.from(context)?.toast(e.message ?: "Error")
            }
        }
    }

    private suspend fun saveWatchWallet(): WalletEntity {
        val account = getWatchAccount() ?: throw IllegalStateException("Account is not set")
        val label = getLabel()
        val publicKey = getPublicKey(account.address)

        return accountRepository.addWatchWallet(label, publicKey, account.walletVersion)
    }

    private suspend fun newWallet(context: Context): WalletEntity {
        val mnemonic = Mnemonic.generate()
        val label = getLabel()
        val wallet = accountRepository.addNewWallet(label, mnemonic)
        saveMnemonic(context, listOf(wallet.id), mnemonic)
        AnalyticsHelper.trackEvent("generate_wallet")
        AnalyticsHelper.trackEvent("create_wallet")
        return wallet
    }

    private suspend fun importWallet(context: Context): List<WalletEntity> = withContext(Dispatchers.IO) {
        val versions = getSelectedAccounts().map { it.walletVersion }
        val mnemonic = savedState.mnemonic ?: throw IllegalStateException("Mnemonic is not set")
        if (!TonMnemonic.isValid(mnemonic)) {
            throw IllegalStateException("Invalid mnemonic")
        }
        val label = getLabel()
        val wallets = accountRepository.importWallet(label, mnemonic, versions, testnet)
        saveMnemonic(context, wallets.map { it.id }, mnemonic)
        AnalyticsHelper.trackEvent("import_wallet")
        wallets
    }

    private suspend fun ledgerWallets(): List<WalletEntity> {
        val ledgerConnectData =
            savedState.ledgerConnectData ?: throw IllegalStateException("Ledger connect data is not set")

        val accounts = getSelectedAccounts().map { selectedAccount ->
            ledgerConnectData.accounts.find { account -> account.path.index == selectedAccount.ledgerIndex }
                ?: throw IllegalStateException("Ledger account is not found")
        }
        val label = getLabel()

        return accountRepository.pairLedger(
            label = label,
            ledgerAccounts = accounts,
            deviceId = ledgerConnectData.deviceId,
        )
    }

    private suspend fun signerWallets(qr: Boolean): List<WalletEntity> {
        val versions = getSelectedAccounts().map { it.walletVersion }
        val label = getLabel()
        val publicKey = savedState.publicKey ?: throw IllegalStateException("Public key is not set")

        return accountRepository.pairSigner(label, publicKey, versions, qr)
    }

    private suspend fun saveMnemonic(
        context: Context,
        walletIds: List<String>,
        list: List<String>
    ) = withContext(Dispatchers.IO) {
        var passcode = savedState.passcode
        if (passcode == null) {
            passcode = withContext(Dispatchers.Main) {
                PasscodeDialog.request(context)
            }
        }
        if (passcode.isNullOrBlank()) {
            throw IllegalStateException("wrong passcode")
        }
        rnLegacy.addMnemonics(passcode, walletIds, list)
    }

    private fun getPublicKey(
        accountId: String,
    ): PublicKeyEd25519 {
        api.getPublicKey(accountId, testnet).let { hex ->
            return PublicKeyEd25519(hex(hex))
        }
    }

}