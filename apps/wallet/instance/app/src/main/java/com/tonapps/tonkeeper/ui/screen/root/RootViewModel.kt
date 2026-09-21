package com.tonapps.tonkeeper.ui.screen.root

import android.app.Application
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.webkit.WebView
import androidx.core.content.edit
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.net.toUri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.google.firebase.Firebase
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.crashlytics.crashlytics
import com.google.firebase.crashlytics.setCustomKeys
import com.tonapps.base64.decodeBase64
import com.tonapps.blockchain.model.legacy.WalletCurrency
import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.blockchain.model.legacy.WalletType
import com.tonapps.blockchain.ton.TonNetwork
import com.tonapps.blockchain.ton.connect.TONProof
import com.tonapps.blockchain.ton.extensions.equalsAddress
import com.tonapps.blockchain.ton.extensions.hex
import com.tonapps.blockchain.ton.extensions.toAccountId
import com.tonapps.bus.core.AnalyticsHelper
import com.tonapps.bus.generated.Events
import com.tonapps.bus.generated.Events.BatteryNative.BatteryNativeFrom
import com.tonapps.bus.generated.Events.DepositFlow.DepositFlowFrom
import com.tonapps.bus.generated.Events.WalletFlow.WalletFlowSource
import com.tonapps.bus.generated.Events.WithdrawFlow.WithdrawFlowFrom
import com.tonapps.chainkit.core.chain.model.account.Chain
import com.tonapps.chainkit.core.chain.model.account.Network
import com.tonapps.core.deeplink.DeepLink
import com.tonapps.core.deeplink.DeepLinkRoute
import com.tonapps.core.deeplink.withRaffleSourceWalletId
import com.tonapps.core.flags.InAppReviewManager
import com.tonapps.core.flags.WalletFeature
import com.tonapps.core.helper.navigationDelegate
import com.tonapps.dapp.screens.confirm.DappConfirmFragment
import com.tonapps.dapp.screens.session.WcSessionFragment
import com.tonapps.deposit.DepositFragment
import com.tonapps.deposit.DepositRoutes
import com.tonapps.deposit.WithdrawFragment
import com.tonapps.deposit.WithdrawRoutes
import com.tonapps.deposit.multicoin.DepositMulticoinFragment
import com.tonapps.deposit.multicoin.DepositMulticoinRoutes
import com.tonapps.deposit.multicoin.WcConfirmFragment
import com.tonapps.deposit.multicoin.WithdrawMulticoinFragment
import com.tonapps.deposit.multicoin.WithdrawMulticoinRoutes
import com.tonapps.deposit.screens.qr.QrAssetFragment
import com.tonapps.extensions.MutableEffectFlow
import com.tonapps.extensions.bestMessage
import com.tonapps.extensions.currentTimeSeconds
import com.tonapps.extensions.getStringValue
import com.tonapps.extensions.setLocales
import com.tonapps.extensions.toUriOrNull
import com.tonapps.icu.Coins
import com.tonapps.ledger.ton.LedgerConnectData
import com.tonapps.legacy.enteties.WalletPurchaseMethodEntity
import com.tonapps.log.L
import com.tonapps.migration.MigrationFragment
import com.tonapps.migration.analytics.MigrationAnalytics
import com.tonapps.swap.SwapFragment
import com.tonapps.swap.SwapRoutes
import com.tonapps.tonkeeper.App
import com.tonapps.tonkeeper.Environment
import com.tonapps.tonkeeper.api.getCurrencyCodeByCountry
import com.tonapps.tonkeeper.billing.BillingManager
import com.tonapps.tonkeeper.client.safemode.SafeModeClient
import com.tonapps.tonkeeper.core.DevSettings
import com.tonapps.tonkeeper.core.FirebaseHelper
import com.tonapps.tonkeeper.core.history.ActionOptions
import com.tonapps.tonkeeper.core.history.HistoryHelper
import com.tonapps.tonkeeper.core.history.list.item.HistoryItem
import com.tonapps.tonkeeper.extensions.getAppFixIcon
import com.tonapps.tonkeeper.extensions.hasRefer
import com.tonapps.tonkeeper.extensions.hasUtmSource
import com.tonapps.tonkeeper.extensions.safeExternalOpenUri
import com.tonapps.tonkeeper.helper.BrowserHelper
import com.tonapps.tonkeeper.helper.ReferrerClientHelper
import com.tonapps.tonkeeper.helper.ShortcutHelper
import com.tonapps.tonkeeper.manager.apk.APKManager
import com.tonapps.tonkeeper.manager.push.FirebasePush
import com.tonapps.tonkeeper.manager.push.PushManager
import com.tonapps.tonkeeper.manager.shortcut.AppShortcutRepository
import com.tonapps.tonkeeper.manager.tonconnect.ITonConnectBridge
import com.tonapps.tonkeeper.manager.tonconnect.bridge.BridgeException
import com.tonapps.tonkeeper.manager.tonconnect.bridge.model.BridgeError
import com.tonapps.tonkeeper.manager.tonconnect.bridge.model.SignDataRequestPayload
import com.tonapps.tonkeeper.manager.walletkit.WalletKitEvent
import com.tonapps.tonkeeper.os.AppInfo
import com.tonapps.tonkeeper.ui.base.BaseWalletVM
import com.tonapps.tonkeeper.ui.component.UpdateAvailableDialog
import com.tonapps.portfolio.screens.raffle.ConfigStoriesAutoShow
import com.tonapps.portfolio.screens.raffle.RaffleFragment
import com.tonapps.portfolio.screens.raffle.RaffleStoryAutoShow
import com.tonapps.portfolio.screens.raffle.StoryAutoShowSession
import com.tonapps.tonkeeper.ui.screen.init.InitArgs
import com.tonapps.tonkeeper.ui.screen.init.InitScreen
import com.tonapps.tonkeeper.ui.screen.backup.main.BackupScreen
import com.tonapps.tonkeeper.ui.screen.battery.BatteryScreen
import com.tonapps.tonkeeper.ui.screen.browser.dapp.DAppScreen
import com.tonapps.tonkeeper.ui.screen.browser.safe.DAppSafeScreen
import com.tonapps.tonkeeper.ui.screen.camera.CameraScreen
import com.tonapps.tonkeeper.ui.screen.collectibles.main.CollectiblesScreen
import com.tonapps.tonkeeper.ui.screen.dns.renew.DNSRenewScreen
import com.tonapps.tonkeeper.ui.screen.events.compose.history.TxEventsScreen
import com.tonapps.wallet.features.events.screens.EventsFragment
import com.tonapps.tonkeeper.ui.screen.init.list.AccountItem
import com.tonapps.tonkeeper.ui.screen.name.edit.EditNameScreen
import com.tonapps.tonkeeper.ui.screen.send.main.SendScreen
import com.tonapps.tonkeeper.ui.screen.send.transaction.SendTransactionScreen
import com.tonapps.tonkeeper.ui.screen.settings.currency.CurrencyScreen
import com.tonapps.tonkeeper.ui.screen.settings.extensions.ExtensionsScreen
import com.tonapps.tonkeeper.ui.screen.settings.language.LanguageScreen
import com.tonapps.tonkeeper.ui.screen.settings.main.SettingsScreen
import com.tonapps.tonkeeper.ui.screen.settings.security.SecurityScreen
import com.tonapps.tonkeeper.ui.screen.sign.SignDataScreen
import com.tonapps.tonkeeper.ui.screen.staking.stake.StakingScreen
import com.tonapps.tonkeeper.ui.screen.staking.viewer.StakeViewerScreen
import com.tonapps.tonkeeper.ui.screen.stories.remote.RemoteStoriesScreen
import com.tonapps.tonkeeper.ui.screen.token.viewer.TokenScreen
import com.tonapps.tonkeeper.ui.screen.transaction.TransactionScreen
import com.tonapps.tonkeeper.ui.screen.wallet.manage.TokensManageScreen
import com.tonapps.tonkeeper.ui.screen.wallet.picker.PickerScreen
import com.tonapps.tonkeeper.worker.DAppPushToggleWorker
import com.tonapps.tonkeeperx.R
import com.tonapps.trading.AssetsFragment
import com.tonapps.trading.TradeEntryTracker
import com.tonapps.wallet.api.API
import com.tonapps.wallet.api.configs.CountryConfig
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.browser.BrowserRepository
import com.tonapps.wallet.data.core.entity.SignRequestEntity
import com.tonapps.wallet.data.dapps.DAppsRepository
import com.tonapps.wallet.data.dapps.entities.AppConnectEntity
import com.tonapps.wallet.data.dapps.entities.AppEntity
import com.tonapps.wallet.data.dapps.wc.WcRepository
import com.tonapps.wallet.data.dapps.wc.WcRequestResult
import com.tonapps.wallet.data.multichain.account.AccountWithDetails
import com.tonapps.wallet.data.multichain.account.McAccountRepository
import com.tonapps.wallet.data.multichain.account.UnifiedAccountRepository
import com.tonapps.wallet.data.multichain.device.SecureDeviceRepository
import com.tonapps.wallet.data.multichain.realtime.McWalletRealtimeProvider
import com.tonapps.wallet.data.passcode.LockScreen
import com.tonapps.wallet.data.passcode.PasscodeManager
import com.tonapps.wallet.data.purchase.PurchaseRepository
import com.tonapps.tonkeeper.koin.koin
import com.tonapps.wallet.data.raffle.RaffleRepository
import com.tonapps.wallet.data.raffle.ownsStoryId
import com.tonapps.wallet.data.raffle.toStories
import com.tonapps.wallet.data.raffle.debug.RaffleDebugStore
import com.tonapps.wallet.data.rates.RatesRepository
import com.tonapps.wallet.data.settings.SettingsRepository
import com.tonapps.wallet.data.token.TokenRepository
import com.tonapps.wallet.localization.Localization
import com.tonapps.wallet.localization.RStr
import com.tonapps.walletkit.WalletKitRequestHandler
import com.tonapps.wc.models.WcConnection
import io.ton.walletkit.request.TONWalletConnectionRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.CancellationException
import kotlin.math.abs

