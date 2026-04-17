package com.tonapps.tonkeeper.ui.screen.root

import android.annotation.SuppressLint
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
import com.tonapps.blockchain.model.legacy.WalletCurrency
import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.blockchain.ton.TonNetwork
import com.tonapps.blockchain.ton.extensions.hex
import com.tonapps.blockchain.ton.extensions.equalsAddress
import com.tonapps.blockchain.ton.extensions.toAccountId
import com.tonapps.bus.generated.Events
import com.tonapps.core.deeplink.DeepLink
import com.tonapps.core.deeplink.DeepLinkRoute
import com.tonapps.core.flags.WalletFeature
import com.tonapps.dapp.warning.DAppConfirmFragment
import com.tonapps.deposit.DepositFragment
import com.tonapps.deposit.DepositRoutes
import com.tonapps.deposit.WithdrawFragment
import com.tonapps.deposit.WithdrawRoutes
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
import com.tonapps.tonkeeper.Environment
import com.tonapps.tonkeeper.api.getCurrencyCodeByCountry
import com.tonapps.tonkeeper.billing.BillingManager
import com.tonapps.tonkeeper.client.safemode.SafeModeClient
import com.tonapps.bus.core.AnalyticsHelper
import com.tonapps.tonkeeper.App
import com.tonapps.tonkeeper.core.DevSettings
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
import com.tonapps.tonkeeper.manager.tonconnect.TonConnectManager
import com.tonapps.tonkeeper.manager.tonconnect.bridge.model.BridgeError
import com.tonapps.tonkeeper.manager.tonconnect.bridge.model.SignDataRequestPayload
import com.tonapps.tonkeeper.os.AppInstall
import com.tonapps.tonkeeper.ui.base.BaseWalletVM
import com.tonapps.tonkeeper.ui.component.UpdateAvailableDialog
import com.tonapps.tonkeeper.ui.screen.add.AddWalletScreen
import com.tonapps.tonkeeper.ui.screen.backup.main.BackupScreen
import com.tonapps.tonkeeper.ui.screen.battery.BatteryScreen
import com.tonapps.tonkeeper.ui.screen.browser.dapp.DAppScreen
import com.tonapps.tonkeeper.ui.screen.browser.safe.DAppSafeScreen
import com.tonapps.tonkeeper.ui.screen.camera.CameraScreen
import com.tonapps.tonkeeper.ui.screen.dns.renew.DNSRenewScreen
import com.tonapps.tonkeeper.ui.screen.init.list.AccountItem
import com.tonapps.tonkeeper.ui.screen.name.edit.EditNameScreen
import com.tonapps.tonkeeper.ui.screen.onramp.main.OnRampScreen
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
import com.tonapps.tonkeeperx.R
import com.tonapps.wallet.api.API
import com.tonapps.wallet.api.configs.CountryConfig
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.browser.BrowserRepository
import com.tonapps.wallet.data.core.entity.SignRequestEntity
import com.tonapps.wallet.data.dapps.DAppsRepository
import com.tonapps.wallet.data.dapps.entities.AppConnectEntity
import com.tonapps.wallet.data.dapps.entities.AppEntity
import com.tonapps.wallet.data.passcode.LockScreen
import com.tonapps.wallet.data.passcode.PasscodeManager
import com.tonapps.wallet.data.purchase.PurchaseRepository
import com.tonapps.wallet.data.rates.RatesRepository
import com.tonapps.wallet.data.settings.SettingsRepository
import com.tonapps.wallet.data.token.TokenRepository
import com.tonapps.wallet.localization.Localization
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.CancellationException
import kotlin.math.abs

