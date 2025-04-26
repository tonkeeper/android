package com.tonapps.tonkeeper.ui.screen.init

import android.app.Application
import android.content.Context
import android.graphics.Color
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.tonapps.blockchain.MnemonicHelper
import com.tonapps.blockchain.ton.AndroidSecureRandom
import com.tonapps.blockchain.ton.EntropyHelper
import com.tonapps.blockchain.ton.TonMnemonic
import com.tonapps.blockchain.ton.TonNetwork
import com.tonapps.blockchain.ton.contract.BaseWalletContract
import com.tonapps.blockchain.ton.contract.WalletV5R1Contract
import com.tonapps.blockchain.ton.contract.WalletVersion
import com.tonapps.blockchain.ton.extensions.toAccountId
import com.tonapps.blockchain.ton.extensions.toRawAddress
import com.tonapps.blockchain.ton.extensions.toWalletAddress
import com.tonapps.emoji.Emoji
import com.tonapps.extensions.MutableEffectFlow
import com.tonapps.extensions.logError
import com.tonapps.icu.Coins
import com.tonapps.icu.CurrencyFormatter
import com.tonapps.tonkeeper.RemoteConfig
import com.tonapps.tonkeeper.core.AnalyticsHelper
import com.tonapps.tonkeeper.extensions.fixW5Title
import com.tonapps.tonkeeper.extensions.toast
import com.tonapps.tonkeeper.manager.push.PushManager
import com.tonapps.tonkeeper.ui.base.BaseWalletVM
import com.tonapps.tonkeeper.ui.screen.init.list.AccountItem
import com.tonapps.tonkeeper.worker.PushToggleWorker
import com.tonapps.tonkeeper.worker.TotalBalancesWorker
import com.tonapps.uikit.list.ListCell
import com.tonapps.wallet.api.API
import com.tonapps.wallet.api.entity.AccountDetailsEntity
import com.tonapps.wallet.api.entity.TokenEntity
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.account.Wallet
import com.tonapps.wallet.data.account.WalletColor
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.backup.BackupRepository
import com.tonapps.wallet.data.backup.entities.BackupEntity
import com.tonapps.wallet.data.passcode.PasscodeManager
import com.tonapps.wallet.data.passcode.dialog.PasscodeDialog
import com.tonapps.wallet.data.rn.RNLegacy
import com.tonapps.wallet.data.settings.SafeModeState
import com.tonapps.wallet.data.settings.SettingsRepository
import com.tonapps.wallet.localization.Localization
import io.tonapi.models.AccountStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ton.api.pk.PrivateKeyEd25519
import org.ton.block.AddrStd
import org.ton.mnemonic.Mnemonic
import uikit.navigation.Navigation
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.properties.Delegates

