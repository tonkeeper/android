package com.tonapps.tonkeeper.ui.screen.init

import android.app.Application
import android.content.Context
import android.graphics.Color
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.tonapps.async.Async
import com.tonapps.blockchain.MnemonicHelper
import com.tonapps.blockchain.model.legacy.TokenEntity
import com.tonapps.blockchain.model.legacy.Wallet
import com.tonapps.blockchain.model.legacy.WalletColor
import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.blockchain.model.legacy.WalletType
import com.tonapps.blockchain.ton.AndroidSecureRandom
import com.tonapps.blockchain.ton.EntropyHelper
import com.tonapps.blockchain.ton.MnemonicType
import com.tonapps.blockchain.ton.TonNetwork
import com.tonapps.blockchain.ton.contract.BaseWalletContract
import com.tonapps.blockchain.ton.contract.WalletV5R1Contract
import com.tonapps.blockchain.ton.contract.WalletVersion
import com.tonapps.blockchain.ton.extensions.EmptyPrivateKeyEd25519
import com.tonapps.blockchain.ton.extensions.toAccountId
import com.tonapps.blockchain.ton.extensions.toRawAddress
import com.tonapps.blockchain.ton.extensions.toWalletAddress
import com.tonapps.bus.core.AnalyticsHelper
import com.tonapps.bus.generated.Events.WalletFlow.WalletFlowFrom
import com.tonapps.bus.generated.Events.WalletFlow.WalletFlowSource
import com.tonapps.bus.generated.Events.WalletFlow.WalletFlowWalletMode
import com.tonapps.bus.generated.Events.WalletFlow.WalletFlowWalletSource
import com.tonapps.chainkit.core.chain.model.account.Address
import com.tonapps.chainkit.core.chain.model.account.CryptoWallet
import com.tonapps.chainkit.core.secure.mnemonic.Mnemonic
import com.tonapps.core.flags.WalletFeature
import com.tonapps.emoji.Emoji
import com.tonapps.extensions.MutableEffectFlow
import com.tonapps.extensions.logError
import com.tonapps.icu.Coins
import com.tonapps.icu.CurrencyFormatter
import com.tonapps.bus.generated.Events.Migration.MigrationFrom
import com.tonapps.log.L
import com.tonapps.migration.MigrationFragment
import com.tonapps.migration.data.MigrationRepository
import com.tonapps.tonkeeper.Environment
import com.tonapps.tonkeeper.extensions.fixW5Title
import com.tonapps.tonkeeper.manager.push.PushManager
import com.tonapps.onboading.screens.loader.InitLoaderState
import com.tonapps.onboading.screens.selector.WalletKind
import com.tonapps.onboading.screens.selector.WalletKindItem
import com.tonapps.onboading.screens.selector.WalletVersionItem
import com.tonapps.tonkeeper.ui.base.BaseWalletVM
import com.tonapps.tonkeeper.ui.screen.init.list.AccountItem
import com.tonapps.tonkeeper.ui.screen.watchonly.WatchInfoScreen
import com.tonapps.tonkeeper.worker.PushToggleWorker
import com.tonapps.tonkeeper.worker.TotalBalancesWorker
import com.tonapps.uikit.list.ListCell
import com.tonapps.wallet.api.API
import com.tonapps.wallet.api.entity.AccountDetailsEntity
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.backup.BackupRepository
import com.tonapps.wallet.data.multichain.account.AccountWithDetails
import com.tonapps.wallet.data.multichain.account.McAccountRepository
import com.tonapps.wallet.data.multichain.account.UnifiedAccountRepository
import com.tonapps.wallet.data.multichain.account.WalletRegistrationException
import com.tonapps.wallet.data.multichain.wallet.McWalletEntity
import com.tonapps.wallet.data.passcode.PasscodeBiometric
import com.tonapps.wallet.data.passcode.PasscodeManager
import com.tonapps.wallet.data.raffle.RaffleRepository
import com.tonapps.wallet.data.rn.RNLegacy
import com.tonapps.wallet.data.settings.SettingsRepository
import com.tonapps.wallet.localization.Localization
import io.tonapi.models.AccountStatus
import io.tonapi.models.TokenRates
import java.math.BigDecimal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
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
import kotlinx.coroutines.withTimeoutOrNull
import org.ton.block.AddrStd
import org.ton.kotlin.crypto.PublicKeyEd25519
import org.ton.kotlin.crypto.mnemonic.Mnemonic as TonMnemonicGenerator
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

private const val MIGRATABLE_WALLETS_CHECK_TIMEOUT_MS = 15_000L

