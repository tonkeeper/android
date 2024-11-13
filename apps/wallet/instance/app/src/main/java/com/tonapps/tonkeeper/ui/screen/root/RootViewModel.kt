package com.tonapps.tonkeeper.ui.screen.root

import android.app.Application
import android.net.Uri
import android.os.Bundle
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.net.toUri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.crashlytics.setCustomKeys
import com.google.firebase.ktx.Firebase
import com.tonapps.blockchain.ton.extensions.cellFromBase64
import com.tonapps.blockchain.ton.extensions.equalsAddress
import com.tonapps.blockchain.ton.extensions.toAccountId
import com.tonapps.extensions.MutableEffectFlow
import com.tonapps.extensions.bestMessage
import com.tonapps.extensions.currentTimeSeconds
import com.tonapps.extensions.locale
import com.tonapps.extensions.setLocales
import com.tonapps.extensions.toUriOrNull
import com.tonapps.ledger.ton.LedgerConnectData
import com.tonapps.tonkeeper.Environment
import com.tonapps.tonkeeper.api.getCurrencyCodeByCountry
import com.tonapps.tonkeeper.core.AnalyticsHelper
import com.tonapps.tonkeeper.core.DevSettings
import com.tonapps.tonkeeper.core.entities.WalletPurchaseMethodEntity
import com.tonapps.tonkeeper.core.history.HistoryHelper
import com.tonapps.tonkeeper.core.history.list.item.HistoryItem
import com.tonapps.tonkeeper.deeplink.DeepLink
import com.tonapps.tonkeeper.deeplink.DeepLinkRoute
import com.tonapps.tonkeeper.extensions.safeExternalOpenUri
import com.tonapps.tonkeeper.helper.BrowserHelper
import com.tonapps.tonkeeper.helper.ShortcutHelper
import com.tonapps.tonkeeper.manager.push.FirebasePush
import com.tonapps.tonkeeper.manager.push.PushManager
import com.tonapps.tonkeeper.manager.tonconnect.TonConnectManager
import com.tonapps.tonkeeper.manager.tonconnect.bridge.model.BridgeError
import com.tonapps.tonkeeper.ui.base.BaseWalletVM
import com.tonapps.tonkeeper.ui.screen.backup.main.BackupScreen
import com.tonapps.tonkeeper.ui.screen.battery.BatteryScreen
import com.tonapps.tonkeeper.ui.screen.browser.dapp.DAppScreen
import com.tonapps.tonkeeper.ui.screen.camera.CameraScreen
import com.tonapps.tonkeeper.ui.screen.card.CardScreen
import com.tonapps.tonkeeper.ui.screen.init.list.AccountItem
import com.tonapps.tonkeeper.ui.screen.name.edit.EditNameScreen
import com.tonapps.tonkeeper.ui.screen.purchase.PurchaseScreen
import com.tonapps.tonkeeper.ui.screen.qr.QRScreen
import com.tonapps.tonkeeper.ui.screen.send.main.SendScreen
import com.tonapps.tonkeeper.ui.screen.send.transaction.SendTransactionScreen
import com.tonapps.tonkeeper.ui.screen.settings.currency.CurrencyScreen
import com.tonapps.tonkeeper.ui.screen.settings.language.LanguageScreen
import com.tonapps.tonkeeper.ui.screen.settings.main.SettingsScreen
import com.tonapps.tonkeeper.ui.screen.staking.stake.StakingScreen
import com.tonapps.tonkeeper.ui.screen.staking.viewer.StakeViewerScreen
import com.tonapps.tonkeeper.ui.screen.token.viewer.TokenScreen
import com.tonapps.tonkeeper.ui.screen.transaction.TransactionScreen
import com.tonapps.tonkeeper.ui.screen.wallet.manage.TokensManageScreen
import com.tonapps.tonkeeper.ui.screen.wallet.picker.PickerScreen
import com.tonapps.tonkeeperx.R
import com.tonapps.wallet.api.API
import com.tonapps.wallet.api.entity.TokenEntity
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.browser.BrowserRepository
import com.tonapps.wallet.data.core.entity.SignRequestEntity
import com.tonapps.wallet.data.dapps.entities.AppConnectEntity
import com.tonapps.wallet.data.passcode.LockScreen
import com.tonapps.wallet.data.passcode.PasscodeManager
import com.tonapps.wallet.data.purchase.PurchaseRepository
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
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.ton.cell.Cell
import uikit.extensions.activity
import uikit.navigation.Navigation.Companion.navigation
import java.util.concurrent.CancellationException

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
    savedStateHandle: SavedStateHandle,
): BaseWalletVM(app) {

    private val savedState = RootModelState(savedStateHandle)

    private val appUpdateManager: AppUpdateManager by lazy {
        AppUpdateManagerFactory.create(context)
    }

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
        pushManager.clearNotifications()

        tonConnectManager.transactionRequestFlow.map { (connection, message) ->
            val tx = RootSignTransaction(connection, message, savedState.returnUri)
            savedState.returnUri = null
            tx
        }.filter { !ignoreTonConnectTransaction.contains(it.hash) }.collectFlow {
            _eventFlow.tryEmit(RootEvent.CloseCurrentTonConnect)
            viewModelScope.launch {
                ignoreTonConnectTransaction.add(it.hash)
                signTransaction(it)
            }
        }

        settingsRepository.languageFlow.collectFlow {
            context.setLocales(settingsRepository.localeList)
        }

        accountRepository.selectedStateFlow.filter {
            it !is AccountRepository.SelectedState.Initialization
        }.onEach { state ->
            if (state is AccountRepository.SelectedState.Empty) {
                _hasWalletFlow.tryEmit(false)
                ShortcutManagerCompat.removeAllDynamicShortcuts(context)
            } else if (state is AccountRepository.SelectedState.Wallet) {
                _hasWalletFlow.tryEmit(true)
            }
        }.flowOn(Dispatchers.IO).launchIn(viewModelScope)

        viewModelScope.launch(Dispatchers.IO) {
            settingsRepository.firebaseToken = FirebasePush.requestToken()
        }

        selectedWalletFlow.collectFlow { wallet ->
            applyAnalyticsKeys(wallet)
            initShortcuts(wallet)
        }

        api.configFlow.take(1).collectFlow { config ->
            AnalyticsHelper.setConfig(context, config)
            AnalyticsHelper.trackEvent("launch_app", settingsRepository.installId)
        }

        settingsRepository.countryFlow.take(1).filter { it.isBlank() }.map {
            api.resolveCountry()
        }.filterNotNull().onEach {
            settingsRepository.country = it
        }.flowOn(Dispatchers.IO).launchIn(viewModelScope)

        viewModelScope.launch(Dispatchers.IO) {
            if (environment.isGooglePlayServicesAvailable) {
                delay(2000)
                checkAppUpdate()
            }
        }
    }

    private suspend fun checkAppUpdate() = withContext(Dispatchers.IO) {
        try {
            val updateInfo = appUpdateManager.appUpdateInfo.await()
            if (updateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE) {
                startUpdateFlow(updateInfo)
            }
        } catch (e: Throwable) {
            FirebaseCrashlytics.getInstance().recordException(e)
        }
    }

    private suspend fun startUpdateFlow(appUpdateInfo: AppUpdateInfo) = withContext(Dispatchers.Main) {
        val activity = context.activity ?: return@withContext
        appUpdateManager.startUpdateFlowForResult(
            appUpdateInfo,
            activity,
            AppUpdateOptions.defaultOptions(AppUpdateType.IMMEDIATE),
            0
        )
    }

    fun connectTonConnectBridge() {
        tonConnectManager.connectBridge()
    }

    fun disconnectTonConnectBridge() {
        tonConnectManager.disconnectBridge()
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
        if (signRequest.from != null && !signRequest.from!!.toAccountId().equalsAddress(connection.accountId)) {
            DevSettings.tonConnectLog("Invalid \"from\" address.\nReceived: ${signRequest.from?.toAccountId()}\nExpected: ${connection.accountId}", error = true)
            tonConnectManager.sendBridgeError(connection, BridgeError.badRequest("Invalid \"from\" address. Specified wallet address not connected to this app."), eventId)
            return
        }

        val now = currentTimeSeconds()
        val max = now + 86400
        if (signRequest.validUntil != 0L && now >= signRequest.validUntil) {
            tonConnectManager.sendBridgeError(connection, BridgeError.badRequest("Transaction has expired"), eventId)
            return
        } else if (signRequest.validUntil != 0L && signRequest.validUntil > max) {
            tonConnectManager.sendBridgeError(connection, BridgeError.badRequest("Invalid validUntil field. Transaction validity duration exceeds maximum limit of 24 hours. Max: $max Received: ${signRequest.validUntil}"), eventId)
            return
        }

        val wallets = accountRepository.getWalletsByAccountId(
            accountId = connection.accountId,
            testnet = connection.testnet
        ).filter {
            it.isTonConnectSupported
        }
        if (wallets.isEmpty()) {
            tonConnectManager.sendBridgeError(connection, BridgeError.unknown(""), eventId)
            return
        }
        val wallet = wallets.find { it.hasPrivateKey } ?: wallets.first()
        try {
            val boc = SendTransactionScreen.run(context, wallet, signRequest)
            tonConnectManager.sendTransactionResponseSuccess(connection, boc, eventId)
        } catch (e: Throwable) {
            DevSettings.tonConnectLog("Error while signing transaction: ${e.bestMessage}", error = true)
            if (e is CancellationException) {
                tonConnectManager.sendBridgeError(connection, BridgeError.userDeclinedTransaction(), eventId)
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
            list.add(ShortcutHelper.shortcutAction(context, Localization.send, R.drawable.ic_send_shortcut, "tonkeeper://send"))
        }
        list.addAll(walletShortcutsFromWallet(currentWallet, wallets))
        ShortcutManagerCompat.setDynamicShortcuts(context, list.take(3))
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
            list.add(ShortcutHelper.shortcutWallet(context, wallet))
        }
        return list
    }

    private fun applyAnalyticsKeys(wallet: WalletEntity) {
        val crashlytics = Firebase.crashlytics
        crashlytics.setUserId(wallet.accountId)
        crashlytics.setCustomKeys {
            key("testnet", wallet.testnet)
            key("walletType", wallet.type.name)
            key("wallet", wallet.address)
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

    fun processIntentExtras(bundle: Bundle) {
        val pushType = bundle.getString("type") ?: return
        hasWalletFlow.take(1).collectFlow {
            if (pushType == "console_dapp_notification") {
                processDAppPush(bundle)
            } else {
                val deeplink = bundle.getString("deeplink")?.toUriOrNull() ?: return@collectFlow
                processDeepLinkPush(deeplink, bundle)
            }
        }
    }

    private suspend fun processDAppPush(bundle: Bundle) {
        val accountId = bundle.getString("account") ?: return
        val dappUrl = bundle.getString("dapp_url")?.toUriOrNull() ?: return
        val connections = tonConnectManager.accountConnectionsFlow(accountId).firstOrNull()?.filter { it.appUrl == dappUrl } ?: return
        if (connections.isEmpty()) {
            return
        }
        val wallet = accountRepository.getWalletByAccountId(accountId) ?: return
        val openUrl = bundle.getString("link")?.toUriOrNull() ?: dappUrl
        openScreen(DAppScreen.newInstance(wallet, url = openUrl))
    }

    private suspend fun processDeepLinkPush(uri: Uri, bundle: Bundle) {
        val accountId = bundle.getString("account") ?: return
        val wallet = accountRepository.getWalletByAccountId(accountId) ?: return
        val deeplink = DeepLink(uri, false, null)
        processDeepLink(wallet, deeplink, null)
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
            return false
        }
        if (deeplink.route is DeepLinkRoute.Internal && !internal) {
            return true
        }
        accountRepository.selectedStateFlow.take(1).onEach { state ->
            if (deeplink.route is DeepLinkRoute.Signer) {
                processSignerDeepLink(deeplink.route, fromQR)
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

    private suspend fun processDeepLink(wallet: WalletEntity, deeplink: DeepLink, fromPackageName: String?) {
        val route = deeplink.route
        if (route is DeepLinkRoute.TonConnect && !wallet.isWatchOnly) {
            processTonConnectDeepLink(deeplink, fromPackageName)
        } else if (route is DeepLinkRoute.Tabs) {
            _eventFlow.tryEmit(RootEvent.OpenTab(route.tabUri, wallet))
        } else if (route is DeepLinkRoute.Send && !wallet.isWatchOnly) {
            openScreen(SendScreen.newInstance(wallet))
        } else if (route is DeepLinkRoute.Staking && !wallet.isWatchOnly) {
            openScreen(StakingScreen.newInstance(wallet))
        } else if (route is DeepLinkRoute.StakingPool) {
            openScreen(StakeViewerScreen.newInstance(wallet, route.poolAddress, ""))
        } else if (route is DeepLinkRoute.AccountEvent) {
            if (route.address == null) {
                showTransaction(route.eventId)
            } else {
                showTransaction(route.address, route.eventId)
            }
        } else if (route is DeepLinkRoute.Transfer && !wallet.isWatchOnly) {
            processTransferDeepLink(wallet, route)
        } else if (route is DeepLinkRoute.PickWallet) {
            accountRepository.setSelectedWallet(route.walletId)
        } else if (route is DeepLinkRoute.Swap) {
            _eventFlow.tryEmit(RootEvent.Swap(
                wallet = wallet,
                uri = api.config.swapUri,
                address = wallet.address,
                from = route.from,
                to = route.to
            ))
        } else if (route is DeepLinkRoute.Battery && !wallet.isWatchOnly) {
            openBattery(wallet, route)
        } else if (route is DeepLinkRoute.Purchase && !wallet.isWatchOnly) {
            openScreen(PurchaseScreen.newInstance(wallet))
        } else if (route is DeepLinkRoute.Exchange && !wallet.isWatchOnly) {
            val method = purchaseRepository.getMethod(
                id = route.methodName,
                testnet = wallet.testnet,
                locale = settingsRepository.getLocale()
            )
            if (method == null) {
                toast(Localization.payment_method_not_found)
            } else {
                BrowserHelper.openPurchase(context, WalletPurchaseMethodEntity(
                    method = method,
                    wallet = wallet,
                    currency = api.getCurrencyCodeByCountry(settingsRepository),
                    config = api.config
                ))
            }
        } else if (route is DeepLinkRoute.Backups && wallet.hasPrivateKey) {
            openScreen(BackupScreen.newInstance(wallet))
        } else if (route is DeepLinkRoute.Settings) {
            openScreen(SettingsScreen.newInstance(wallet))
        } else if (route is DeepLinkRoute.DApp && !wallet.isWatchOnly) {
            val dAppUri = route.url.toUri()
            val dApp = browserRepository.getApps(
                country = settingsRepository.country,
                testnet = wallet.testnet,
                locale = context.locale
            ).find { it.url.host == dAppUri.host }
            if (dApp == null) {
                toast(Localization.app_not_found)
            } else {
                openScreen(DAppScreen.newInstance(wallet, url = dAppUri))
            }
        } else if (route is DeepLinkRoute.SettingsSecurity && wallet.hasPrivateKey) {
            openScreen(SettingsScreen.newInstance(wallet))
        } else if (route is DeepLinkRoute.SettingsCurrency) {
            openScreen(CurrencyScreen.newInstance())
        } else if (route is DeepLinkRoute.SettingsLanguage) {
            openScreen(LanguageScreen.newInstance())
        } else if (route is DeepLinkRoute.SettingsNotifications) {
            openScreen(SettingsScreen.newInstance(wallet))
        } else if (route is DeepLinkRoute.EditWalletLabel) {
            openScreen(EditNameScreen.newInstance(wallet))
        } else if (route is DeepLinkRoute.Camera && !wallet.isWatchOnly) {
            openScreen(CameraScreen.newInstance())
        } else if (route is DeepLinkRoute.Receive) {
            openScreen(QRScreen.newInstance(wallet, TokenEntity.TON))
        } else if (route is DeepLinkRoute.ManageAssets) {
            openScreen(TokensManageScreen.newInstance(wallet))
        } else if (route is DeepLinkRoute.WalletPicker) {
            openScreen(PickerScreen.newInstance())
        } else if (route is DeepLinkRoute.Jetton) {
            openTokenViewer(wallet, route)
        } else {
            toast(Localization.invalid_link)
        }
    }

    private suspend fun openBattery(wallet: WalletEntity, route: DeepLinkRoute.Battery) {
        val promoCode = route.promocode
        if (promoCode.isNullOrEmpty()) {
            openScreen(BatteryScreen.newInstance(wallet))
        } else {
            loading(true)
            val validCode = api.batteryVerifyPurchasePromo(wallet.testnet, promoCode)
            loading(false)
            if (validCode) {
                openScreen(BatteryScreen.newInstance(wallet, promoCode))
            } else {
                toast(Localization.wrong_promocode)
            }
        }
    }

    private suspend fun openTokenViewer(wallet: WalletEntity, route: DeepLinkRoute.Jetton) {
        val token = tokenRepository.getToken(wallet.accountId, wallet.testnet, route.address) ?: return
        openScreen(TokenScreen.newInstance(wallet, token.address, token.name, token.symbol))
    }

    private suspend fun processTransferDeepLink(wallet: WalletEntity, route: DeepLinkRoute.Transfer) {
        if (route.isExpired) {
            toast(Localization.expired_link)
            return
        }
        val bin: Cell? = if (route.bin.isNullOrEmpty()) {
            null
        } else {
            try {
                route.bin.cellFromBase64()
            } catch (e: Throwable) {
                toast(Localization.invalid_link)
                return
            }
        }

        _eventFlow.tryEmit(RootEvent.Transfer(
            wallet = wallet,
            address = route.address,
            amount = route.amount,
            text = route.text,
            jettonAddress = route.jettonAddress,
            bin = bin
        ))
    }

    private fun processSignerDeepLink(route: DeepLinkRoute.Signer, fromQR: Boolean) {
        _eventFlow.tryEmit(RootEvent.Singer(
            publicKey = route.publicKey,
            name = route.name,
            qr = fromQR || !route.local
        ))
    }

    private suspend fun showTransaction(hash: String) {
        val wallet = selectedWalletFlow.firstOrNull() ?: return
        val tx = historyHelper.getEvent(wallet, hash).filterIsInstance<HistoryItem.Event>().firstOrNull() ?: return
        openScreen(TransactionScreen.newInstance(tx))
    }

    private suspend fun showTransaction(accountId: String, hash: String) {
        val wallet = accountRepository.getWalletByAccountId(accountId, false) ?: return
        val event = api.getTransactionEvents(wallet.accountId, wallet.testnet, hash) ?: return
        val tx = historyHelper.mapping(wallet, event).filterIsInstance<HistoryItem.Event>().firstOrNull() ?: return
        openScreen(TransactionScreen.newInstance(tx))
    }
}