@OptIn(FlowPreview::class)
class InitViewModel(
    app: Application,
    args: InitArgs,
    private val passcodeManager: PasscodeManager,
    private val accountRepository: AccountRepository,
    private val api: API,
    private val backupRepository: BackupRepository,
    private val rnLegacy: RNLegacy,
    private val settingsRepository: SettingsRepository,
    private val remoteConfig: RemoteConfig,
    savedStateHandle: SavedStateHandle
): BaseWalletVM(app) {

    private val entropyHelper: EntropyHelper by lazy {
        EntropyHelper(context)
    }

    val installId: String
        get() = settingsRepository.installId

    private val savedState = InitModelState(savedStateHandle)
    private val type = args.type
    private val testnet: Boolean = type == InitArgs.Type.Testnet
    private val walletsCount = AtomicInteger(-1)

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
            if (account == null || account.walletVersion == WalletVersion.UNKNOWN) {
                setWatchAccount(null)
                return@map null
            }
            setWatchAccount(account)
            account
        }.flowOn(Dispatchers.IO)

    private val _accountsFlow = MutableEffectFlow<List<AccountItem>?>()
    val accountsFlow = _accountsFlow.asSharedFlow().filterNotNull()

    val labelFlow = savedState.labelFlow.stateIn(viewModelScope, SharingStarted.Lazily, null).filterNotNull()

    private val isPinSet = AtomicBoolean(false)

    private val requestSetPinCode: Boolean
        get() = (type == InitArgs.Type.New || type == InitArgs.Type.Import || type == InitArgs.Type.Testnet) && !isPinSet.get()

    var wordsCount: Int
        get() = savedState.wordsCount
        set(value) {
            savedState.wordsCount = value
        }

    init {
        savedState.publicKey = args.publicKey?.let {
            InitModelState.PublicKey(publicKey = it)
        }

        savedState.ledgerConnectData = args.ledgerConnectData
        savedState.keystone = args.keystone

        viewModelScope.launch(Dispatchers.IO) {
            setLoading(true)
            isPinSet.set(passcodeManager.hasPinCode())

            val labelName = args.labelName ?: getDefaultWalletName()
            setLabelName(labelName)

            if (type == InitArgs.Type.New) {
                withContext(Dispatchers.Main) {
                    entropyHelper.start()
                }
            }

            start()
        }
    }

    private suspend fun start() {
        when (type) {
            InitArgs.Type.Watch -> routeTo(InitRoute.WatchAccount)
            InitArgs.Type.Import, InitArgs.Type.Testnet -> routeTo(InitRoute.ImportWords)
            InitArgs.Type.Signer, InitArgs.Type.SignerQR -> resolveWallets(savedState.publicKey!!)
            InitArgs.Type.Ledger -> routeTo(InitRoute.SelectAccount)
            InitArgs.Type.Keystone -> routeTo(InitRoute.LabelAccount)
            InitArgs.Type.New -> { routeTo(if (requestSetPinCode) InitRoute.CreatePasscode else InitRoute.Push) }
        }
    }

    private fun routeTo(route: InitRoute) {
        _routeFlow.tryEmit(route)
        setLoading(false)
    }

    fun enablePush(enable: Boolean) {
        savedState.enablePush = enable
        routeTo(InitRoute.LabelAccount)
    }

    fun toggleAccountSelection(address: String, selected: Boolean): Boolean {
        val items = getAccounts().toMutableList()
        val index = items.indexOfFirst { it.address.toRawAddress() == address }
        if (index == -1) {
            return false
        }
        val oldItem = items[index]
        if (oldItem.selected == selected) {
            return true
        }
        val newItem = oldItem.copy(selected = selected)
        items[index] = newItem
        setAccounts(items.toList())
        return true
    }

    private fun setLoading(loading: Boolean) {
        _eventFlow.tryEmit(InitEvent.Loading(loading))
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
        } else {
            routeTo(InitRoute.Push)
        }
    }

    suspend fun setMnemonic(words: List<String>): Boolean {
        if (resolveWallets(words)) {
            savedState.mnemonic = words
            return true
        }
        return false
    }

    private suspend fun resolveWallets(mnemonic: List<String>): Boolean = withContext(Dispatchers.IO) {
        try {
            val privateKey = MnemonicHelper.privateKey(mnemonic)
            val publicKey = privateKey.publicKey()
            resolveWallets(InitModelState.PublicKey(publicKey = publicKey))
            true
        } catch (e: Throwable) {
            false
        }
    }

    private suspend fun resolveWallets(publicKey: InitModelState.PublicKey) = withContext(Dispatchers.IO) {
        val accounts = if (publicKey.new) {
            mutableListOf()
        } else {
            api.resolvePublicKey(publicKey.publicKey, testnet).filter {
                it.walletVersion != WalletVersion.UNKNOWN
            }.sortedByDescending { it.walletVersion.index }.toMutableList()
        }

        if (accounts.count { it.walletVersion == WalletVersion.V5R1 } == 0) {
            val contract = WalletV5R1Contract(publicKey.publicKey, tonNetwork)
            val query = contract.address.toAccountId()
            if (publicKey.new) {
                accounts.add(0, AccountDetailsEntity(contract, testnet, new = true, initialized = false))
            } else {
                val apiAccount = api.resolveAccount(query, testnet)
                val account = if (apiAccount == null) {
                    AccountDetailsEntity(contract, testnet = testnet, new = true, initialized = false)
                } else {
                    AccountDetailsEntity(query, apiAccount.copy(
                        interfaces = listOf("wallet_v5r1")
                    ), testnet, false)
                }
                accounts.add(0, account)
            }
        }

        val list = accounts.mapIndexed { index, account ->
            getAccountItem(account, ListCell.getPosition(accounts.size, index))
        }

        val items = mutableListOf<AccountItem>()
        for (account in list) {
            items.add(account)
        }
        setAccounts(items.toList())

        if (items.size > 1) {
            routeTo(InitRoute.SelectAccount)
        } else if (requestSetPinCode) {
            val accountName = items.first().name
            if (!accountName.isNullOrBlank()) {
                setLabelName(accountName)
            }
            routeTo(InitRoute.CreatePasscode)
        } else {
            routeTo(InitRoute.Push)
        }
    }

    private fun applyAccountName() {
        viewModelScope.launch {
            val selectedAccounts = getSelectedAccounts()
            val accountName = (if (selectedAccounts.size == 1) {
                selectedAccounts.first()
            } else {
                selectedAccounts.firstOrNull { !it.name.isNullOrBlank() }
            })?.name

            if (!accountName.isNullOrBlank()) {
                setLabelName(accountName)
            }
        }
    }

    private suspend fun getAccountItem(
        account: AccountDetailsEntity,
        position: ListCell.Position,
    ): AccountItem = withContext(Dispatchers.IO) {
        if (account.new) {
            AccountItem(
                address = AddrStd(account.address).toWalletAddress(testnet),
                name = account.name,
                walletVersion = account.walletVersion,
                balanceFormat = CurrencyFormatter.format("TON", Coins.ZERO),
                tokens = false,
                collectibles = false,
                selected = true,
                position = position,
                initialized = false
            )
        } else {
            val tokensDeferred = async { api.getJettonsBalances(account.address, testnet) }
            val nftItemsDeferred = async { api.getNftItems(account.address, testnet, 1) }
            val tokens = tokensDeferred.await() ?: emptyList()
            val nftItems = nftItemsDeferred.await() ?: emptyList()
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
                initialized = account.initialized
            )
        }
    }

    private suspend fun setWatchAccount(account: AccountDetailsEntity?) {
        val oldAccount = getWatchAccount()
        if (oldAccount?.equals(account) == true) {
            return
        }

        savedState.watchAccount = account

        setLabelName(account?.name ?: getDefaultWalletName())
    }

    private suspend fun getDefaultWalletName(): String {
        val count = getWalletsCount()
        return if (count == 0 && type == InitArgs.Type.New) {
            getString(Localization.app_name)
        } else  {
            getString(Localization.wallet)
        }
    }

    private suspend fun getWalletsCount(): Int {
        val count = walletsCount.get()
        return if (0 > count) {
            accountRepository.getWallets().size.also {
                walletsCount.set(it)
            }
        } else {
            count
        }
    }

    fun getWatchAccount(): AccountDetailsEntity? {
        return savedState.watchAccount
    }

    private fun getAccounts(): List<AccountItem> {
        return (savedState.accounts ?: emptyList())
    }

    fun setAccounts(accounts: List<AccountItem>) {
        savedState.accounts = accounts.map { it.copy() }
        _accountsFlow.tryEmit(accounts.map { it.copy() })
    }

    private fun getSelectedAccounts(): List<AccountItem> {
        return getAccounts().toList().filter { it.selected }
    }

    fun setLabel(name: String, emoji: String, color: Int) {
        setLabel(Wallet.Label(name, emoji, color))
    }

    private fun setLabel(label: Wallet.Label) {
        savedState.label = label
    }

    private suspend fun getLabel(): Wallet.Label = withContext(Dispatchers.IO) {
        var label = savedState.label ?: Wallet.Label(
            accountName = getDefaultWalletName(),
            emoji = Emoji.WALLET_ICON,
            color = WalletColor.all.first()
        )
        if (label.color == Color.TRANSPARENT) {
            label = label.copy(color = WalletColor.all.first())
        }
        if (label.emoji.isBlank()) {
            label = label.copy(emoji = Emoji.WALLET_ICON)
        }
        if (label.name.isBlank()) {
            label = label.copy(accountName = getDefaultWalletName())
        }
        label
    }

    private suspend fun setLabelName(name: String) {
        val oldLabel = getLabel()
        val emoji = Emoji.getEmojiFromPrefix(name) ?: oldLabel.emoji

        setLabel(oldLabel.copy(
            accountName = name.replace(emoji.toString(), "").trim(),
            emoji = emoji
        ))
    }

    fun nextStep(context: Context, from: InitRoute) {
        if (from == InitRoute.CreatePasscode) {
            routeTo(InitRoute.ReEnterPasscode)
        } else if (from == InitRoute.LabelAccount) {
            execute(context)
        } else if (from == InitRoute.WatchAccount) {
            routeTo(InitRoute.Push)
        } else if (from == InitRoute.SelectAccount && !requestSetPinCode) {
            applyAccountName()
            routeTo(InitRoute.Push)
        } else if (requestSetPinCode) {
            applyAccountName()
            routeTo(InitRoute.CreatePasscode)
        } else {
            execute(context)
        }
    }

    private fun execute(context: Context) {
        setLoading(true)

        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (requestSetPinCode) {
                    passcodeManager.save(savedState.passcode!!)
                }

                val alreadyWalletCount = accountRepository.getWallets()
                if (alreadyWalletCount.isEmpty() && api.config.flags.safeModeEnabled) {
                    settingsRepository.setSafeModeState(SafeModeState.Enabled)
                    if (type == InitArgs.Type.Import || type == InitArgs.Type.Testnet) {
                        settingsRepository.showSafeModeSetup = true
                    }
                }

                val wallets = mutableListOf<WalletEntity>()
                when (type) {
                    InitArgs.Type.Watch -> wallets.add(saveWatchWallet())
                    InitArgs.Type.New -> wallets.add(newWallet(context))
                    InitArgs.Type.Import, InitArgs.Type.Testnet -> wallets.addAll(importWallet(context))
                    InitArgs.Type.Signer -> wallets.addAll(signerWallets(false))
                    InitArgs.Type.SignerQR -> wallets.addAll(signerWallets(true))
                    InitArgs.Type.Ledger -> wallets.addAll(ledgerWallets())
                    InitArgs.Type.Keystone -> wallets.addAll(keystoneWallet())
                }

                if (type == InitArgs.Type.Import || type == InitArgs.Type.Testnet) {
                    backupRepository.addBackups(wallets.map { it.id })
                }

                if (savedState.enablePush) {
                    withContext(Dispatchers.Main) {
                        PushToggleWorker.run(context, wallets, PushManager.State.Enable)
                    }
                }

                withContext(Dispatchers.Main) {
                    TotalBalancesWorker.run(context)
                }

                val selectedWalletId = wallets.minByOrNull { it.version }!!.id
                accountRepository.setSelectedWallet(selectedWalletId)
                finish()
            } catch (e: Throwable) {
                context.logError(e)
                setLoading(false)
            }
        }
    }

    private suspend fun buildNewLabel(accounts: List<SimpleAccount>): Wallet.NewLabel {
        val versions = accounts.map { it.version }
        val walletsCount = getWalletsCount()
        val label = getLabel()
        val isMultipleVersions = versions.distinct().size > 1
        val isUsingDefaultName = label.name == getDefaultWalletName()

        val names = mutableListOf<String>()
        for ((index, account) in accounts.withIndex()) {
            val builder = StringBuilder(label.name)
            if (isMultipleVersions) {
                builder.append(" ")
                builder.append(account.version.title.fixW5Title())
            }
            if (isUsingDefaultName && !isMultipleVersions && (accounts.size > 1 || walletsCount > 1)) {
                builder.append(" ")
                builder.append((walletsCount + index) + 1)
            }
            names.add(builder.toString())
        }

        return Wallet.NewLabel(
            names = names,
            emoji = label.emoji,
            color = label.color
        )
    }

    private suspend fun buildNewLabel(account: SimpleAccount): Wallet.NewLabel {
        return buildNewLabel(listOf(account))
    }

    private suspend fun saveWatchWallet(): WalletEntity {
        val account = getWatchAccount() ?: throw IllegalStateException("Account is not set")
        val publicKey = api.safeGetPublicKey(account.address, testnet)
        val label = buildNewLabel(SimpleAccount(account))

        return accountRepository.addWatchWallet(label, publicKey, account.walletVersion)
    }

    private suspend fun newWallet(context: Context): WalletEntity {
        AndroidSecureRandom.seed(entropyHelper.getSeed(512))

        val mnemonic = Mnemonic.generate(random = AndroidSecureRandom)
        val walletId = AccountRepository.newWalletId()
        saveMnemonic(context, listOf(walletId), mnemonic)
        val label = buildNewLabel(SimpleAccount(version = WalletVersion.V5R1))

        val wallet = accountRepository.addNewWallet(walletId, label, mnemonic)

        AnalyticsHelper.simpleTrackEvent("wallet_generate", installId, hashMapOf("wallet_type" to wallet.version.title))
        return wallet
    }

    private suspend fun importWallet(context: Context): List<WalletEntity> = withContext(Dispatchers.IO) {
        val accounts = getSelectedAccounts()
        if (accounts.isEmpty()) {
            throw IllegalStateException("Wallet versions are not set")
        }

        val mnemonic = savedState.mnemonic ?: throw IllegalStateException("Mnemonic is not set")
        if (!TonMnemonic.isValid(mnemonic)) {
            throw IllegalStateException("Invalid mnemonic")
        }

        val ids = accounts.map { AccountRepository.newWalletId() }
        saveMnemonic(context, ids, mnemonic)

        val label = buildNewLabel(accounts.map {
            SimpleAccount(
                name = it.name,
                version = it.walletVersion
            )
        })

        accounts.map {
            AnalyticsHelper.simpleTrackEvent("wallet_import", installId, hashMapOf("wallet_type" to it.walletVersion.title))
        }

        val wallets = accountRepository.importWallet(ids, label, mnemonic, accounts.map { it.walletVersion }, testnet, accounts.map { it.initialized })

        if (!testnet && !remoteConfig.isTronDisabled) {
            checkTronBalance(wallets)
        }

        wallets
    }

    private suspend fun checkTronBalance(wallets: List<WalletEntity>) {
        val wallet = wallets.first()
        val tronAddress = accountRepository.getTronAddress(wallet.id) ?: return
        val balance = api.tron.getTronUsdtBalance(tronAddress)

        if (balance.value.isPositive) {
            wallets.forEach {
                settingsRepository.setTokenHidden(it.id, TokenEntity.TRON_USDT.address, false)
                settingsRepository.setTokenPinned(it.id, TokenEntity.TRON_USDT.address, true)
                settingsRepository.setTokensSort(wallet.id, listOf(TokenEntity.USDT.address, TokenEntity.TRON_USDT.address))
            }
        }
    }

    private suspend fun ledgerWallets(): List<WalletEntity> {
        val ledgerConnectData = savedState.ledgerConnectData ?: throw IllegalStateException("Ledger connect data is not set")

        val accounts = getSelectedAccounts()

        val ledgerAccounts = accounts.map { selectedAccount ->
            ledgerConnectData.accounts.find {
                account -> account.path.index == selectedAccount.ledgerIndex
            } ?: throw IllegalStateException("Ledger account is not found")
        }

        val label = buildNewLabel(accounts.map {
            SimpleAccount(
                name = it.name,
                version = WalletVersion.V4R2
            )
        })

        return accountRepository.pairLedger(
            label = label,
            ledgerAccounts = ledgerAccounts,
            deviceId = ledgerConnectData.deviceId,
            initialized = accounts.map { it.initialized }
        )
    }

    private suspend fun signerWallets(qr: Boolean): List<WalletEntity> {
        val accounts = getSelectedAccounts()
        val publicKey = savedState.publicKey ?: throw IllegalStateException("Public key is not set")
        val label = buildNewLabel(accounts.map {
            SimpleAccount(
                name = it.name,
                version = it.walletVersion
            )
        })

        return accountRepository.pairSigner(label, publicKey.publicKey, accounts.map { it.walletVersion }, qr, accounts.map { it.initialized })
    }

    private suspend fun keystoneWallet(): List<WalletEntity> {
        val publicKey = savedState.publicKey ?: throw IllegalStateException("Public key is not set")
        val keystone = savedState.keystone ?: throw IllegalStateException("Keystone is not set")

        val label = buildNewLabel(SimpleAccount(
            version = WalletVersion.V4R2
        ))

        val contact = BaseWalletContract.create(publicKey.publicKey, WalletVersion.V4R2.title, tonNetwork.value)
        val account = api.resolveAccount(contact.address.toWalletAddress(testnet = testnet), testnet)
        val initialized = account != null && (account.status == AccountStatus.active || account.status == AccountStatus.frozen)

        return accountRepository.pairKeystone(label, publicKey.publicKey, keystone, initialized)
    }

    private suspend fun saveMnemonic(
        context: Context,
        walletIds: List<String>,
        mnemonic: List<String>
    ) = withContext(Dispatchers.IO) {
        if (requestSetPinCode) {
            return@withContext
        }
        if (passcodeManager.hasPinCode()) {
            val isValid = passcodeManager.confirmation(context, context.getString(Localization.app_name))
            if (!isValid) {
                throw IllegalStateException("wrong passcode")
            }
        } else {
            var passcode = savedState.passcode
            if (passcode == null) {
                passcode = withContext(Dispatchers.Main) {
                    passcodeManager.legacyGetPasscode(context)
                }
            }
            if (passcode.isNullOrBlank()) {
                throw IllegalStateException("wrong passcode")
            }
            rnLegacy.addMnemonics(passcode, walletIds, mnemonic)
        }
    }

    override fun onCleared() {
        super.onCleared()
        entropyHelper.stop()
    }

    private data class SimpleAccount(
        val name: String? = null,
        val version: WalletVersion
    ) {

        constructor(account: AccountDetailsEntity): this(
            name = account.name,
            version = account.walletVersion
        )
    }
}