@Suppress("LargeClass")
@OptIn(FlowPreview::class)
class InitViewModel(
    app: Application,
    args: InitArgs,

    private val passcodeManager: PasscodeManager,
    private val accountRepository: AccountRepository,
    private val settingsRepository: SettingsRepository,
    private val backupRepository: BackupRepository,
    private val api: API,
    private val rnLegacy: RNLegacy,
    private val migrationRepository: MigrationRepository,
    private val tonWalletVersionInteractor: TonWalletVersionInteractor,

    private val mcAccountRepository: McAccountRepository,
    private val unifiedAccountRepository: UnifiedAccountRepository,
    private val raffleRepository: RaffleRepository,

    private val environment: Environment,
    private val analytics: AnalyticsHelper,
    savedStateHandle: SavedStateHandle
) : BaseWalletVM(app) {

    private val entropyHelper: EntropyHelper by lazy {
        EntropyHelper(context)
    }

    val installId: String
        get() = settingsRepository.installId

    private val savedState = InitModelState(savedStateHandle)

    private var type = args.type
    private val testnet: Boolean
        get() = type == InitArgs.Type.Testnet
    private val tetra: Boolean
        get() = type == InitArgs.Type.Tetra
    private val walletsCount = AtomicInteger(-1)
    private var migrationAvailable = false
    private var existingWalletLabels: List<String>? = null
    val watchRecoveryAccountId = args.watchRecoveryAccountId
    private val raffleSourceWalletId = args.raffleSourceWalletId

    private val walletLabelHelper: WalletLabelHelper by lazy {
        WalletLabelHelper(context)
    }

    private val tonNetwork: TonNetwork
        get() = when {
            testnet -> TonNetwork.TESTNET
            tetra -> TonNetwork.TETRA
            else -> TonNetwork.MAINNET
        }

    private val isMultichainEnabled: Boolean
        get() = WalletFeature.Multichain.isEnabled
                && (api.getConfig(TonNetwork.MAINNET).flags.multichainEnabled || WalletFeature.Multichain.isOverridden)

    private val isImportMultichainEnabled: Boolean
        get() = WalletFeature.ImportMultichainWallet.isEnabled
                && (api.getConfig(TonNetwork.MAINNET).flags.multichainEnabled || WalletFeature.ImportMultichainWallet.isOverridden)

    private val _uiTopOffset = MutableStateFlow(0)
    val uiTopOffset = _uiTopOffset.asStateFlow()

    private val _eventFlow = MutableEffectFlow<InitEvent>()
    val eventFlow = _eventFlow.asSharedFlow().filterNotNull()

    // One-shot signal for a failed passcode entry. Uses replay = 0 so a freshly created
    // PasscodeScreen (e.g. after popping back and re-opening EnterPasscode) does not receive a
    // stale replayed value and incorrectly show an error before the user types anything.
    private val _wrongPasscodeFlow = MutableSharedFlow<Unit>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val wrongPasscodeFlow = _wrongPasscodeFlow.asSharedFlow()

    private val _routeFlow = MutableEffectFlow<InitRoute>()
    val routeFlow = _routeFlow.asSharedFlow().filterNotNull()

    // Drives the multichain create/sync loader overlay. Null = hidden; otherwise the dialog shows the
    // loading animation (creation + sync) and finally the success animation.
    private val _loaderState = MutableStateFlow<InitLoaderState?>(null)
    val loaderState = _loaderState.asStateFlow()

    private val _watchAccountResolveFlow = MutableStateFlow<String>("")
    val watchAccountFlow = _watchAccountResolveFlow.asSharedFlow()
        .debounce(1000)
        .filter { it.isNotBlank() }
        .map {
            val account = api.resolveAddressOrName(it, tonNetwork)
            if (account == null || account.walletVersion == WalletVersion.UNKNOWN) {
                setWatchAccount(null, null)
                return@map null
            }
            setWatchAccount(it, account)
            account
        }
        .flowOn(Dispatchers.IO)

    private val _accountsFlow = MutableEffectFlow<List<AccountItem>?>()
    val accountsFlow = _accountsFlow.asSharedFlow().filterNotNull()

    private val _walletAccountsFlow = MutableStateFlow<List<WalletVersionItem>>(emptyList())
    val walletAccountsFlow = _walletAccountsFlow.asStateFlow()

    private val _selectedAddressFlow = MutableStateFlow<String?>(null)
    val selectedAddressFlow = _selectedAddressFlow.asStateFlow()

    private val _selectedWalletKindFlow = MutableStateFlow(WalletKind.Ton)
    val selectedWalletKindFlow = _selectedWalletKindFlow.asStateFlow()

    private val _walletKindsFlow = MutableStateFlow(defaultWalletKindItems())
    val walletKindsFlow = _walletKindsFlow.asStateFlow()

    val labelFlow = savedState.labelFlow

    private val isPinSet = AtomicBoolean(false)

    private var enterPasscodeJob: Job? = null

    private var reEnterPopJob: Job? = null

    private val requestSetPinCode: Boolean
        get() = (type == InitArgs.Type.New || type == InitArgs.Type.Import || type == InitArgs.Type.Testnet || type == InitArgs.Type.Tetra) && !isPinSet.get()

    private val requestEnterPinCode: Boolean
        get() = (type == InitArgs.Type.New || type == InitArgs.Type.Import || type == InitArgs.Type.Testnet || type == InitArgs.Type.Tetra) && isPinSet.get()

    private val isOnboarding: Boolean
        get() = walletsCount.get() == 0

    private val pushEnabledByDefault: Boolean
        get() = environment.isGooglePlayServicesAvailable && environment.areNotificationsEnabled

    private val needPushPermission: Boolean
        get() = environment.isGooglePlayServicesAvailable && !environment.areNotificationsEnabled

    private val analyticsFrom: WalletFlowFrom
        get() = if (isOnboarding) {
            WalletFlowFrom.Onboarding
        } else {
            WalletFlowFrom.Main
        }

    private val createWalletMode: WalletFlowWalletMode
        get() = if (isMultichainEnabled) {
            WalletFlowWalletMode.Multi
        } else {
            WalletFlowWalletMode.Single
        }

    private val importWalletMode: WalletFlowWalletMode
        get() = if (!isImportMultichainEnabled) {
            WalletFlowWalletMode.Single
        } else {
            WalletFlowWalletMode.Multi
        }

    private val importedWalletMode: WalletFlowWalletMode
        get() = when (type) {
            InitArgs.Type.Import, InitArgs.Type.Testnet, InitArgs.Type.Tetra -> when (effectiveMnemonicType()) {
                MnemonicType.Ton -> WalletFlowWalletMode.Single
                MnemonicType.Bip39, MnemonicType.Both -> WalletFlowWalletMode.Multi
                null -> importWalletMode
            }
            else -> WalletFlowWalletMode.Single
        }

    private val importWalletSource: WalletFlowWalletSource?
        get() = when (type) {
            InitArgs.Type.Import, InitArgs.Type.Testnet, InitArgs.Type.Tetra -> WalletFlowWalletSource.Mnemonic
            InitArgs.Type.Signer, InitArgs.Type.SignerQR -> WalletFlowWalletSource.Signer
            InitArgs.Type.Ledger -> WalletFlowWalletSource.Ledger
            InitArgs.Type.Keystone -> WalletFlowWalletSource.Keystone
            InitArgs.Type.Watch -> WalletFlowWalletSource.Watchonly
            InitArgs.Type.New, InitArgs.Type.AddWallet -> null
        }

    var wordsCount: Int
        get() = savedState.wordsCount
        set(value) {
            savedState.wordsCount = value
        }

    init {
        walletsCount.set(savedState.walletsCount)
        savedState.publicKey = args.publicKey?.let { InitModelState.PublicKey(publicKey = it) }

        savedState.ledgerConnectData = args.ledgerConnectData
        savedState.keystone = args.keystone

        viewModelScope.launch(Dispatchers.IO) {
            setLoading(true)
            isPinSet.set(passcodeManager.hasPinCode())

            args.labelName?.let(::setInitName)

            if (type == InitArgs.Type.New) {
                withContext(Dispatchers.Main) {
                    entropyHelper.start()
                }
            }

            start()
        }
    }

    private suspend fun start() {
        setLabel(
            name = getDefaultWalletName(),
            emoji = Emoji.WALLET_ICON,
            color = WalletColor.all.first()
        )

        trackFlowStarted()

        when (type) {
            InitArgs.Type.AddWallet -> routeTo(InitRoute.SelectType)
            InitArgs.Type.Watch -> routeTo(InitRoute.WatchAccount)
            InitArgs.Type.Import, InitArgs.Type.Testnet, InitArgs.Type.Tetra -> routeTo(InitRoute.ImportWords)
            InitArgs.Type.Signer, InitArgs.Type.SignerQR -> resolveWallets(savedState.publicKey!!)
            InitArgs.Type.Ledger -> routeTo(InitRoute.SelectAccount)
            InitArgs.Type.Keystone -> {
                setLabel(
                    name = getDefaultWalletName(),
                    emoji = Emoji.WALLET_ICON,
                    color = WalletColor.all.first()
                )
                savedState.enablePush = pushEnabledByDefault
                routeTo(InitRoute.LabelAccount)
            }

            InitArgs.Type.New -> {
                if (requestSetPinCode) {
                    routeTo(InitRoute.CreatePasscode)
                } else if (requestEnterPinCode) {
                    routeTo(InitRoute.EnterPasscode)
                } else {
                    routeTo(InitRoute.BackupStart)
                }
            }
        }
    }

    private fun routeTo(route: InitRoute) {
        trackRoute(route)
        _routeFlow.tryEmit(route)
        setLoading(false)
    }

    private suspend fun trackFlowStarted() {
        getWalletsCount()
        when (type) {
            InitArgs.Type.AddWallet -> analytics.events.walletFlow.addWalletMenuView(from = analyticsFrom)
            InitArgs.Type.New -> analytics.events.walletFlow.walletCreateStarted(
                walletMode = createWalletMode,
                from = analyticsFrom,
            )
            InitArgs.Type.Import, InitArgs.Type.Testnet, InitArgs.Type.Tetra,
            InitArgs.Type.Signer, InitArgs.Type.SignerQR,
            InitArgs.Type.Ledger, InitArgs.Type.Keystone,
            InitArgs.Type.Watch -> trackImportStarted()
        }
    }

    private fun trackImportStarted() {
        analytics.events.walletFlow.walletImportStarted(
            walletMode = importedWalletMode,
            walletSource = importWalletSource ?: return,
            from = analyticsFrom,
        )
    }

    private fun trackRoute(route: InitRoute) {
        when (route) {
            InitRoute.BackupStart -> analytics.events.walletFlow.walletBackupStarted(
                walletMode = createWalletMode,
                source = WalletFlowSource.Onboarding,
            )
            InitRoute.LabelAccount -> if (type == InitArgs.Type.New && isOnboarding) {
                analytics.events.onboardingFlow.onboardingViewCustomize()
            }
            else -> Unit
        }
    }

    private fun trackImportSuccess() {
        analytics.events.walletFlow.walletImportSuccess(
            walletMode = importedWalletMode,
            walletSource = importWalletSource ?: return,
            from = analyticsFrom,
        )
    }

    private fun trackImportError(e: Throwable) {
        analytics.events.walletFlow.walletImportError(
            walletMode = importedWalletMode,
            walletSource = importWalletSource ?: return,
            from = analyticsFrom,
            errorType = e.javaClass.simpleName,
            errorCode = null,
            errorMessage = e.message,
        )
    }

    fun trackImportInputError(errorType: String) {
        analytics.events.walletFlow.walletImportError(
            walletMode = importedWalletMode,
            walletSource = WalletFlowWalletSource.Mnemonic,
            from = analyticsFrom,
            errorType = errorType,
            errorCode = null,
            errorMessage = null,
        )
    }

    // Called from the type selector step: fixes the flow type the user picked and pushes the first
    // step of that flow within the same navigation stack.
    fun continueWith(newType: InitArgs.Type) {
        viewModelScope.launch(Dispatchers.IO) {
            setLoading(true)
            if (type != newType) {
                savedState.clearMnemonic()
                savedState.mnemonicType = null
                _walletAccountsFlow.value = emptyList()
                _selectedAddressFlow.value = null
            }
            type = newType

            if (newType == InitArgs.Type.New) {
                withContext(Dispatchers.Main) {
                    entropyHelper.start()
                }
            }

            start()
        }
    }

    // Whether this run creates a multichain wallet — those use the new lottie loader (create + sync)
    // instead of the legacy gear loader.
    private fun isMultichainCreation(): Boolean = when (type) {
        InitArgs.Type.New -> isMultichainEnabled
        InitArgs.Type.Import,
        InitArgs.Type.Testnet,
        InitArgs.Type.Tetra -> {
            val effectiveType = effectiveMnemonicType() ?: return false
            effectiveType != MnemonicType.Ton
        }
        else -> false
    }

    // The mnemonic type to act on: the user's explicit choice for an ambiguous (Both) seed, otherwise the
    // type detected from the stored words.
    private fun effectiveMnemonicType(): MnemonicType? {
        savedState.mnemonicType?.let { return it }
        val mnemonic = savedState.mnemonic ?: return null
        var detectedType: MnemonicType? = null
        mnemonic.useWordsAndClear { words -> detectedType = MnemonicHelper.mnemonicType(words.toList()) }
        return detectedType
    }

    // Biometry is an optional convenience; a failure here must not abort wallet creation.
    private suspend fun applyBiometry() {
        val passcode = savedState.passcode
        if (!savedState.enableBiometry || passcode == null) {
            return
        }
        try {
            rnLegacy.setupBiometry(passcode)
            settingsRepository.biometric = true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            L.e(e)
        }
    }

    private fun reportRaffleImport(importedWalletId: String) {
        val sourceWalletId = raffleSourceWalletId ?: return
        Async.globalScope().launch {
            raffleRepository.reportImport(sourceWalletId, importedWalletId)
        }
    }

    private suspend fun applyMultichainPush(walletId: String) {
        if (!savedState.enablePush) {
            return
        }
        settingsRepository.setPushWallet(walletId, true)
        PushToggleWorker.runByIds(context, listOf(walletId), PushManager.State.Enable)
    }

    // Keep the loading animation up while the freshly created wallet syncs, then switch to the success
    // animation; the dialog calls [onSyncLoaderFinished] once it has played through.
    private suspend fun awaitSyncThenDone(walletId: String) = coroutineScope {
        val migratableWallets = async { hasMigratableWallets() }
        mcAccountRepository.awaitWalletSync(walletId)
        migrationAvailable = migratableWallets.await()
        _loaderState.value = InitLoaderState.Done(walletId)
    }

    private suspend fun hasMigratableWallets(): Boolean {
        if (!WalletFeature.Migration.isEnabled) {
            return false
        }
        return try {
            withTimeoutOrNull(MIGRATABLE_WALLETS_CHECK_TIMEOUT_MS) {
                migrationRepository.migratableWalletsCount() > 0
            } ?: false
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            L.e(e)
            false
        }
    }

    fun onSyncLoaderFinished() {
        viewModelScope.launch {
            (loaderState.value as? InitLoaderState.Done)?.let { done ->
                accountRepository.setSelectedWallet(done.walletId)
            }
            if (migrationAvailable) {
                migrationAvailable = false
                openScreen(
                    MigrationFragment.newInstance(
                        from = MigrationFrom.Deeplink,
                        onboarding = true,
                    ),
                )
            }
            finish()
        }
    }

    fun getMnemonic(): List<String>? {
        return savedState.mnemonic?.toWordsUnsafe()?.toList()
    }

    // Word indices of the stored mnemonic, derived without materializing any words. Display code maps
    // each index back to a word via MnemonicHelper.wordAt only at bind time.
    fun getMnemonicIndexes(): IntArray? {
        val bytes = savedState.mnemonic?.toBytes() ?: return null
        val indexes = IntArray(bytes.size / 2) { i ->
            ((bytes[i * 2].toInt() and 0xFF) shl 8) or (bytes[i * 2 + 1].toInt() and 0xFF)
        }
        bytes.fill(0)
        return indexes
    }

    fun navigateToBackupPhrase() {
        viewModelScope.launch {
            generateNewWallet()
            routeTo(InitRoute.BackupPhrase)
        }
    }

    fun navigateToBackupCheck() {
        routeTo(InitRoute.BackupCheck)
    }

    fun completeBackup(done: Boolean = true) {
        if (done) {
            analytics.events.walletFlow.walletBackupSuccess(
                walletMode = createWalletMode,
                source = WalletFlowSource.Onboarding,
            )
        }
        savedState.backupDone = done
        routeToPushOrLabelAccount()
    }

    fun trackBackupError() {
        analytics.events.walletFlow.walletBackupError(
            walletMode = createWalletMode,
            source = WalletFlowSource.Onboarding,
            errorType = "backup_check_mismatch",
            errorCode = null,
            errorMessage = null,
        )
    }

    fun skipBackup() {
        analytics.events.walletFlow.walletBackupSkip(
            walletMode = createWalletMode,
            source = WalletFlowSource.Onboarding,
        )
        viewModelScope.launch {
            generateNewWallet()
            completeBackup(done = false)
        }
    }

    fun enablePush(enable: Boolean) {
        savedState.enablePush = enable
        routeTo(InitRoute.LabelAccount)
    }

    fun onPushPermissionRequested() {
        settingsRepository.pushPermissionRequested = true
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
        enterPasscodeJob?.cancel()
        enterPasscodeJob = null
        reEnterPopJob?.cancel()
        reEnterPopJob = null
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
        if (isOnboarding) {
            if (valid) {
                analytics.events.onboardingFlow.onboardingPasscodeCreated()
            } else {
                analytics.events.onboardingFlow.onboardingPasscodeMismatch()
            }
        }
        if (valid) {
            requestBiometryOrContinue()
        } else {
            _wrongPasscodeFlow.tryEmit(Unit)
            reEnterPopJob?.cancel()
            reEnterPopJob = viewModelScope.launch {
                delay(400)
                routePopBackStack()
            }
        }
    }

    fun enterExistingPasscode(passcode: String) {
        enterPasscodeJob?.cancel()
        enterPasscodeJob = viewModelScope.launch {
            val valid = withContext(Dispatchers.IO) { passcodeManager.isValid(context, passcode) }
            if (!valid) {
                _wrongPasscodeFlow.tryEmit(Unit)
                return@launch
            }
            savedState.passcode = passcode
            continueAfterPasscode()
        }
    }

    private fun requestBiometryOrContinue() {
        if (!requestSetPinCode || !PasscodeBiometric.isAvailableOnDevice(context)) {
            continueAfterPasscode()
            return
        }
        _eventFlow.tryEmit(InitEvent.RequestBiometry)
    }

    fun setBiometryEnabled(enabled: Boolean) {
        // MutableEffectFlow replays the last event to new collectors; drop the answered request so a
        // recreated InitScreen doesn't show the prompt again.
        _eventFlow.resetReplayCache()
        savedState.enableBiometry = enabled
        continueAfterPasscode()
    }

    private fun continueAfterPasscode() {
        if (watchRecoveryAccountId != null) {
            execute(context)
        } else if (type == InitArgs.Type.New) {
            routeTo(InitRoute.BackupStart)
        } else {
            routeToPushOrLabelAccount()
        }
    }

    // Full validation of an entered phrase, before anything tries to derive keys from it: the expected
    // number of words, every one of them from the BIP39 list, and a checksum valid under TON or BIP39.
    // Runs off the main thread — both checksum checks hash the phrase.
    suspend fun isValidMnemonic(words: List<String>): Boolean = withContext(Dispatchers.IO) {
        if (words.size != wordsCount || !MnemonicHelper.isValid(words)) {
            return@withContext false
        }
        try {
            MnemonicHelper.mnemonicType(words) != null
        } catch (e: Throwable) {
            L.e(e)
            false
        }
    }

    // True when a multichain wallet derived from this seed is already imported. The wallet id is
    // deterministic per seed, so re-importing would otherwise create a duplicate set of accounts.
    suspend fun isWalletAlreadyImported(words: List<String>): Boolean = withContext(Dispatchers.IO) {
        try {
            Mnemonic.from(MnemonicHelper.indexes(words)).use { mcAccountRepository.walletExists(it) }
        } catch (e: Throwable) {
            L.e(e)
            false
        }
    }

    suspend fun setMnemonic(words: List<String>): Boolean {
        // Any previously stored choice belongs to a different seed; clear it before resolving this one.
        savedState.mnemonicType = null

        // With multichain import disabled, every seed is imported the legacy way, as a plain TON
        // mnemonic. The type is forced so the downstream import takes the legacy path too.
        if (!isImportMultichainEnabled) {
            if (resolveWallets(words)) {
                savedState.mnemonicType = MnemonicType.Ton
                savedState.mnemonic = Mnemonic.fromWords(words.toTypedArray())
                return true
            }
            return false
        }

        val type = MnemonicHelper.mnemonicType(words)
        when (type) {
            // Ambiguous seed (valid as both TON and BIP39). Only offer the choice when the TON wallet
            // actually holds funds; otherwise import it as a multichain (BIP39) wallet without asking.
            MnemonicType.Both -> {
                Mnemonic.from(MnemonicHelper.indexes(words)).use { savedState.mnemonic = it }

                val tonNano = withContext(Dispatchers.IO) {
                    runCatching { tonKindBalanceNano(words) }.getOrDefault(0L)
                }
                if (tonNano > 0L) {
                    _selectedWalletKindFlow.value = WalletKind.Ton
                    // Only the TON wallet shows a balance; the multichain option is left empty.
                    _walletKindsFlow.value = listOf(
                        WalletKindItem(WalletKind.Ton, formatTonBalance(tonNano)),
                        WalletKindItem(WalletKind.Multichain),
                    )
                    routeTo(InitRoute.SelectMnemonicType)
                } else {
                    savedState.mnemonicType = MnemonicType.Bip39
                    resolveBip39Wallets(words)
                }
                return true
            }

            MnemonicType.Bip39 -> {
                Mnemonic.from(MnemonicHelper.indexes(words)).use { savedState.mnemonic = it }
                resolveBip39Wallets(words)
                return true
            }

            MnemonicType.Ton -> {
                if (resolveWallets(words)) {
                    Mnemonic.from(MnemonicHelper.indexes(words)).use { savedState.mnemonic = it }
                    return true
                }
            }

            else -> {}
        }

        return false
    }

    fun selectWalletKind(kind: WalletKind) {
        _selectedWalletKindFlow.value = kind
    }

    // Total TON balance (nano) across the wallet versions derived from the seed read as a TON mnemonic.
    private suspend fun tonKindBalanceNano(words: List<String>): Long {
        val publicKey = MnemonicHelper.privateKey(words).publicKey()
        return api.resolvePublicKey(publicKey, tonNetwork)
            .filter { it.walletVersion != WalletVersion.UNKNOWN }
            .sumOf { it.balance }
    }

    private fun formatTonBalance(nano: Long): String =
        CurrencyFormatter.format("TON", Coins.of(nano)).toString()

    private fun defaultWalletKindItems(): List<WalletKindItem> = listOf(
        WalletKindItem(WalletKind.Ton),
        WalletKindItem(WalletKind.Multichain),
    )

    fun confirmWalletKind() {
        viewModelScope.launch {
            val words = getMnemonic() ?: return@launch // TODO handle if we'll have problems
            when (_selectedWalletKindFlow.value) {
                WalletKind.Ton -> {
                    savedState.mnemonicType = MnemonicType.Ton
                    resolveWallets(words)
                }
                WalletKind.Multichain -> {
                    savedState.mnemonicType = MnemonicType.Bip39
                    resolveBip39Wallets(words)
                }
            }
        }
    }

    fun setLabel(name: String, emoji: String, color: Int): Wallet.Label {
        val label = Wallet.Label(name, emoji, color)
        setLabel(label)
        return label
    }

    private fun setLabel(label: Wallet.Label) {
        savedState.label = label
    }

    private suspend fun generateLabel(names: List<String> = emptyList()): Pair<CharSequence?, Int?> {
        return walletLabelHelper.generate(names, null)
    }

    fun nextStep(context: Context, from: InitRoute) {
        if (from == InitRoute.CreatePasscode) {
            routeTo(InitRoute.ReEnterPasscode)
        } else if (from == InitRoute.LabelAccount) {
            if (type == InitArgs.Type.New && isOnboarding) {
                analytics.events.onboardingFlow.onboardingClickCustomizeContinue()
            }
            execute(context)
        } else if (from == InitRoute.WatchAccount) {
            routeToPushOrLabelAccount()
        } else if (from == InitRoute.SelectAccount && !requestSetPinCode && !requestEnterPinCode) {
            applyAccountName()
            routeToPushOrLabelAccount()
        } else if (requestSetPinCode) {
            applyAccountName()
            routeTo(InitRoute.CreatePasscode)
        } else if (requestEnterPinCode) {
            applyAccountName()
            routeTo(InitRoute.EnterPasscode)
        } else {
            execute(context)
        }
    }

    private fun execute(context: Context) {
        if (isMultichainCreation()) {
            // New flow: show the lottie loader up front and keep it through the requests until done.
            _loaderState.value = InitLoaderState.Loading
        } else {
            setLoading(true)
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (requestSetPinCode) {
                    passcodeManager.save(savedState.passcode!!)
                    applyBiometry()
                }

                // TODO we should remove safeMode from the app
//                val alreadyWalletCount = accountRepository.getWallets()
//                if (alreadyWalletCount.isEmpty() && api.getConfig(TonNetwork.MAINNET).flags.safeModeEnabled) {
//                    settingsRepository.setSafeModeState(SafeModeState.Enabled)
//                    if (type == InitArgs.Type.Import || type == InitArgs.Type.Testnet || type == InitArgs.Type.Tetra) {
//                        settingsRepository.showSafeModeSetup = true
//                    }
//                }

                val wallets = mutableListOf<WalletEntity>()
                when (type) {
                    InitArgs.Type.AddWallet -> throw IllegalStateException("Wallet type is not selected")
                    InitArgs.Type.Watch -> {
                        wallets.add(saveWatchWallet())
                    }
                    InitArgs.Type.Import, InitArgs.Type.Testnet, InitArgs.Type.Tetra -> {
                        when (val wallet = newImportWallet()) {
                            is ImportedWallet.Multicoin-> {
                                trackImportSuccess()
                                reportRaffleImport(wallet.wallet.id)
                                applyMultichainPush(wallet.wallet.id)
                                awaitSyncThenDone(wallet.wallet.id)
                                return@launch
                            }
                            is ImportedWallet.Legacy -> {
                                wallets.addAll(wallet.wallets)
                            }
                        }
                    }

                    InitArgs.Type.Signer -> wallets.addAll(signerWallets(false))
                    InitArgs.Type.SignerQR -> wallets.addAll(signerWallets(true))
                    InitArgs.Type.Ledger -> wallets.addAll(ledgerWallets())
                    InitArgs.Type.Keystone -> wallets.addAll(keystoneWallet())

                    InitArgs.Type.New -> {
                        if (isMultichainEnabled) {
                            val wallet = setupMultichainCoin(true)
                            analytics.events.walletFlow.walletCreateSuccess(
                                walletMode = WalletFlowWalletMode.Multi,
                                backedUp = savedState.backupDone,
                                from = analyticsFrom,
                            )
                            applyMultichainPush(wallet.id)
                            awaitSyncThenDone(wallet.id)
                            return@launch
                        } else {
                            wallets.add(newWallet(context))
                        }
                    }
                }

                if (type == InitArgs.Type.Import || type == InitArgs.Type.Testnet || type == InitArgs.Type.Tetra || savedState.backupDone) {
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

                val selectedWallet = wallets.minByOrNull { it.version }!!
                accountRepository.setSelectedWallet(selectedWallet.id)

                if (type == InitArgs.Type.New) {
                    analytics.events.walletFlow.walletCreateSuccess(
                        walletMode = WalletFlowWalletMode.Single,
                        backedUp = savedState.backupDone,
                        from = analyticsFrom,
                    )
                } else {
                    trackImportSuccess()
                }

                if (type == InitArgs.Type.Watch) {
                    openScreen(WatchInfoScreen.newInstance(selectedWallet))
                } else {
                    _loaderState.value = InitLoaderState.Done(selectedWallet.id)
                }

                // The screen is a bottom sheet now, so nothing removes it from the outside anymore:
                // the primary-fragment replace only touches the root container. Close it explicitly,
                // revealing the main screen that was swapped in behind the sheet.
                finish()
            } catch (e: WalletRegistrationException) {
                // Backend registration failed — hide the loader, surface an error and step back so the
                // user can retry.
                trackImportError(e)
                context.logError(e)
                _loaderState.value = null
                setLoading(false)
                toast(Localization.unknown_error)
                routePopBackStack()
            } catch (e: Throwable) {
                trackImportError(e)
                context.logError(e)
                _loaderState.value = null
                setLoading(false)
            }
        }
    }

    private fun setInitName(name: String) {
        if (name.isBlank()) {
            return
        }

        val (parsedName, parsedEmoji) = walletLabelHelper.parseNameAndEmoji(listOf(name))

        if (parsedName.isNullOrBlank() && parsedEmoji.isNullOrEmpty()) {
            return
        }

        setLabel(
            Wallet.Label(
                accountName = parsedName ?: "",
                emoji = parsedEmoji ?: "",
            )
        )
    }

    private suspend fun applyAccountName(accounts: List<AccountItem>) {
        val names = accounts.mapNotNull { it.name?.trim() }

        val (emoji, color) = generateLabel(names)

        setLabel(
            Wallet.Label(
                accountName = names.firstOrNull() ?: getDefaultWalletName(),
                emoji = emoji ?: Emoji.WALLET_ICON,
                color = color ?: WalletColor.all.first()
            )
        )
    }

    private fun applyAccountName() {
        viewModelScope.launch {
            applyAccountName(getSelectedAccounts())
        }
    }

    // TON-WALLET ONLY
    private suspend fun getAccountItem(
        account: AccountDetailsEntity,
        position: ListCell.Position,
    ): AccountItem = withContext(Dispatchers.IO) {
        val currencyCode = settingsRepository.currency.code
        if (account.new) {
            AccountItem(
                address = AddrStd(account.address).toWalletAddress(testnet),
                name = account.name,
                walletVersion = account.walletVersion,
                balanceFormat = CurrencyFormatter.formatFiat(currencyCode, BigDecimal.ZERO),
                tokens = false,
                collectibles = false,
                selected = true,
                position = position,
                initialized = false
            )
        } else {
            val tokensDeferred = async { api.getJettonsBalances(account.address, tonNetwork, currencyCode) }
            val nftItemsDeferred = async { api.getNftItems(account.address, tonNetwork, 1) }
            val tonRateDeferred = async { api.getRates(tonNetwork, "TON", currencyCode) }
            val tokens = tokensDeferred.await() ?: emptyList()
            val nftItems = nftItemsDeferred.await() ?: emptyList()
            val balance = Coins.of(account.balance)
            val hasTokens = tokens.isNotEmpty()
            val hasNftItems = nftItems.isNotEmpty()
            val tonPrice = tonRateDeferred.await()?.get("TON")?.fiatPrice(currencyCode)
            val balanceFormat = if (tonPrice == null) {
                CurrencyFormatter.format("TON", balance)
            } else {
                var totalFiat = balance.value * tonPrice
                for (token in tokens) {
                    val price = (token.rates as? TokenRates)?.fiatPrice(currencyCode) ?: continue
                    totalFiat += token.value.value * price
                }
                CurrencyFormatter.formatFiat(currencyCode, totalFiat)
            }
            AccountItem(
                address = AddrStd(account.address).toWalletAddress(testnet),
                name = account.name,
                walletVersion = account.walletVersion,
                balanceFormat = balanceFormat,
                tokens = hasTokens,
                collectibles = hasNftItems,
                selected = account.walletVersion == WalletVersion.V5R1 || (account.balance > 0 || hasTokens || hasNftItems),
                position = position,
                initialized = account.initialized
            )
        }
    }

    private fun setPublicKey(publicKey: PublicKeyEd25519?) {
        if (publicKey == null || publicKey == EmptyPrivateKeyEd25519.publicKey()) {
            savedState.publicKey = null
        } else {
            savedState.publicKey = InitModelState.PublicKey(publicKey = publicKey)
        }
    }

    private suspend fun setWatchAccount(
        query: String?,
        account: AccountDetailsEntity?
    ) {
        if (account == null) {
            setPublicKey(null)
            savedState.watchAccount = null
            setLabel(Wallet.Label())
        }

        val oldAccount = getWatchAccount()
        if (oldAccount?.equals(account) == true) {
            return
        }

        val publicKey = account?.address?.let {
            api.safeGetPublicKey(it, tonNetwork)
        }
        setPublicKey(publicKey)

        savedState.watchAccount = account

        val names = listOfNotNull(account?.name, query)
        val (generatedEmoji, generatedColor) = generateLabel(names)
        val accountName = account?.name ?: getDefaultWalletName()

        setLabel(
            Wallet.Label(
                accountName = accountName,
                emoji = generatedEmoji ?: Emoji.WALLET_ICON,
                color = generatedColor ?: WalletColor.all.first()
            )
        )
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

    private suspend fun resolveWallets(mnemonic: List<String>): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val privateKey = MnemonicHelper.privateKey(mnemonic)
                val publicKey = privateKey.publicKey()
                setPublicKey(publicKey)
                resolveWallets(InitModelState.PublicKey(publicKey = publicKey))
                true
            } catch (e: Throwable) {
                L.e(e)
                false
            }
        }

    // For a Bip39 (multichain) seed we only check the two TON wallet versions a user might already
    // hold assets on: v4r2 and v5 (W5). Both funded -> the user picks which one to import; exactly
    // one funded -> that version is imported silently; none funded -> W5 (no selection is recorded).
    private suspend fun resolveBip39Wallets(mnemonic: List<String>) = withContext(Dispatchers.IO) {
        // The user can walk back to the words screen and enter a different seed: items and selection
        // left over from the previous seed would otherwise decide the version for the new one.
        _walletAccountsFlow.value = emptyList()
        _selectedAddressFlow.value = null

        val cryptoWallet = try {
            CryptoWallet.fromMnemonic(mnemonic.joinToString(" "))
        } catch (e: Throwable) {
            L.e(e)
            null
        }
        val versions = if (cryptoWallet != null) {
            tonWalletVersionInteractor.resolve(cryptoWallet, tonNetwork)
        } else {
            WalletVersions.Empty
        }

        _walletAccountsFlow.value = versions.items
        when {
            versions.funded.size > 1 -> {
                selectWalletAccount(versions.items.first().account)
                routeTo(InitRoute.SelectWalletVersion)
            }
            versions.funded.size == 1 -> {
                selectWalletAccount(versions.funded.first().account)
                continueAfterMnemonic()
            }
            else -> {
                continueAfterMnemonic()
            }
        }
    }

    fun selectWalletAccount(account: AccountWithDetails) {
        _selectedAddressFlow.value = account.data.displayAddress
    }

    // The TON variant the user picked in the version selector. Falls back to W5 when nothing was
    // selected (the new-wallet flow never opens the selector).
    private fun selectedTonWalletType(): Address.Type {
        val selectedAddress = _selectedAddressFlow.value

        val selected = _walletAccountsFlow.value
            .firstOrNull { it.account.data.displayAddress == selectedAddress }
            ?.account

        return when (selected?.data?.addressType) {
            Address.Type.TonV4R2 -> Address.Type.TonV4R2
            else -> Address.Type.TonV5R1
        }
    }

    fun confirmWalletVersion() {
        continueAfterMnemonic()
    }

    private fun continueAfterMnemonic() {
        if (requestSetPinCode) {
            routeTo(InitRoute.CreatePasscode)
        } else if (requestEnterPinCode) {
            routeTo(InitRoute.EnterPasscode)
        } else {
            routeToPushOrLabelAccount()
        }
    }

    private fun routeToPushOrLabelAccount() {
        if (needPushPermission) {
            routeTo(InitRoute.Push)
            return
        }
        savedState.enablePush = pushEnabledByDefault
        routeTo(InitRoute.LabelAccount)
    }

    private suspend fun resolveWallets(publicKey: InitModelState.PublicKey) =
        withContext(Dispatchers.IO) {
            val accounts = if (publicKey.new) {
                mutableListOf()
            } else {
                api.resolvePublicKey(publicKey.publicKey, tonNetwork).filter {
                    it.walletVersion != WalletVersion.UNKNOWN
                }.sortedByDescending { it.walletVersion.index }.toMutableList()
            }

            if (accounts.count { it.walletVersion == WalletVersion.V5R1 } == 0) {
                val network = if (tonNetwork.isTetra) {
                    TonNetwork.TETRA
                } else {
                    tonNetwork
                }
                val contract = WalletV5R1Contract(publicKey.publicKey, network)
                val query = contract.address.toAccountId()
                if (publicKey.new) {
                    accounts.add(
                        0,
                        AccountDetailsEntity(contract, tonNetwork, new = true, initialized = false)
                    )
                } else {
                    val apiAccount = api.resolveAccount(query, tonNetwork)
                    val account = if (apiAccount == null) {
                        AccountDetailsEntity(
                            contract,
                            tonNetwork,
                            new = true,
                            initialized = false
                        )
                    } else {
                        AccountDetailsEntity(
                            query, apiAccount.copy(
                                interfaces = listOf("wallet_v5r1")
                            ), tonNetwork, false
                        )
                    }
                    accounts.add(0, account)
                }
            }

            val list = accounts.mapIndexed { index, account ->
                getAccountItem(account, ListCell.getPosition(accounts.size, index))
            }

            val recoveryWallet = getRecoveryWatchWallet(publicKey = publicKey.publicKey)
            val recoveryAccountItem = accounts.firstOrNull { it.preview.accountId == recoveryWallet?.accountId }?.let {
                getAccountItem(it, ListCell.Position.SINGLE)
            }

            val items = mutableListOf<AccountItem>()
            if (recoveryAccountItem != null) {
                items.add(recoveryAccountItem)
            } else {
                for (account in list) {
                    items.add(account)
                }
            }
            setAccounts(items.toList())

            if (recoveryWallet != null) {
                savedState.enablePush = settingsRepository.getPushWallet(recoveryWallet.id)
            }

            if (recoveryWallet != null && !requestSetPinCode) {
                if (requestEnterPinCode) {
                    applyAccountName(items)
                    routeTo(InitRoute.EnterPasscode)
                } else {
                    execute(context)
                }
            } else if (items.size > 1) {
                routeTo(InitRoute.SelectAccount)
            } else if (requestSetPinCode) {
                applyAccountName(items)
                routeTo(InitRoute.CreatePasscode)
            } else if (requestEnterPinCode) {
                applyAccountName(items)
                routeTo(InitRoute.EnterPasscode)
            } else {
                routeToPushOrLabelAccount()
            }
        }

    private suspend fun getLabel(): Wallet.Label = withContext(Dispatchers.IO) {
        val savedLabel = savedState.label
        if (savedLabel != null && !savedLabel.isEmpty) {
            savedLabel
        } else {
            Wallet.Label(
                accountName = "",
                emoji = "",
                color = Color.TRANSPARENT
            )
        }
    }

    suspend fun getRecoveryWatchWallet(mnemonic: List<String>): WalletEntity? =
        withContext(Dispatchers.IO) {
            val publicKey = try {
                MnemonicHelper.privateKey(mnemonic).publicKey()
            } catch (e: Throwable) {
                L.e(e)
                return@withContext null
            }
            getRecoveryWatchWallet(publicKey)
        }

    suspend fun getRecoveryWatchWallet(publicKey: PublicKeyEd25519): WalletEntity? =
        withContext(Dispatchers.IO) {
            if (watchRecoveryAccountId == null) {
                return@withContext null
            }

            val wallet = accountRepository.getWallets()
                .firstOrNull { it.accountId == watchRecoveryAccountId && it.type == WalletType.Watch }
                ?: return@withContext null

            if (wallet.publicKey == publicKey) {
                wallet
            } else {
                null
            }
        }

    private suspend fun buildNewLabel(accounts: List<SimpleAccount>): Wallet.NewLabel {
        val versions = accounts.map { it.version }
        val label = getLabel()
        val isMultipleVersions = versions.distinct().size > 1
        val isUsingDefaultName = label.name == getDefaultWalletName()

        val names = if (isUsingDefaultName && !isMultipleVersions && accounts.size > 1) {
            val base = getString(Localization.wallet)
            // taken excludes label.name: the first generated name must match the prefilled label
            val taken = getExistingWalletLabels().toMutableList()
            accounts.map {
                DefaultWalletName.suggest(base, taken).also(taken::add)
            }
        } else {
            accounts.map { account ->
                if (isMultipleVersions) {
                    label.name + " " + account.version.title.fixW5Title()
                } else {
                    label.name
                }
            }
        }

        val (generatedEmoji, generatedColor) = generateLabel(names)

        val emoji = label.emoji.ifBlank {
            generatedEmoji
        }

        val color = if (label.color == Color.TRANSPARENT) {
            generatedColor
        } else {
            label.color
        }

        return Wallet.NewLabel(
            names = names,
            emoji = emoji ?: Emoji.WALLET_ICON,
            color = color ?: WalletColor.all.first()
        )
    }

    private suspend fun saveWatchWallet(): WalletEntity {
        val account = getWatchAccount() ?: throw IllegalStateException("Account is not set")
        val publicKey = savedState.publicKey?.publicKey ?: EmptyPrivateKeyEd25519.publicKey()

        val label = buildNewLabel(SimpleAccount(account))

        return accountRepository.addWatchWallet(label, publicKey, account.walletVersion)
    }

    private suspend fun buildNewLabel(account: SimpleAccount): Wallet.NewLabel {
        return buildNewLabel(listOf(account))
    }

    private suspend fun newWallet(context: Context): WalletEntity {
        val mnemonic = savedState.mnemonic ?: throw IllegalStateException("Mnemonic is not set")
        val mnemonicList = mnemonic.toWordsUnsafeList()
        val walletId = AccountRepository.newWalletId()
        saveMnemonic(context, listOf(walletId), mnemonicList)
        val label = buildNewLabel(SimpleAccount(version = WalletVersion.V5R1))
        return accountRepository.addNewWallet(walletId, label, mnemonicList)
    }

    private suspend fun importWallet(context: Context): List<WalletEntity> {
        return withContext(Dispatchers.IO) {
            val accounts = getSelectedAccounts()
            if (accounts.isEmpty()) {
                throw IllegalStateException("Wallet versions are not set")
            }

            val mnemonic = savedState.mnemonic ?: throw IllegalStateException("Mnemonic is not set")
            val mnemonicList = mnemonic.toWordsUnsafeList()
            if (!MnemonicHelper.isValid(mnemonicList)) {
                throw IllegalStateException("Invalid mnemonic")
            }

            val ids = accounts.map { AccountRepository.newWalletId() }
            saveMnemonic(context, ids, mnemonicList) // TODO legacy

            val recoveryWallet = getRecoveryWatchWallet(mnemonicList)

            val label = recoveryWallet?.let {
                Wallet.NewLabel(
                    names = listOf(it.label.name),
                    emoji = it.label.emoji,
                    color = it.label.color
                )
            } ?: buildNewLabel(accounts.map {
                SimpleAccount(
                    name = it.name,
                    version = it.walletVersion
                )
            })

            val type = when {
                testnet -> WalletType.Testnet
                tetra -> WalletType.Tetra
                else -> WalletType.Default
            }

            val wallets = accountRepository.importWallet(
                ids,
                label,
                mnemonicList,
                accounts.map { it.walletVersion },
                type,
                accounts.map { it.initialized },
            )

            // delete watch only wallet
            recoveryWallet?.let {
                PushToggleWorker.run(context, recoveryWallet, PushManager.State.Delete)
                unifiedAccountRepository.deleteWallet(it.id)
            }

            if (!testnet && !tetra) {
                checkTronBalance(wallets)
            }

            wallets
        }
    }

    private suspend fun checkTronBalance(wallets: List<WalletEntity>) {
        val wallet = wallets.first()
        val tronAddress = accountRepository.getTronAddress(wallet.id) ?: return
        val balance = api.tron.getTronUsdtBalance(tronAddress)

        if (balance.value.isPositive) {
            wallets.forEach {
                settingsRepository.setTokenHidden(it.id, TokenEntity.TRON_USDT.address, false)
                settingsRepository.setTokenPinned(it.id, TokenEntity.TRON_USDT.address, true)
                settingsRepository.setTokensSort(
                    wallet.id,
                    listOf(
                        TokenEntity.USDT.address,
                        TokenEntity.TRON_USDT.address,
                        TokenEntity.USDE.address
                    )
                )
            }
        }
    }

    private suspend fun ledgerWallets(): List<WalletEntity> {
        val ledgerConnectData = savedState.ledgerConnectData
            ?: throw IllegalStateException("Ledger connect data is not set")

        val accounts = getSelectedAccounts()

        val ledgerAccounts = accounts.map { selectedAccount ->
            ledgerConnectData.accounts.find { account ->
                account.path.index == selectedAccount.ledgerIndex
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

        return accountRepository.pairSigner(
            label,
            publicKey.publicKey,
            accounts.map { it.walletVersion },
            qr,
            accounts.map { it.initialized })
    }

    private suspend fun keystoneWallet(): List<WalletEntity> {
        val publicKey = savedState.publicKey ?: throw IllegalStateException("Public key is not set")
        val keystone = savedState.keystone ?: throw IllegalStateException("Keystone is not set")

        val label = buildNewLabel(
            SimpleAccount(
                version = WalletVersion.V4R2
            )
        )

        val contact = BaseWalletContract.create(
            publicKey.publicKey,
            WalletVersion.V4R2.title,
            tonNetwork
        )
        val account =
            api.resolveAccount(contact.address.toWalletAddress(testnet = testnet), tonNetwork)
        val initialized =
            account != null && (account.status == AccountStatus.active || account.status == AccountStatus.frozen)

        return accountRepository.pairKeystone(label, publicKey.publicKey, keystone, initialized)
    }

    private suspend fun saveMnemonic(
        context: Context,
        walletIds: List<String>,
        mnemonic: List<String>
    ) = withContext(Dispatchers.IO) {
        if (requestSetPinCode || savedState.passcode != null) {
            return@withContext
        }

        if (passcodeManager.hasPinCode()) {
            val isValid =
                passcodeManager.confirmation(context, context.getString(Localization.app_name))
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

    private data class SimpleAccount(
        val name: String? = null,
        val version: WalletVersion
    ) {

        constructor(account: AccountDetailsEntity) : this(
            name = account.name,
            version = account.walletVersion
        )
    }


    // MULTICHAIN
    private suspend fun getDefaultWalletName(): String {
        return DefaultWalletName.suggest(getString(Localization.wallet), getExistingWalletLabels())
    }

    private suspend fun generateNewWallet() = withContext(Dispatchers.IO) {
        if (savedState.hasMnemonic) {
            return@withContext
        }

        savedState.mnemonic = if (isMultichainEnabled) {
            mcAccountRepository.createMnemonic()
        } else {
            AndroidSecureRandom.seed(entropyHelper.getSeed(512))
            val words = TonMnemonicGenerator.generate(random = AndroidSecureRandom).words.toTypedArray()
            Mnemonic.from(MnemonicHelper.indexes(words.toList())).also { words.fill("") }
        }
    }

    private suspend fun generateMultiCoinLabel(): Wallet.Label {
        val (emoji, color) = generateLabel()

        return setLabel(
            name = getDefaultWalletName(),
            emoji = emoji?.toString() ?: Emoji.WALLET_ICON,
            color = color ?: WalletColor.all.first()
        )
    }

    private suspend fun getExistingWalletLabels(): List<String> {
        existingWalletLabels?.let {
            return it
        }

        val labels = unifiedAccountRepository.getWalletLabels()
        existingWalletLabels = labels
        return labels
    }

    private suspend fun getWalletsCount(): Int {
        val count = walletsCount.get()
        return if (0 > count) {
            unifiedAccountRepository.getTonWallets().size.also {
                walletsCount.set(it)
                savedState.walletsCount = it
            }
        } else {
            count
        }
    }

    private suspend fun setupMultichainCoin(isCreate: Boolean): McWalletEntity {
        val mnemonic = savedState.mnemonic!!

        // The TON variant the user chose in the version selector (W5 for the new-wallet flow, which
        // never opens the selector). Both variants are still created and synced; this only sets which
        // one McWalletEntity stores as primary.
        val tonWalletType = selectedTonWalletType()

        val savedLabel = savedState.label ?: generateMultiCoinLabel()
        val passcode = savedState.passcode ?: passcodeManager.requestValidPasscode(context)
        val pin = passcode.toCharArray()

        val wallet = try {
            passcodeManager.unlockOrCreateMultichainVault(pin) { coder ->
                mcAccountRepository.createWallet(
                    mnemonic = mnemonic,
                    mnemonicCoder = coder,
                    name = savedLabel.name,
                    emoji = savedLabel.emoji,
                    color = savedLabel.color,
                    tonWalletType = tonWalletType,
                )
            }
        } finally {
            pin.fill(0.toChar())
        }

        val wallets = listOf(wallet)
        if (!isCreate || savedState.backupDone) {
            backupRepository.addBackups(wallets.map { it.id })
        }

//        PushToggleWorker.run(context, wallets, PushManager.State.Enable)
//        TotalBalancesWorker.run(context) // TODO?

        return wallet
    }

    private suspend fun newImportWallet(): ImportedWallet {
        return withContext(Dispatchers.IO) {
            if (savedState.mnemonic == null) {
                throw IllegalStateException("Mnemonic is not set")
            }
            val type = effectiveMnemonicType() ?: throw IllegalStateException("Invalid mnemonic")

            when (type) {
                MnemonicType.Ton -> {
                    ImportedWallet.Legacy(importWallet(context))
                }
                // For an ambiguous (Both) seed the user already chose, so mnemonicType is set above and
                // Both never reaches here; default to multichain just in case.
                MnemonicType.Bip39,
                MnemonicType.Both -> {
                    ImportedWallet.Multicoin(setupMultichainCoin(false))
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        entropyHelper.stop()
    }
}

sealed interface ImportedWallet {
    class Legacy(val wallets: List<WalletEntity>): ImportedWallet
    class Multicoin(val wallet: McWalletEntity): ImportedWallet
}

private fun TokenRates.fiatPrice(currency: String): BigDecimal? =
    prices?.get(currency)?.toBigDecimalOrNull()