@SuppressLint("LargeClass")
class RootViewModel(
    app: Application,
    private val settingsRepository: SettingsRepository,
    private val accountRepository: AccountRepository,
    private val api: API,
    private val historyHelper: HistoryHelper,
    private val purchaseRepository: PurchaseRepository,
    private val tonConnectManager: TonConnectManager,
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
    savedStateHandle: SavedStateHandle,
): BaseWalletVM(app) {

    private val savedState = RootModelState(savedStateHandle)

    private val selectedWalletFlow: Flow<WalletEntity> = accountRepository.selectedWalletFlow

    private val _hasWalletFlow = MutableEffectFlow<Boolean?>()
    val hasWalletFlow = _hasWalletFlow.asSharedFlow().filterNotNull()

    private val _eventFlow = MutableEffectFlow<RootEvent?>()
    val eventFlow = _eventFlow.asSharedFlow().filterNotNull()

    private val ignoreTonConnectTransaction = mutableListOf<String>()

    val installId: String
        get() = settingsRepository.installId

    val lockscreenFlow = combine(
        passcodeManager.lockscreenFlow,
        accountRepository.selectedStateFlow.filter { it !is AccountRepository.SelectedState.Initialization }.take(1)
    ) { lockscreen, state ->
        if ((lockscreen is LockScreen.State.Input || lockscreen is LockScreen.State.Biometric) && state !is AccountRepository.SelectedState.Wallet) {
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
                    storeCountryCode = environment.storeCountry,
                    deviceCountryCode = environment.deviceCountry,
                )

                analyticsHelper.setConfig(context, config)
                sendFirstLaunchEvent()
            }

        val initialWalletAndConfigFlow = combine(
            accountRepository.selectedWalletFlow.take(1),
            api.configFlow.filter { !it.empty }
        ) { _, config ->
            config
        }.take(1)

        initialWalletAndConfigFlow.collectFlow { config ->
            if (config.stories.isNotEmpty()) {
                showStories(config.stories)
            }
            _eventFlow.tryEmit(RootEvent.CheckGooglePlayUpdate)
        }

        apkManager.statusFlow.filter {
            it is APKManager.Status.UpdateAvailable
        }.collectFlow {
            delay(1000)
            showUpdateAvailable(it as APKManager.Status.UpdateAvailable)
        }

        bgScope.launch {
            accountRepository.selectedStateFlow.filter {
                it !is AccountRepository.SelectedState.Initialization
            }.firstOrNull()
            resolvePubkeysOnStart()
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
    }

    private suspend fun sendFirstLaunchEvent() = withContext(Dispatchers.IO) {
        if (DevSettings.firstLaunchDate <= 0) {
            val referrer = referrerClientHelper.getInstallReferrer()
            val deeplink = DevSettings.firstLaunchDeeplink.ifBlank { null }
            val installerStore = AppInstall.getInstallerPackageName(context)
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
        tonConnectManager.transactionRequestFlow.map { (connection, message) ->
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
        tonConnectManager.signDataRequestFlow.collectFlow { event ->
            val wallet = accountRepository.getWalletByAccountId(event.connection.accountId) ?: return@collectFlow
            val params = event.message.params.firstOrNull() ?: return@collectFlow
            val payload = SignDataRequestPayload.parse(params) ?: return@collectFlow
            signData(wallet, event.connection, payload, event.message.id)
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

    private suspend fun showStories(storiesIds: List<String>) = withContext(Dispatchers.IO) {
        val firstStoryId = storiesIds.firstOrNull { !settingsRepository.isStoriesViewed(it) } ?: return@withContext
        showStory(firstStoryId, "wallet")
    }

    private suspend fun showStory(id: String, from: String) = withContext(Dispatchers.IO) {
        val stories = api.getStories(id) ?: return@withContext
        openScreen(RemoteStoriesScreen.newInstance(stories, from))
    }

    fun connectTonConnectBridge() {
        tonConnectManager.connectBridge()
    }

    fun disconnectTonConnectBridge() {
        tonConnectManager.disconnectBridge()
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
            val signRequests = tx.params.map { SignRequestEntity(it, tx.connection.appUrl) }
            if (signRequests.isEmpty()) {
                throw IllegalArgumentException("Empty sign requests")
            }
            for (signRequest in signRequests) {
                signRequest(eventId, tx.connection, signRequest)
            }
        } catch (e: Throwable) {
            FirebaseCrashlytics.getInstance().recordException(e)
            tonConnectManager.sendBridgeError(tx.connection, BridgeError.unknown(e.bestMessage), eventId)
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
            tonConnectManager.sendBridgeError(
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
            tonConnectManager.sendBridgeError(
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
            tonConnectManager.sendBridgeError(connection, BridgeError.unknown(""), eventId)
            return
        }
        val wallet = wallets.find { it.hasPrivateKey } ?: wallets.first()
        try {
            val boc = SendTransactionScreen.run(
                context, wallet, signRequest,
                sendNativeFrom = Events.SendNative.SendNativeFrom.TonconnectRemote
            )
            tonConnectManager.sendTransactionResponseSuccess(connection, boc, eventId)
        } catch (e: Throwable) {
            DevSettings.tonConnectLog(
                "Error while signing transaction: ${e.bestMessage}",
                error = true
            )
            if (e is CancellationException) {
                tonConnectManager.showLogoutAppBar(wallet, context, connection.appUrl)
                tonConnectManager.sendBridgeError(
                    connection,
                    BridgeError.userDeclinedTransaction(),
                    eventId
                )
            } else {
                tonConnectManager.sendBridgeError(connection, BridgeError.unknown(e.bestMessage), eventId)
            }
        }
    }

    private suspend fun initShortcuts(
        currentWallet: WalletEntity
    ) = withContext(Dispatchers.IO) {
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
            accountRepository.logout()
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

    fun openDappScreen(
        walletId: String, app: AppEntity, dAppUrl: Uri, source: String
    ) {
        viewModelScope.launch { // TODO Refactor
            val wallet = accountRepository.getWalletById(walletId)
                ?: throw IllegalArgumentException("walletId doesn't exists")

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
                analyticsHelper.trackPushClick(
                    pushId = pushId ?: pushType,
                    payload = deeplink.toString(),
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
            return accountRepository.selectedWalletFlow.firstOrNull()
        }
    }

    fun processDeepLink(
        uri: Uri,
        fromQR: Boolean,
        refSource: Uri?,
        internal: Boolean,
        fromPackageName: String?
    ): Boolean {
        savedState.returnUri = null
        val deeplink = DeepLink(uri, fromQR, refSource)
        if (deeplink.route is DeepLinkRoute.Unknown) {
            viewModelScope.launch { showInvalidLinkToast(deeplink.route) }
            return false
        }
        if (deeplink.route is DeepLinkRoute.Internal && !internal) {
            return true
        }
        accountRepository.selectedStateFlow.take(1).onEach { state ->
            val route = deeplink.route
            if (route is DeepLinkRoute.Signer) {
                processSignerDeepLink(route, fromQR)
            } else if (state is AccountRepository.SelectedState.Wallet) {
                processDeepLink(state.wallet, deeplink, fromPackageName)
            }
        }.launch()
        return true
    }

    fun processTonConnectDeepLink(deeplink: DeepLink, fromPackageName: String?) {
        val route = deeplink.route as DeepLinkRoute.TonConnect

        savedState.returnUri = tonConnectManager.processDeeplink(
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
        fromPackageName: String?
    ) {
        val route = deeplink.route
        if (route is DeepLinkRoute.DnsRenew) {
            openScreen(DNSRenewScreen.newInstance(wallet, emptyList()))
        } else if (route is DeepLinkRoute.TonConnect) {
            if (!wallet.isTonConnectSupported && accountRepository.getWallets().count { it.isTonConnectSupported } == 0) {
                openScreen(AddWalletScreen.newInstance(true))
                return
            }
            processTonConnectDeepLink(deeplink, fromPackageName)
        } else if (route is DeepLinkRoute.Story) {
            showStory(route.id, "deep-link")
        } else if (route is DeepLinkRoute.Tabs) {
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
            openScreen(StakingScreen.newInstance(wallet, from = "deeplink"))
        } else if (route is DeepLinkRoute.StakingPool) {
            openScreen(StakeViewerScreen.newInstance(wallet, address = route.poolAddress, name = ""))
        } else if (route is DeepLinkRoute.AccountEvent) {
            val address = route.address
            if (address == null) {
                showTransaction(route.eventId)
            } else {
                showTransaction(address, route.eventId)
            }
        } else if (route is DeepLinkRoute.Transfer && !wallet.isWatchOnly) {
            processTransferDeepLink(wallet, deeplink, route)
        } else if (route is DeepLinkRoute.PickWallet) {
            accountRepository.setSelectedWallet(route.walletId)
        } else if (route is DeepLinkRoute.Swap && !api.getConfig(wallet.network).flags.disableSwap) {
            _eventFlow.tryEmit(
                RootEvent.Swap(
                    wallet = wallet,
                    uri = api.getConfig(wallet.network).swapUri,
                    address = wallet.address,
                    from = route.from,
                    to = route.to
                )
            )
        } else if (route is DeepLinkRoute.Battery && !wallet.isWatchOnly) {
            openBattery(wallet, route)
        } else if (route is DeepLinkRoute.Purchase && !wallet.isWatchOnly) {
            if (WalletFeature.NewRampFlow.isEnabled) {
                openScreen(DepositFragment())
            } else {
                openScreen(OnRampScreen.newInstance(context, wallet, "deep-link"))
            }
        } else if (route is DeepLinkRoute.Deposit && !wallet.isWatchOnly) {
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
        } else if (route is DeepLinkRoute.Withdraw && !wallet.isWatchOnly) {
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
        } else if (route is DeepLinkRoute.Exchange && !wallet.isWatchOnly) {
            val method = purchaseRepository.getMethod(
                id = route.methodName,
                network = wallet.network,
                locale = settingsRepository.getLocale()
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
        } else if (route is DeepLinkRoute.Backups && wallet.hasPrivateKey) {
            openScreen(BackupScreen.newInstance(wallet))
        } else if (route is DeepLinkRoute.Settings) {
            openScreen(SettingsScreen.newInstance(wallet, from = "deeplink"))
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
                deeplink = dAppUri
            )

            if (!isTrustedApp && settingsRepository.isDAppOpenConfirm(wallet.id, app.host)) {
                openScreen(DAppConfirmFragment.newInstance(wallet.id, app, dAppUri))
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
            openScreen(SettingsScreen.newInstance(wallet, "deeplink"))
        } else if (route is DeepLinkRoute.EditWalletLabel) {
            openScreen(EditNameScreen.newInstance(wallet))
        } else if (route is DeepLinkRoute.Camera && !wallet.isWatchOnly) {
            openScreen(CameraScreen.newInstance())
        } else if (route is DeepLinkRoute.Receive) {
            openScreen(QrAssetFragment.newInstance())
        } else if (route is DeepLinkRoute.ManageAssets) {
            openScreen(TokensManageScreen.newInstance(wallet))
        } else if (route is DeepLinkRoute.WalletPicker) {
            openScreen(PickerScreen.newInstance(from = "deeplink"))
        } else if (route is DeepLinkRoute.Jetton) {
            openTokenViewer(wallet, route)
        } else if (route is DeepLinkRoute.Install) {
            installAPK(route)
        } else {
            showInvalidLinkToast(deeplink.route)
        }
    }

    private suspend fun showInvalidLinkToast(route: DeepLinkRoute) {
        if (!(route is DeepLinkRoute.Unknown && (route.uri.hasRefer() || route.uri.hasUtmSource()))) {
            toast(Localization.invalid_link)
        }
    }

    private suspend fun installAPK(route: DeepLinkRoute.Install) {
        if (!apkManager.install(context, route.file)) {
            toast(Localization.invalid_link)
        }
    }

    private suspend fun openBattery(wallet: WalletEntity, route: DeepLinkRoute.Battery) {
        val promoCode = route.promocode
        if (promoCode.isNullOrEmpty()) {
            openScreen(BatteryScreen.newInstance(wallet, from = "deeplink", jetton = route.jetton))
        } else {
            loading(true)
            val validCode = api.batteryVerifyPurchasePromo(wallet.network, promoCode)
            loading(false)
            if (validCode) {
                openScreen(
                    BatteryScreen.newInstance(
                        wallet,
                        promoCode,
                        "deeplink",
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

        val decimals = route.jettonAddress?.let {
            tokenRepository.getToken(wallet.accountId, wallet.network, it)
        }?.decimals ?: WalletCurrency.TON.decimals

        val amount = route.amount?.let {
            Coins.of(it, decimals)
        }

        _eventFlow.tryEmit(
            RootEvent.Transfer(
                wallet = wallet,
                address = route.address,
                amount = amount,
                text = route.text,
                jettonAddress = route.jettonAddress,
                bin = route.bin,
                initStateBase64 = route.initStateBase64,
                validUnit = route.exp,
                source = deepLink.source,
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
                safeMode = settingsRepository.isSafeModeEnabled(wallet.network),
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
                safeMode = settingsRepository.isSafeModeEnabled(wallet.network),
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
            tonConnectManager.sendSignDataResponseSuccess(
                connection,
                proof,
                wallet.address,
                payload,
                eventId
            )
        } catch (e: Throwable) {
            DevSettings.tonConnectLog("Error while signing data: ${e.bestMessage}", error = true)
            if (e is CancellationException) {
                tonConnectManager.showLogoutAppBar(wallet, context, connection.appUrl)
                tonConnectManager.sendBridgeError(
                    connection,
                    BridgeError.userDeclinedTransaction(),
                    eventId
                )
            } else {
                tonConnectManager.sendBridgeError(
                    connection,
                    BridgeError.unknown(e.bestMessage),
                    eventId
                )
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