private val regexPrivateData: Regex by lazy {
    Regex("[a-f0-9]{64}|0:[a-f0-9]{64}")
}

private fun removePrivateDataFromUrl(url: String): String {
    return url.replace(regexPrivateData, "X")
}

@Suppress("LargeClass")
class RootViewModel(
    app: Application,
    private val settingsRepository: SettingsRepository,
    private val accountRepository: AccountRepository,
    private val unifiedAccountRepository: UnifiedAccountRepository,
    private val api: API,
    private val historyHelper: HistoryHelper,
    private val purchaseRepository: PurchaseRepository,
    private val tonConnectBridge: ITonConnectBridge,
    private val browserRepository: BrowserRepository,
    private val pushManager: PushManager,
    private val tokenRepository: TokenRepository,
    private val environment: Environment,
    private val passcodeManager: PasscodeManager,
    private val apkManager: APKManager,
    private val referrerClientHelper: ReferrerClientHelper,
    private val dAppsRepository: DAppsRepository,
    private val safeModeClient: SafeModeClient,
    private val ratesRepository: RatesRepository,
    private val analyticsHelper: AnalyticsHelper,
    private val billingManager: BillingManager,
    private val wcRepository: WcRepository,
    savedStateHandle: SavedStateHandle,
): BaseWalletVM(app) {

    private val savedState = RootModelState(savedStateHandle)

    // Resolved via Koin rather than the constructor only because viewModelOf
    // caps constructors at 22 parameters and RootViewModel is already at the
    // limit; move these to the constructor when the class gets split up.
    private val mcAccountRepository: McAccountRepository? by lazy { context.koin?.get() }
    private val mcWalletRealtimeProvider: McWalletRealtimeProvider? by lazy { context.koin?.get() }
    private val secureDeviceRepository: SecureDeviceRepository by lazy { requireNotNull(context.koin).get() }
    private val raffleRepository: RaffleRepository? by lazy { context.koin?.get() }
    private val appShortcutRepository: AppShortcutRepository? by lazy { context.koin?.get() }
    private val raffleDebugStore: RaffleDebugStore? by lazy { context.koin?.get() }
    private val storyAutoShowSession = StoryAutoShowSession()
    private val raffleStoryAutoShow: RaffleStoryAutoShow? by lazy {
        val repository = raffleRepository ?: return@lazy null
        val debugStore = raffleDebugStore ?: return@lazy null
        RaffleStoryAutoShow(repository, settingsRepository, debugStore, storyAutoShowSession)
    }
    private val configStoriesAutoShow: ConfigStoriesAutoShow by lazy {
        ConfigStoriesAutoShow(api, settingsRepository, raffleDebugStore, storyAutoShowSession)
    }

    private val selectedWalletFlow: Flow<WalletEntity> = unifiedAccountRepository.selectedTonWalletFlow.filterNotNull()

    private val _hasWalletFlow = MutableEffectFlow<Boolean?>()
    val hasWalletFlow = _hasWalletFlow.asSharedFlow().filterNotNull()

    private val _eventFlow = MutableEffectFlow<RootEvent?>()
    val eventFlow = _eventFlow.asSharedFlow().filterNotNull()

    private val _inAppReviewRequestFlow = Channel<Unit>(capacity = Channel.BUFFERED)
    val inAppReviewRequestFlow = _inAppReviewRequestFlow.receiveAsFlow()

    private val ignoreTonConnectTransaction = mutableListOf<String>()

    val installId: String
        get() = settingsRepository.installId

    val lockscreenFlow = combine(
        passcodeManager.lockscreenFlow,
        accountRepository.selectedStateFlow.filter { it !is AccountRepository.SelectedState.Initialization }.take(1)
    ) { lockscreen, state ->
        // "PIN exists but no wallets" cleanup. Must check BOTH repos on their raw rows: reset()
        // destroys the vault master key, so running it with MC wallets still in the DB would make
        // their mnemonics undecryptable forever. Any read failure counts as "wallets exist" —
        // never reset on an error.
        if ((lockscreen is LockScreen.State.Input || lockscreen is LockScreen.State.Biometric) &&
            state !is AccountRepository.SelectedState.Wallet &&
            !hasAnyWallet()
        ) {
            passcodeManager.reset()
            LockScreen.State.None
        } else {
            lockscreen
        }
    }

    init {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                environment.setCountryFromStore(billingManager.getCountry())
            } catch (_: Throwable) {
                L.d("RootViewModel", "Failed to get country from billing manager")
            }
            api.setCountryConfig(
                config = CountryConfig(
                    deviceCountry = environment.deviceCountry,
                    storeCountry = environment.storeCountry,
                    simCountry = environment.simCountry,
                    isVpn = environment.vpnActive,
                    timezone = environment.timezone,
                ),
            )
            api.initConfig()
        }

        pushManager.clearNotifications()

        mcWalletRealtimeProvider?.balanceHints?.collectFlow {
            mcAccountRepository?.refresh()
        }

        settingsRepository.languageFlow.collectFlow {
            context.setLocales(settingsRepository.localeList)
            App.instance.updateThemes()
        }

        accountRepository.selectedStateFlow
            .filter {
                it !is AccountRepository.SelectedState.Initialization
            }
            .onEach { state ->
                if (state is AccountRepository.SelectedState.Empty) {
                    _hasWalletFlow.tryEmit(false)
                    ShortcutManagerCompat.removeAllDynamicShortcuts(context)
                } else if (state is AccountRepository.SelectedState.Wallet) {
                    _hasWalletFlow.tryEmit(true)
                }
            }
            .flowOn(Dispatchers.IO)
            .launchIn(viewModelScope)

        viewModelScope.launch(Dispatchers.IO) {
            unifiedAccountRepository.selectedTonWalletFlow
                .filterNotNull()
                .filter { it.type == WalletType.Multichain }
                .distinctUntilChangedBy { it.id }
                .collectLatest { wallet ->
                    raffleStoryAutoShow?.prefetch(wallet.id)
                    awaitLockscreenHidden()
                    raffleStoryAutoShow?.showOnce(wallet.id) { raffleId ->
                        showStory(raffleId, "auto")
                    }
                }
        }

        viewModelScope.launch(Dispatchers.IO) {
            combine(
                unifiedAccountRepository.selectedTonWalletFlow,
                api.configFlow.filter { !it.empty },
            ) { wallet, config -> wallet to config.stories }
                .distinctUntilChangedBy { (wallet, stories) -> wallet?.id to stories }
                .collectLatest { (wallet, _) ->
                    if (wallet != null) {
                        awaitLockscreenHidden()
                        configStoriesAutoShow.showOnce(wallet) { stories ->
                            openScreen(RemoteStoriesScreen.newInstance(stories, "auto"))
                            true
                        }
                    }
                }
        }

        wcRepository.request
            .onEach { result ->
                L.d("WcResult: $result")
                when (result) {
                    is WcRequestResult.Connecting -> toast(RStr.connecting)
                    is WcRequestResult.Proposal -> openScreen(WcSessionFragment.newInstance(result.requestId))
                    is WcRequestResult.Request -> openScreen(WcConfirmFragment.newInstance(result.requestId))
                    is WcRequestResult.Error -> { toast(result.cause.bestMessage) }
                    is WcRequestResult.NoInternet -> toast(RStr.no_internet_connection)
                    is WcRequestResult.Executed -> {
                        val type = when (result.type) {
                            WcRequestResult.Executed.Type.Session -> RStr.session
                            WcRequestResult.Executed.Type.Request -> RStr.request
                        }

                        val outcome = when (result.isApproved) {
                            true -> RStr.approved
                            false -> RStr.rejected
                        }

                        toast("${context.getString(type)} ${context.getString(outcome)}")

                        if (result.connection.isDeeplink) {
                            context.navigationDelegate?.navigateTaskBack()
                        }
                    }
                }
            }
            .launchIn(viewModelScope)

        viewModelScope.launch(Dispatchers.IO) {
            val firebaseToken = FirebasePush.requestToken()
            settingsRepository.firebaseToken = firebaseToken
            ratesRepository.updateAll(TonNetwork.MAINNET, settingsRepository.currency)
            if (firebaseToken.isNullOrBlank()) {
                L.e("TonkeeperFirebasePush", "Failed to get Firebase push token")
            } else {
                L.d("TonkeeperFirebasePush", "Firebase push token: $firebaseToken")
            }
        }

        selectedWalletFlow.collectFlow { wallet ->
            applyAnalyticsKeys(wallet)
            initShortcuts(wallet)
        }

        api.configFlow
            .filter { !it.empty }
            .take(1)
            .collectFlow { config ->
                val config = AnalyticsHelper.Config(
                    aptabaseAppKey = config.aptabaseAppKey,
                    aptabaseEndpoint = config.aptabaseEndpoint,
                    installId = settingsRepository.installId,
                    deviceId = secureDeviceRepository.registerIfNeeded(),
                    storeCountryCode = environment.storeCountry,
                    deviceCountryCode = environment.deviceCountry,
                )

                analyticsHelper.setConfig(context, config)
                sendFirstLaunchEvent()
            }

        val initialWalletAndConfigFlow = combine(
            selectedWalletFlow.take(1),
            api.configFlow.filter { !it.empty }
        ) { _, config ->
            config
        }.take(1)

        initialWalletAndConfigFlow.collectFlow { config ->
            _eventFlow.tryEmit(RootEvent.CheckGooglePlayUpdate)
        }

        apkManager.statusFlow.filter {
            it is APKManager.Status.UpdateAvailable
        }.collectFlow {
            delay(1000)
            showUpdateAvailable(it as APKManager.Status.UpdateAvailable)
        }

        InAppReviewManager.requestFlow.collectFlow {
            _inAppReviewRequestFlow.trySend(Unit)
        }

        bgScope.launch {
            accountRepository.selectedStateFlow.filter {
                it !is AccountRepository.SelectedState.Initialization
            }.firstOrNull()
            resolveHadWalletsOnMultichainRelease()
            resolvePubkeysOnStart()
        }
    }

    private suspend fun hasAnyWallet(): Boolean {
        return runCatching { unifiedAccountRepository.hasAnyWallet() }.getOrDefault(true)
    }

    private suspend fun resolveHadWalletsOnMultichainRelease() = withContext(Dispatchers.IO) {
        try {
            settingsRepository.resolveHadWalletsOnMultichainRelease(
                unifiedAccountRepository.getWalletsCount() > 0
            )
        } catch (e: Throwable) {
            L.e(e)
        }
    }

    private suspend fun resolvePubkeysOnStart() = withContext(Dispatchers.IO) {
        try {
            val publicKeys = accountRepository.getWallets()
                .filter { it.network.isMainnet && !it.isWatchOnly }
                .map { it.publicKey }
                .distinctBy { it.hex() }

            if (publicKeys.isNotEmpty()) {
                api.resolvePublicKeysBulk(publicKeys, TonNetwork.MAINNET, installId)
            }
        } catch (e: Throwable) {
            L.e(e)
        }
    }

    override fun attachHolder(holder: Holder) {
        super.attachHolder(holder)
        observeTonConnectTransaction()
        observeTonConnectSignData()
        observeWalletKitEvents()
    }

    private suspend fun sendFirstLaunchEvent() = withContext(Dispatchers.IO) {
        if (DevSettings.firstLaunchDate <= 0) {
            val referrer = referrerClientHelper.getInstallReferrer()
            val deeplink = DevSettings.firstLaunchDeeplink.ifBlank { null }
            val installerStore = AppInfo.getInstallerPackageName(context)
            AnalyticsHelper.Default.events.installApp.installApp(referrer, deeplink, installerStore)
            DevSettings.isWebviewFolderMigrated = true
            DevSettings.firstLaunchDate = currentTimeSeconds()
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                try {
                    val wallet = accountRepository.getSelectedWallet()
                    if (wallet != null) {
                        val dataDir = DevSettings.webViewDataDir
                        if (dataDir == null && !DevSettings.isWebviewFolderMigrated) {
                            DevSettings.webViewDataDir = "wallet_${wallet.id.replace("-", "")}"
                            DevSettings.isWebviewFolderMigrated = true
                        }
                        DevSettings.webViewDataDir?.let { runCatching { WebView.setDataDirectorySuffix(it) } }
                    }
                } catch (e: Throwable) {
                    L.e(e)
                }
            }
        }
    }

    private fun observeTonConnectTransaction() {
        tonConnectBridge.transactionRequestFlow.map { (connection, message) ->
            val tx = RootSignTransaction(connection, message, savedState.returnUri)
            savedState.returnUri = null
            tx
        }.filter {
            !ignoreTonConnectTransaction.contains(it.hash)
        }.collectFlow {
            _eventFlow.tryEmit(RootEvent.CloseCurrentTonConnect)
            viewModelScope.launch {
                ignoreTonConnectTransaction.add(it.hash)
                signTransaction(it)
            }
        }
    }

    private fun observeTonConnectSignData() {
        tonConnectBridge.signDataRequestFlow.collectFlow { event ->
            val wallet = accountRepository.getWalletByAccountId(event.connection.accountId) ?: return@collectFlow
            val params = event.message.params.firstOrNull() ?: return@collectFlow
            val payload = SignDataRequestPayload.parse(params) ?: return@collectFlow
            signData(wallet, event.connection, payload, event.message.id)
        }
    }

    private fun observeWalletKitEvents() {
        tonConnectBridge.walletKitEventFlow.collectFlow { event ->
            when (event) {
                is WalletKitEvent.SendTransactionRequest -> {
                    _eventFlow.tryEmit(RootEvent.CloseCurrentTonConnect)
                    signWalletKitTransaction(event)
                }
                is WalletKitEvent.SignDataRequest -> {
                    signWalletKitData(event)
                }
                is WalletKitEvent.ConnectionRequest -> {
                    connectionWalletKit(event)
                }
            }
        }
    }

    private suspend fun connectionWalletKit(event: WalletKitEvent.ConnectionRequest) {
        val connectionRequest = event.request
        val manifestErrorCode = connectionRequest.event.preview.manifestFetchErrorCode
        if (manifestErrorCode != null) {
            FirebaseHelper.manifestFetchFailed(
                kind = "walletkit_code_$manifestErrorCode",
                url = connectionRequest.event.dAppInfo?.manifestUrl,
            )
            toast(Localization.dapp_manifest_error)
            connectionRequest.reject("Manifest fetch failed", manifestErrorCode)
            return
        }
        val isInjected = connectionRequest.event.isJsBridge == true
        val wallet = if (isInjected) {
            connectionRequest.event.walletId
                ?.let { unifiedAccountRepository.getTonWalletById(it) }
                ?: unifiedAccountRepository.getSelectedWallet()
        } else {
            null
        }
        _eventFlow.tryEmit(RootEvent.ShowTonConnect(connectionRequest, wallet, event.fromPackageName))
    }

    private suspend fun signWalletKitTransaction(event: WalletKitEvent.SendTransactionRequest) {
        val dAppUrl: Uri? = event.request.event.dAppInfo?.url?.let {
            Uri.parse(it)
        }
        val returnUri = savedState.returnUri
        savedState.returnUri = null

        if (dAppUrl == null) {
            DevSettings.tonConnectLog("Skipping transaction event (local) with no dAppUrl", error = false)
            return
        }

        val wallet = event.wallet
        val sendNativeFrom = if (event.request.event.isJsBridge == true) {
            Events.SendNative.SendNativeFrom.TonconnectLocal
        } else {
            Events.SendNative.SendNativeFrom.TonconnectRemote
        }
        try {
            WalletKitRequestHandler.handleTransactionRequest(
                request = event.request
            ) { signRequestJson ->
                SendTransactionScreen.run(
                    context, wallet,
                    SignRequestEntity.parse(signRequestJson, dAppUrl)
                        ?: throw BridgeException(message = "Failed to parse message params"),
                    sendNativeFrom = sendNativeFrom,
                )
            }
            DevSettings.tonConnectLog("WalletKit transaction approved", error = false)
        } catch (e: Throwable) {
            DevSettings.tonConnectLog("Error signing WalletKit transaction: ${e.bestMessage}", error = true)
            if (e is CancellationException) {
                tonConnectBridge.showLogoutAppBar(wallet, context, dAppUrl)
            }
        } finally {
            returnUri?.let {
                context.safeExternalOpenUri(it)
            }
        }
    }

    private suspend fun signWalletKitData(event: WalletKitEvent.SignDataRequest) {
        val dAppUrl: Uri? = event.request.event.dAppInfo?.url?.let {
            Uri.parse(it)
        }

        if (dAppUrl == null) {
            DevSettings.tonConnectLog("Skipping sign data event (local) with no dAppUrl", error = false)
            return
        }

        val wallet = event.wallet
        try {
            WalletKitRequestHandler.handleSignDataRequest(event.request) { payloadJson ->
                val payload = SignDataRequestPayload.parse(payloadJson)
                    ?: throw IllegalArgumentException("Failed to parse sign data payload")
                val proof = SignDataScreen.run(context, wallet, dAppUrl, payload)
                WalletKitRequestHandler.SignDataProof(
                    signatureBytes = proof.signature.decodeBase64(),
                    timestamp = proof.timestamp,
                    domainValue = proof.domain.value,
                )
            }
            DevSettings.tonConnectLog("WalletKit sign data approved", error = false)
        } catch (e: Throwable) {
            DevSettings.tonConnectLog("Error signing WalletKit data: ${e.bestMessage}", error = true)
            if (e is CancellationException) {
                tonConnectBridge.showLogoutAppBar(wallet, context, dAppUrl)
            }
        }
    }

    fun approveConnectionRequest(
        request: TONWalletConnectionRequest,
        wallet: WalletEntity,
        proof: TONProof.Result?,
        notifications: Boolean,
        appUrl: android.net.Uri,
    ) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                tonConnectBridge.approveConnectionRequest(request, wallet, proof, notifications)
                DAppPushToggleWorker.run(
                    context = context,
                    wallet = wallet,
                    appUrl = appUrl,
                    enable = notifications,
                )
            } catch (e: Exception) {
                L.e("Failed to approve connection", e)
            }
        }
    }

    fun rejectConnectionRequest(
        request: TONWalletConnectionRequest,
        reason: String?,
    ) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                tonConnectBridge.rejectConnectionRequest(request, reason)
            } catch (e: Exception) {
                L.e("Failed to reject connection", e)
            }
        }
    }

    private fun showUpdateAvailable(status: APKManager.Status.UpdateAvailable) {
        try {
            UpdateAvailableDialog(context, apkManager).show {
                apkManager.download(status.apk)
            }
        } catch (e: Throwable) {
            FirebaseCrashlytics.getInstance().recordException(e)
        }
    }

    // The lockscreen is an overlay view inside the activity, so a story opened under it would run
    // its timers and burn through frames while the passcode is being typed.
    private suspend fun awaitLockscreenHidden() {
        passcodeManager.lockscreenHiddenFlow.first { it }
    }

    private suspend fun showStory(id: String, from: String): Boolean = withContext(Dispatchers.IO) {
        val mcWalletId = unifiedAccountRepository.getSelectedWallet()
            ?.takeIf { it.type == WalletType.Multichain }
            ?.id
        val raffle = mcWalletId?.let { raffleRepository?.getRaffle(it, raffleId = id, forced = false) }
            ?.takeIf { it.ownsStoryId(id) }
        val stories = when {
            raffle != null && mcWalletId != null -> raffle.toStories(id)?.withRaffleSourceWalletId(mcWalletId)
            else -> api.getStories(id, mcWalletId, !settingsRepository.hadWalletsOnMultichainRelease)
        } ?: return@withContext false
        openScreen(RemoteStoriesScreen.newInstance(stories, from))
        true
    }

    fun connectTonConnectBridge() {
        tonConnectBridge.connectBridge()
    }

    fun disconnectTonConnectBridge() {
        tonConnectBridge.disconnectBridge()
    }

    fun canShowGooglePlayUpdatePrompt(): Boolean {
        val lastShownAt = settingsRepository.prefs.getLong(UPDATE_PROMPT_TIMESTAMP_KEY, 0L)
        return System.currentTimeMillis() - lastShownAt >= GOOGLE_PLAY_PROMPT_COOLDOWN_MS
    }

    fun onGooglePlayUpdatePromptShown() {
        settingsRepository.prefs.edit {
            putLong(UPDATE_PROMPT_TIMESTAMP_KEY, System.currentTimeMillis())
        }
    }

    fun canShowGooglePlayDownloadedPrompt(): Boolean {
        val lastShownAt = settingsRepository.prefs.getLong(DOWNLOADED_PROMPT_TIMESTAMP_KEY, 0L)
        return System.currentTimeMillis() - lastShownAt >= GOOGLE_PLAY_PROMPT_COOLDOWN_MS
    }

    fun onGooglePlayDownloadedPromptShown() {
        settingsRepository.prefs.edit {
            putLong(DOWNLOADED_PROMPT_TIMESTAMP_KEY, System.currentTimeMillis())
        }
    }

    private suspend fun signTransaction(tx: RootSignTransaction) {
        val eventId = tx.id
        try {
            val signRequests = tx.params.mapNotNull { SignRequestEntity.parse(it, tx.connection.appUrl) }
            if (signRequests.isEmpty()) {
                throw IllegalArgumentException("Empty sign requests")
            }
            for (signRequest in signRequests) {
                signRequest(eventId, tx.connection, signRequest)
            }
        } catch (e: Throwable) {
            FirebaseCrashlytics.getInstance().recordException(e)
            tonConnectBridge.sendBridgeError(tx.connection, BridgeError.unknown(e.bestMessage), eventId)
        }

        tx.returnUri?.let {
            context.safeExternalOpenUri(it)
        }
    }

    private suspend fun signRequest(
        eventId: Long,
        connection: AppConnectEntity,
        signRequest: SignRequestEntity
    ) {
        if (signRequest.from != null && !signRequest.from!!.toAccountId()
                .equalsAddress(connection.accountId)
        ) {
            DevSettings.tonConnectLog(
                "Invalid \"from\" address.\nReceived: ${signRequest.from?.toAccountId()}\nExpected: ${connection.accountId}",
                error = true
            )
            tonConnectBridge.sendBridgeError(
                connection,
                BridgeError.badRequest("Invalid \"from\" address. Specified wallet address not connected to this app."),
                eventId
            )
            return
        }

        val now = currentTimeSeconds()
        val validUntil = signRequest.validUntil.let { parsedExp ->
            if (0 >= parsedExp) {
                now + DeepLinkRoute.Transfer.MAX_EXP
            } else {
                val maxExp = now + DeepLinkRoute.Transfer.MAX_EXP
                minOf(parsedExp, maxExp)
            }
        }

        val isExpired = run {
            val fixedExp = abs(validUntil - 15L)
            now >= fixedExp
        }

        if (isExpired) {
            tonConnectBridge.sendBridgeError(
                connection,
                BridgeError.badRequest("Transaction has expired"),
                eventId
            )
            return
        }

        val wallets = accountRepository.getWalletsByAccountId(
            accountId = connection.accountId,
            network = connection.network
        ).filter {
            it.isTonConnectSupported
        }
        if (wallets.isEmpty()) {
            tonConnectBridge.sendBridgeError(connection, BridgeError.unknown(""), eventId)
            return
        }
        val wallet = wallets.find { it.hasPrivateKey } ?: wallets.first()
        try {
            val boc = SendTransactionScreen.run(
                context, wallet, signRequest,
                sendNativeFrom = Events.SendNative.SendNativeFrom.TonconnectRemote
            )
            tonConnectBridge.sendTransactionResponseSuccess(connection, boc, eventId)
        } catch (e: Throwable) {
            DevSettings.tonConnectLog(
                "Error while signing transaction: ${e.bestMessage}",
                error = true
            )
            if (e is CancellationException) {
                tonConnectBridge.showLogoutAppBar(wallet, context, connection.appUrl)
                tonConnectBridge.sendBridgeError(
                    connection,
                    BridgeError.userDeclinedTransaction(),
                    eventId
                )
            } else {
                tonConnectBridge.sendBridgeError(connection, BridgeError.unknown(e.bestMessage), eventId)
            }
        }
    }

    private suspend fun initShortcuts(
        currentWallet: WalletEntity
    ) = withContext(Dispatchers.IO) {
        appShortcutRepository?.migrateLegacyPinnedShortcuts()
        val wallets = accountRepository.getWallets()
        val list = mutableListOf<ShortcutInfoCompat>()
        if (!currentWallet.testnet) {
            ShortcutHelper.shortcutAction(
                context,
                Localization.send,
                R.drawable.ic_send_shortcut,
                "tonkeeper://send"
            )?.let {
                list.add(it)
            }
        }
        list.addAll(walletShortcutsFromWallet(currentWallet, wallets))
        if (ShortcutManagerCompat.isRequestPinShortcutSupported(context)) {
            ShortcutManagerCompat.setDynamicShortcuts(context, list.take(3))
        }
    }

    private suspend fun walletShortcutsFromWallet(
        currentWallet: WalletEntity,
        wallets: List<WalletEntity>
    ): List<ShortcutInfoCompat> {
        val list = mutableListOf<ShortcutInfoCompat>()
        if (1 >= wallets.size) {
            return list
        }
        for (wallet in wallets) {
            if (wallet == currentWallet || wallet.label.name.isBlank()) {
                continue
            }
            ShortcutHelper.shortcutWallet(context, wallet)?.let {
                list.add(it)
            }
        }
        return list
    }

    private fun applyAnalyticsKeys(wallet: WalletEntity) {
        val crashlytics = Firebase.crashlytics
        crashlytics.setUserId(wallet.accountId)
        crashlytics.setCustomKeys {
            key("testnet", wallet.testnet)
            key("walletType", wallet.type.name)
            key("installId", settingsRepository.installId)
        }
    }

    fun signOut() {
        viewModelScope.launch {
            unifiedAccountRepository.deleteAllWallets()
            passcodeManager.reset()
        }
    }

    fun connectLedger(connectData: LedgerConnectData, accounts: List<AccountItem>) {
        _eventFlow.tryEmit(RootEvent.Ledger(connectData, accounts))
    }

    fun openDApp(url: Uri, source: String) {
        selectedWalletFlow.take(1).collectFlow {
            _eventFlow.tryEmit(RootEvent.OpenDAppByShortcut(it, url, source))
        }
    }

    fun openLegacyShortcutDApp(url: String) {
        val repository = appShortcutRepository ?: return
        viewModelScope.launch(Dispatchers.IO) {
            if (repository.migrateAndResolveLegacyPin(url)) {
                url.toUriOrNull()?.let { openDApp(it, "push") }
            }
        }
    }

    fun openDappScreen(
        walletId: String, app: AppEntity, dAppUrl: Uri, source: String
    ) {
        viewModelScope.launch { // TODO Refactor
            val wallet = unifiedAccountRepository.getTonWalletById(walletId) ?: return@launch

            navigation?.add(
                DAppScreen.newInstance(
                    wallet = wallet,
                    title = app.name,
                    url = dAppUrl,
                    iconUrl = app.iconUrl,
                    source = source,
                )
            )
        }
    }

    fun processIntentExtras(bundle: Bundle): Boolean {
        val pushType = bundle.getString("type") ?: return false
        val pushId = bundle.getStringValue("push_id", "utm_id", "utm_campaign")
        hasWalletFlow.take(1).collectFlow {
            if (pushType == "console_dapp_notification") {
                processDAppPush(bundle)
            } else {
                val deeplink = bundle.getString("deeplink")?.toUriOrNull() ?: return@collectFlow
                analyticsHelper.events.pushClick.pushClick(
                    pushId = pushId ?: pushType,
                    deepLink = removePrivateDataFromUrl(deeplink.toString()),
                )
                processDeepLinkPush(deeplink, bundle)
            }
        }
        return true
    }

    private suspend fun processDAppPush(bundle: Bundle) {
        val accountId = bundle.getString("account") ?: return
        val wallet = accountRepository.getWalletByAccountId(accountId) ?: return
        val openUrl = bundle.getString("link")?.toUriOrNull() ?: bundle.getString("dapp_url")?.toUriOrNull()
        if (openUrl == null) {
            return
        }
        val app = dAppsRepository.getAppFixIcon(openUrl, wallet, browserRepository, settingsRepository)
        openScreen(
            DAppScreen.newInstance(
                wallet = wallet,
                title = openUrl.host ?: "unknown",
                url = openUrl,
                iconUrl = app.iconUrl,
                source = "push"
            )
        )
    }

    private suspend fun processDeepLinkPush(uri: Uri, bundle: Bundle) {
        val wallet = deeplinkResolveWallet(bundle) ?: return
        if (accountRepository.getSelectedWallet()?.id != wallet.id) {
            accountRepository.setSelectedWallet(wallet.id)
        }
        val deeplink = DeepLink(uri, false, null)
        processDeepLink(wallet, deeplink, null)
    }

    private suspend fun deeplinkResolveWallet(bundle: Bundle): WalletEntity? {
        try {
            val accountId = bundle.getString("account") ?: throw IllegalArgumentException("Key 'account' not found")
            return accountRepository.getWalletByAccountId(accountId) ?: throw IllegalArgumentException("Wallet not found")
        } catch (e: Throwable) {
            return selectedWalletFlow.firstOrNull()
        }
    }

    fun processDeepLink(
        uri: Uri,
        fromQR: Boolean,
        refSource: Uri?,
        internal: Boolean,
        fromPackageName: String?,
        fromExternal: Boolean = false,
        fromBrowser: Boolean = false,
    ): Boolean {
        savedState.returnUri = null
        val deeplink = DeepLink(uri, fromQR, refSource, fromExternal)
        if (deeplink.route is DeepLinkRoute.Unknown) {
            viewModelScope.launch { showInvalidLinkToast(deeplink.route) }
            return false
        }
        if (deeplink.route is DeepLinkRoute.Internal && !internal) {
            return true
        }
        // A scanned crypto address (send?address=…) is resolved against the multichain-aware
        // repository, not the legacy selectedStateFlow which never emits for a multichain wallet.
        val sendAddress = (deeplink.route as? DeepLinkRoute.Send)?.address
        if (sendAddress != null) {
            viewModelScope.launch { openSendWithAddress(sendAddress, deeplink) }
            return true
        }
        unifiedAccountRepository.selectedTonWalletFlow
            .take(1)
            .onEach { wallet ->
                val route = deeplink.route
                if (route is DeepLinkRoute.Signer) {
                    processSignerDeepLink(route, fromQR)
                } else if (wallet != null) {
                    processDeepLink(wallet, deeplink, fromPackageName, fromBrowser)
                }
            }
            .launch()
        return true
    }

    private suspend fun openSendWithAddress(address: String, deeplink: DeepLink) {
        val wallet = unifiedAccountRepository.getSelectedWallet() ?: return
        if (wallet.isWatchOnly) {
            return
        }
        if (wallet.type == WalletType.Multichain) {
            openScreen(
                WithdrawMulticoinFragment.create(
                    initial = WithdrawMulticoinRoutes.Picker(presetAddress = address),
                    analyticsFrom = Events.WithdrawFlow.WithdrawFlowFrom.DeepLink,
                )
            )
        } else {
            openScreen(
                SendScreen.newInstance(
                    wallet,
                    targetAddress = address,
                    type = SendScreen.Companion.Type.Default,
                    from = deeplink.source.analytic,
                )
            )
        }
    }

    fun processTonConnectDeepLink(deeplink: DeepLink, fromPackageName: String?) {
        val route = deeplink.route as DeepLinkRoute.TonConnect

        savedState.returnUri = tonConnectBridge.processDeeplink(
            context = context,
            uri = route.uri,
            fromQR = deeplink.fromQR,
            refSource = deeplink.referrer,
            fromPackageName = fromPackageName
        )
    }

    private suspend fun processDeepLink(
        wallet: WalletEntity,
        deeplink: DeepLink,
        fromPackageName: String?,
        fromBrowser: Boolean = false
    ) {
        val route = deeplink.route
        L.d("processDeepLink route: $route")
        if (route is DeepLinkRoute.DnsRenew) {
            openScreen(DNSRenewScreen.newInstance(wallet, emptyList()))
        } else if (route is DeepLinkRoute.TonConnect) {
            if (!wallet.isTonConnectSupported && unifiedAccountRepository.getTonWallets().count { it.isTonConnectSupported } == 0) {
                openScreen(InitScreen.newInstance(InitArgs.Type.AddWallet))
                return
            }
            processTonConnectDeepLink(deeplink, fromPackageName)
        } else if (route is DeepLinkRoute.WalletConnect) {
            val selected = unifiedAccountRepository.getSelectedWallet()
            if (selected?.type != WalletType.Multichain) {
                toast(Localization.wc_error_multichain_only)
                return
            }

            val source = when {
                deeplink.fromQR -> WcConnection.Qr
                fromBrowser -> WcConnection.DApp
                else -> WcConnection.Deeplink
            }
            wcRepository.pair(route.uri.toString(), source)
        } else if (route is DeepLinkRoute.Story) {
            showStory(route.id, "deep-link")
        } else if (route is DeepLinkRoute.Raffle) {
            // Raffles exist only for multichain wallets; ignore the route for legacy ones.
            // The raw selected wallet is a legacy stub, so resolve the real type first.
            val selected = unifiedAccountRepository.getSelectedWallet()
            if (selected?.type == WalletType.Multichain && WalletFeature.Raffles.isEnabled) {
                openScreen(RaffleFragment.newInstance(selected.id, route.id, route.source))
            }
        } else if (route is DeepLinkRoute.Migrate) {
            if (WalletFeature.Migration.isEnabled) {
                openScreen(MigrationFragment.newInstance(MigrationAnalytics.from(route.from)))
            } else {
                toast(Localization.migration_coming_soon)
            }
        } else if (route is DeepLinkRoute.AddWallet) {
            openScreen(InitScreen.newInstance(InitArgs.Type.AddWallet, raffleSourceWalletId = route.raffleSourceWalletId))
        } else if (route is DeepLinkRoute.Tabs.Activity) {
            val selected = unifiedAccountRepository.getSelectedWallet() ?: wallet
            if (selected.type == WalletType.Multichain) {
                openScreen(EventsFragment())
            } else {
                openScreen(TxEventsScreen.newInstance(wallet))
            }
        } else if (route is DeepLinkRoute.Tabs.Collectibles) {
            openScreen(CollectiblesScreen.newInstance(from = route.from))
        } else if (route is DeepLinkRoute.Tabs) {
            if (route is DeepLinkRoute.Tabs.Trading) {
                TradeEntryTracker.markDeepLink()
            }
            _eventFlow.tryEmit(RootEvent.OpenTab(route.tabUri.toUri(), wallet, route.from))
        } else if (route is DeepLinkRoute.Send && !wallet.isWatchOnly) {
            openScreen(
                SendScreen.newInstance(
                    wallet,
                    type = SendScreen.Companion.Type.Default,
                    from = deeplink.source.analytic,
                )
            )
        } else if (route is DeepLinkRoute.Staking && !wallet.isWatchOnly) {
            val tonWallet = unifiedAccountRepository.getSelectedWallet() ?: wallet
            openScreen(StakingScreen.newInstance(tonWallet, from = "deeplink"))
        } else if (route is DeepLinkRoute.StakingPool) {
            val tonWallet = unifiedAccountRepository.getSelectedWallet() ?: wallet
            openScreen(StakeViewerScreen.newInstance(tonWallet, address = route.poolAddress, name = ""))
        } else if (route is DeepLinkRoute.AccountEvent) {
            val address = route.address
            if (address == null) {
                showTransaction(route.eventId)
            } else {
                showTransaction(address, route.eventId)
            }
        } else if (route is DeepLinkRoute.Transfer && !wallet.isWatchOnly) {
            processTransferDeepLink(wallet, deeplink, route)
        } else if (route is DeepLinkRoute.EvmTransfer && !wallet.isWatchOnly) {
            openEvmTransfer(route)
        } else if (route is DeepLinkRoute.PickWallet) {
            accountRepository.setSelectedWallet(route.walletId)
        } else if (route is DeepLinkRoute.Swap && !api.getConfig(wallet.network).flags.disableSwap) {
            val selected = unifiedAccountRepository.getSelectedWallet()
            if (selected?.type == WalletType.Multichain) {
                openScreen(
                    SwapFragment.create(
                        SwapRoutes.Swap(
                            sellAssetId = route.fromAssetId,
                            buyAssetId = route.toAssetId,
                        )
                    )
                )
            } else {
                // Asset-id deeplinks are multichain-only: the legacy web swap can't open
                // cross-chain pairs or most of the networks, so they are deliberately not
                // mapped back to symbols — the screen opens with defaults as a fail-safe.
                val isMultichainDeepLink = route.fromAssetId != null || route.toAssetId != null
                _eventFlow.tryEmit(
                    RootEvent.Swap(
                        wallet = wallet,
                        uri = api.getConfig(wallet.network).swapUri,
                        address = wallet.address,
                        from = route.from.takeUnless { isMultichainDeepLink } ?: "TON",
                        to = route.to.takeUnless { isMultichainDeepLink },
                    )
                )
            }
        } else if (route is DeepLinkRoute.Battery && !wallet.isWatchOnly) {
            openBattery(wallet, route)
        } else if (route is DeepLinkRoute.Purchase && !wallet.isWatchOnly) {
            if (unifiedAccountRepository.getSelectedWallet()?.type == WalletType.Multichain) {
                openScreen(DepositMulticoinFragment.create(analyticsFrom = DepositFlowFrom.DeepLink))
            } else {
                openScreen(DepositFragment())
            }
        } else if (route is DeepLinkRoute.Deposit && !wallet.isWatchOnly) {
            if (unifiedAccountRepository.getSelectedWallet()?.type == WalletType.Multichain) {
                openScreen(DepositMulticoinFragment.create(analyticsFrom = DepositFlowFrom.DeepLink))
            } else {
                val hasParams = route.fromToken != null || route.toToken != null || route.cashMethod != null
                if (hasParams) {
                    openScreen(DepositFragment.create(
                        DepositRoutes.Buy(
                            ft = route.fromToken,
                            tn = route.toNetwork,
                            tt = route.toToken,
                            fn = route.fromNetwork,
                            cm = route.cashMethod,
                        )
                    ))
                } else {
                    openScreen(DepositFragment())
                }
            }
        } else if (route is DeepLinkRoute.Withdraw && !wallet.isWatchOnly) {
            if (unifiedAccountRepository.getSelectedWallet()?.type == WalletType.Multichain) {
                openScreen(WithdrawMulticoinFragment.create(analyticsFrom = WithdrawFlowFrom.DeepLink))
            } else {
                val hasParams = route.fromToken != null || route.toToken != null || route.cashMethod != null
                if (hasParams) {
                    openScreen(WithdrawFragment.create(
                        WithdrawRoutes.WithdrawMethod(
                            ft = route.fromToken,
                            tn = route.toNetwork,
                            tt = route.toToken,
                            fn = route.fromNetwork,
                            cm = route.cashMethod,
                        )
                    ))
                } else {
                    openScreen(WithdrawFragment.create())
                }
            }
        } else if (route is DeepLinkRoute.Exchange && !wallet.isWatchOnly) {
            val method = purchaseRepository.getMethod(
                id = route.methodName,
                network = wallet.network,
                locale = settingsRepository.getLocale(),
                walletId = wallet.id,
            )
            if (method == null) {
                toast(Localization.payment_method_not_found)
            } else {
                BrowserHelper.openPurchase(
                    context, WalletPurchaseMethodEntity(
                        method = method,
                        wallet = wallet,
                        currency = api.getCurrencyCodeByCountry(settingsRepository),
                        config = api.getConfig(wallet.network)
                    )
                )
            }
        } else if (route is DeepLinkRoute.Backups && (wallet.hasPrivateKey || wallet.isMultichain)) {
            openScreen(BackupScreen.newInstance(WalletFlowSource.Settings))
        } else if (route is DeepLinkRoute.Settings) {
            openScreen(SettingsScreen.newInstance("deeplink"))
        } else if (route is DeepLinkRoute.DApp) {
            val dAppUri = route.url.toUriOrNull()
            if (dAppUri == null) {
                toast(Localization.invalid_link)
                return
            }

            val host = dAppUri.host
            if (host == null || !host.contains(".")) {
                toast(Localization.invalid_link)
                return
            }

            if (safeModeClient.isHasScamUris(dAppUri)) {
                openScreen(DAppSafeScreen.newInstance(wallet))
                return
            }

            val app = dAppsRepository.getAppFixIcon(dAppUri, wallet, browserRepository, settingsRepository)

            val isTrustedApp = browserRepository.isTrustedApp(
                country = settingsRepository.country,
                network = wallet.network,
                locale = settingsRepository.getLocale(),
                deeplink = dAppUri,
                walletId = wallet.multichainWalletId,
            )

            if (!isTrustedApp && settingsRepository.isDAppOpenConfirm(wallet.id, app.host)) {
                openScreen(DappConfirmFragment.newInstance(wallet.id, app, dAppUri))
            } else {
                openScreen(
                    DAppScreen.newInstance(
                        wallet = wallet,
                        title = app.name,
                        url = dAppUri,
                        iconUrl = app.iconUrl,
                        source = "deep-link",
                    )
                )
            }
        } else if (route is DeepLinkRoute.SettingsSecurity) {
            openScreen(SecurityScreen.newInstance(wallet))
        } else if (route is DeepLinkRoute.SettingsCurrency) {
            openScreen(CurrencyScreen.newInstance())
        } else if (route is DeepLinkRoute.SettingsLanguage) {
            openScreen(LanguageScreen.newInstance())
        } else if (route is DeepLinkRoute.SettingsExtensions) {
            openScreen(ExtensionsScreen.newInstance(wallet))
        } else if (route is DeepLinkRoute.SettingsNotifications) {
            openScreen(SettingsScreen.newInstance("deeplink"))
        } else if (route is DeepLinkRoute.EditWalletLabel) {
            openScreen(EditNameScreen.newInstance())
        } else if (route is DeepLinkRoute.Camera && !wallet.isWatchOnly) {
            openScreen(CameraScreen.newInstance())
        } else if (route is DeepLinkRoute.Receive) {
            val selected = unifiedAccountRepository.getSelectedWallet()
            if (selected?.type == WalletType.Multichain) {
                openScreen(
                    DepositMulticoinFragment.create(
                        initial = DepositMulticoinRoutes.Receive,
                        analyticsFrom = DepositFlowFrom.DeepLink,
                    )
                )
            } else {
                openScreen(QrAssetFragment.newInstance())
            }
        } else if (route is DeepLinkRoute.ManageAssets) {
            openScreen(TokensManageScreen.newInstance(wallet))
        } else if (route is DeepLinkRoute.WalletPicker) {
            openScreen(PickerScreen.newInstance())
        } else if (route is DeepLinkRoute.Jetton) {
            openTokenViewer(wallet, route)
        } else if (route is DeepLinkRoute.Asset) {
            openScreen(
                AssetsFragment.newInstance(
                    assetId = route.assetId,
                    from = Events.AssetScreen.AssetScreenFrom.DeepLink,
                )
            )
        } else {
            showInvalidLinkToast(deeplink.route)
        }
    }

    private suspend fun showInvalidLinkToast(route: DeepLinkRoute) {
        if (!(route is DeepLinkRoute.Unknown && (route.uri.hasRefer() || route.uri.hasUtmSource()))) {
            toast(Localization.invalid_link)
        }
    }

    fun installDownloadedAPK(token: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            if (!apkManager.installDownloaded(context, token)) {
                toast(Localization.failed)
            }
        }
    }

    private suspend fun openBattery(wallet: WalletEntity, route: DeepLinkRoute.Battery) {
        val promoCode = route.promocode
        if (promoCode.isNullOrEmpty()) {
            openScreen(BatteryScreen.newInstance(wallet, from = BatteryNativeFrom.Deeplink, jetton = route.jetton))
        } else {
            loading(true)
            val validCode = api.batteryVerifyPurchasePromo(wallet.network, promoCode)
            loading(false)
            if (validCode) {
                openScreen(
                    BatteryScreen.newInstance(
                        wallet,
                        promoCode,
                        BatteryNativeFrom.Deeplink,
                        jetton = route.jetton
                    )
                )
            } else {
                toast(Localization.wrong_promocode)
            }
        }
    }

    private suspend fun openTokenViewer(wallet: WalletEntity, route: DeepLinkRoute.Jetton) {
        val token =
            tokenRepository.getToken(wallet.accountId, wallet.network, route.address) ?: return
        openScreen(TokenScreen.newInstance(wallet, token.address, token.name, token.symbol))
    }

    fun processTransferDeepLink(deepLink: DeepLink, route: DeepLinkRoute.Transfer) {
        selectedWalletFlow.take(1).collectFlow {
            processTransferDeepLink(it, deepLink, route)
        }
    }

    private suspend fun processTransferDeepLink(
        wallet: WalletEntity,
        deepLink: DeepLink,
        route: DeepLinkRoute.Transfer
    ) {
        if (route.isExpired) {
            toast(Localization.expired_link)
            return
        }

        val selected = unifiedAccountRepository.getSelectedWallet() ?: wallet
        if (selected.type == WalletType.Multichain) {
            openMulticoinTransfer(selected, route)
            return
        }

        val jettonMaster = route.jettonMaster()
        val decimals = jettonMaster?.let {
            tokenRepository.getToken(selected.accountId, selected.network, it)
        }?.decimals ?: WalletCurrency.TON.decimals

        val amount = route.amount?.let {
            Coins.of(it, decimals)
        }

        _eventFlow.tryEmit(
            RootEvent.Transfer(
                wallet = selected,
                address = route.address,
                amount = amount,
                text = route.text,
                jettonAddress = jettonMaster,
                bin = route.bin,
                initStateBase64 = route.initStateBase64,
                validUnit = route.exp,
                source = deepLink.source,
            )
        )
    }

    private suspend fun openEvmTransfer(route: DeepLinkRoute.EvmTransfer) {
        val selected = unifiedAccountRepository.getSelectedWallet() ?: return
        if (selected.type != WalletType.Multichain) {
            toast(Localization.invalid_link)
            return
        }
        loading(true)
        val account = findEvmAccount(selected.id, route)
        loading(false)
        val initial = if (account != null) {
            WithdrawMulticoinRoutes.Send(
                assetId = account.asset.id,
                presetAddress = route.recipient,
                presetAmount = route.amount?.toString(),
            )
        } else {
            WithdrawMulticoinRoutes.Picker(presetAddress = route.recipient)
        }
        openScreen(
            WithdrawMulticoinFragment.create(
                initial = initial,
                analyticsFrom = Events.WithdrawFlow.WithdrawFlowFrom.DeepLink,
            )
        )
    }

    private suspend fun findEvmAccount(
        walletId: String,
        route: DeepLinkRoute.EvmTransfer
    ): AccountWithDetails? {
        val repository = mcAccountRepository ?: return null
        route.chain?.let { chain ->
            return repository.findAccountOrNull(walletId, route.assetId(chain))
        }
        if (route.contract == null) {
            return null
        }
        val matches = coroutineScope {
            Chain.all.filterIsInstance<Chain.Evm>().filter { it.network.mode == Network.Mode.Mainnet }.map { chain ->
                async { repository.findAccountOrNull(walletId, route.assetId(chain)) }
            }.awaitAll()
        }.filterNotNull()
        return matches.singleOrNull()
    }

    private suspend fun McAccountRepository.findAccountOrNull(
        walletId: String,
        assetId: String
    ): AccountWithDetails? {
        return try {
            findAccount(walletId, assetId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun openMulticoinTransfer(wallet: WalletEntity, route: DeepLinkRoute.Transfer) {
        val hasPayload = route.bin != null || route.initStateBase64 != null
        val account = if (hasPayload) {
            null
        } else {
            route.targetAssetId(wallet.testnet)?.let { assetId ->
                runCatching { mcAccountRepository?.findAccount(wallet.id, assetId) }.getOrNull()
            }
        }
        val initial = if (account != null) {
            WithdrawMulticoinRoutes.Send(
                assetId = account.asset.id,
                presetAddress = route.address,
                presetAmount = route.amount?.toString(),
                presetComment = route.text,
            )
        } else {
            WithdrawMulticoinRoutes.Picker(
                presetAddress = route.address,
                presetComment = route.text,
            )
        }

        openScreen(
            WithdrawMulticoinFragment.create(
                initial = initial,
                analyticsFrom = Events.WithdrawFlow.WithdrawFlowFrom.DeepLink,
            )
        )
    }

    fun processSignerDeepLink(route: DeepLinkRoute.Signer, fromQR: Boolean) {
        _eventFlow.tryEmit(
            RootEvent.Singer(
                publicKey = route.publicKey,
                name = route.name,
                qr = fromQR || !route.local
            )
        )
    }

    private suspend fun showTransaction(hash: String) {
        val wallet = selectedWalletFlow.firstOrNull() ?: return
        val tx = historyHelper.getEvent(
            wallet = wallet,
            eventId = hash,
            options = ActionOptions(
                safeMode = settingsRepository.isSafeModeEnabled(wallet.id, wallet.network),
            )
        ).filterIsInstance<HistoryItem.Event>().firstOrNull() ?: return
        openScreen(TransactionScreen.newInstance(tx))
    }

    private suspend fun showTransaction(accountId: String, hash: String) {
        val wallet = accountRepository.getWalletByAccountId(accountId) ?: return
        val event = api.getTransactionEvents(wallet.accountId, wallet.network, hash) ?: return
        val tx = historyHelper.mapping(
            wallet = wallet,
            event = event,
            options = ActionOptions(
                safeMode = settingsRepository.isSafeModeEnabled(wallet.id, wallet.network),
            )
        ).filterIsInstance<HistoryItem.Event>().firstOrNull() ?: return
        openScreen(TransactionScreen.newInstance(tx))
    }

    private suspend fun signData(
        wallet: WalletEntity,
        connection: AppConnectEntity,
        payload: SignDataRequestPayload,
        eventId: Long
    ) {
        try {
            val proof = SignDataScreen.run(context, wallet, connection.appUrl, payload)
            tonConnectBridge.sendSignDataResponseSuccess(connection, proof, wallet.address, payload, eventId)
        } catch (e: Throwable) {
            DevSettings.tonConnectLog("Error while signing data: ${e.bestMessage}", error = true)
            if (e is CancellationException) {
                tonConnectBridge.showLogoutAppBar(wallet, context, connection.appUrl)
                tonConnectBridge.sendBridgeError(connection, BridgeError.userDeclinedTransaction(), eventId)
            } else {
                tonConnectBridge.sendBridgeError(connection, BridgeError.unknown(e.bestMessage), eventId)
            }
        }
    }

    suspend fun isScamAddress(address: String, network: TonNetwork): Boolean {
        return api.resolveAccount(address, network)?.isScam ?: false
    }

    private companion object {
        private const val GOOGLE_PLAY_PROMPT_COOLDOWN_MS = 24L * 60L * 60L * 1000L
        private const val UPDATE_PROMPT_TIMESTAMP_KEY = "google_play_update_prompt_timestamp"
        private const val DOWNLOADED_PROMPT_TIMESTAMP_KEY = "google_play_update_downloaded_prompt_timestamp"
    }
}
