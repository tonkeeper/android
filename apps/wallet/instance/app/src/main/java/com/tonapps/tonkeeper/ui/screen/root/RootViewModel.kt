package com.tonapps.tonkeeper.ui.screen.root

import android.app.Application
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.net.toUri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.crashlytics.setCustomKeys
import com.google.firebase.ktx.Firebase
import com.tonapps.blockchain.ton.TonNetwork
import com.tonapps.extensions.MutableEffectFlow
import com.tonapps.extensions.locale
import com.tonapps.extensions.setLocales
import com.tonapps.extensions.toUriOrNull
import com.tonapps.ledger.ton.LedgerConnectData
import com.tonapps.tonkeeper.core.AnalyticsHelper
import com.tonapps.tonkeeper.core.entities.WalletPurchaseMethodEntity
import com.tonapps.tonkeeper.core.history.HistoryHelper
import com.tonapps.tonkeeper.core.history.list.item.HistoryItem
import com.tonapps.tonkeeper.core.widget.Widget
import com.tonapps.tonkeeper.deeplink.DeepLink
import com.tonapps.tonkeeper.deeplink.DeepLinkRoute
import com.tonapps.tonkeeper.extensions.safeExternalOpenUri
import com.tonapps.tonkeeper.helper.ShortcutHelper
import com.tonapps.tonkeeper.manager.push.FirebasePush
import com.tonapps.tonkeeper.manager.tonconnect.TonConnectManager
import com.tonapps.tonkeeper.manager.tonconnect.bridge.model.BridgeError
import com.tonapps.tonkeeper.manager.tonconnect.bridge.model.BridgeEvent
import com.tonapps.tonkeeper.ui.base.BaseWalletVM
import com.tonapps.tonkeeper.ui.screen.backup.main.BackupScreen
import com.tonapps.tonkeeper.ui.screen.battery.BatteryScreen
import com.tonapps.tonkeeper.ui.screen.browser.dapp.DAppScreen
import com.tonapps.tonkeeper.ui.screen.camera.CameraScreen
import com.tonapps.tonkeeper.ui.screen.init.list.AccountItem
import com.tonapps.tonkeeper.ui.screen.name.edit.EditNameScreen
import com.tonapps.tonkeeper.ui.screen.purchase.main.PurchaseScreen
import com.tonapps.tonkeeper.ui.screen.purchase.web.PurchaseWebScreen
import com.tonapps.tonkeeper.ui.screen.qr.QRScreen
import com.tonapps.tonkeeper.ui.screen.send.main.SendScreen
import com.tonapps.tonkeeper.ui.screen.send.transaction.SendTransactionScreen
import com.tonapps.tonkeeper.ui.screen.settings.currency.CurrencyScreen
import com.tonapps.tonkeeper.ui.screen.settings.language.LanguageScreen
import com.tonapps.tonkeeper.ui.screen.settings.main.SettingsScreen
import com.tonapps.tonkeeper.ui.screen.staking.stake.StakingScreen
import com.tonapps.tonkeeper.ui.screen.staking.viewer.StakeViewerScreen
import com.tonapps.tonkeeper.ui.screen.transaction.TransactionScreen
import com.tonapps.tonkeeper.ui.screen.wallet.main.WalletViewModel.Companion.getWalletScreen
import com.tonapps.tonkeeper.ui.screen.wallet.main.list.Item
import com.tonapps.tonkeeper.ui.screen.wallet.main.list.WalletAdapter
import com.tonapps.tonkeeper.ui.screen.wallet.manage.TokensManageScreen
import com.tonapps.tonkeeper.ui.screen.wallet.picker.PickerScreen
import com.tonapps.tonkeeperx.R
import com.tonapps.wallet.api.API
import com.tonapps.wallet.api.entity.TokenEntity
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.browser.BrowserRepository
import com.tonapps.wallet.data.core.ScreenCacheSource
import com.tonapps.wallet.data.core.Theme
import com.tonapps.wallet.data.core.entity.SignRequestEntity
import com.tonapps.wallet.data.dapps.entities.AppConnectEntity
import com.tonapps.wallet.data.passcode.PasscodeManager
import com.tonapps.wallet.data.purchase.PurchaseRepository
import com.tonapps.wallet.data.settings.SettingsRepository
import com.tonapps.wallet.localization.Localization
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.timeout
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.seconds

class RootViewModel(
    app: Application,
    private val passcodeManager: PasscodeManager,
    private val settingsRepository: SettingsRepository,
    private val accountRepository: AccountRepository,
    private val api: API,
    private val historyHelper: HistoryHelper,
    private val screenCacheSource: ScreenCacheSource,
    private val walletAdapter: WalletAdapter,
    private val purchaseRepository: PurchaseRepository,
    private val tonConnectManager: TonConnectManager,
    private val browserRepository: BrowserRepository,
    savedStateHandle: SavedStateHandle,
): BaseWalletVM(app) {

    data class Passcode(
        val show: Boolean,
        val biometric: Boolean,
    )

    private val savedState = RootModelState(savedStateHandle)

    private val selectedWalletFlow: Flow<WalletEntity> = accountRepository.selectedWalletFlow

    private val _hasWalletFlow = MutableEffectFlow<Boolean?>()
    val hasWalletFlow = _hasWalletFlow.asSharedFlow().filterNotNull()

    private val _eventFlow = MutableEffectFlow<RootEvent?>()
    val eventFlow = _eventFlow.asSharedFlow().filterNotNull()

    private val _passcodeFlow = MutableStateFlow<Passcode?>(null)
    val passcodeFlow = _passcodeFlow.asStateFlow().filterNotNull()

    val theme: Theme
        get() = settingsRepository.theme

    init {
        tonConnectManager.transactionRequestFlow.collectFlow(::sendTransaction)

        settingsRepository.languageFlow.collectFlow {
            context.setLocales(settingsRepository.localeList)
        }

        combine(
            settingsRepository.biometricFlow.take(1),
            settingsRepository.lockscreenFlow.take(1)
        ) { biometric, lockscreen ->
            Passcode(lockscreen, biometric)
        }.collectFlow {
            _passcodeFlow.value = it
        }

        combine(
            accountRepository.selectedStateFlow.filter { it !is AccountRepository.SelectedState.Initialization },
            api.configFlow,
        ) { state, _ ->
            if (state is AccountRepository.SelectedState.Empty) {
                _hasWalletFlow.tryEmit(false)
                ShortcutManagerCompat.removeAllDynamicShortcuts(context)
            } else if (state is AccountRepository.SelectedState.Wallet) {
                val items = screenCacheSource.getWalletScreen(state.wallet) ?: listOf(Item.Skeleton(true))
                submitWalletList(items)
            }
            Widget.updateAll()
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
            AnalyticsHelper.trackEvent("launch_app")
        }

        settingsRepository.countryFlow.take(1).filter { it.isBlank() }.map {
            api.resolveCountry()
        }.filterNotNull().onEach {
            settingsRepository.country = it
        }.flowOn(Dispatchers.IO).launchIn(viewModelScope)
    }

    private suspend fun sendTransaction(pair: Pair<AppConnectEntity, BridgeEvent.Message>) {
        val (connection, message) = pair
        val eventId = message.id
        try {
            val signRequests = message.params.map { SignRequestEntity(it) }
            for (signRequest in signRequests) {
                signRequest(eventId, connection, signRequest)
            }
        } catch (e: Exception) {
            tonConnectManager.sendBridgeError(connection, BridgeError.BAD_REQUEST, eventId)
        }

        savedState.returnUri?.let {
            context.safeExternalOpenUri(it)
        }
        savedState.returnUri = null
    }

    private suspend fun signRequest(
        eventId: Long,
        connection: AppConnectEntity,
        signRequest: SignRequestEntity
    ) {
        if (signRequest.network == TonNetwork.TESTNET) {
            tonConnectManager.sendBridgeError(connection, BridgeError.METHOD_NOT_SUPPORTED, eventId)
            return
        }
        val wallets = accountRepository.getWalletsByAccountId(
            accountId = connection.accountId,
            testnet = connection.testnet
        ).filter {
            it.isTonConnectSupported
        }
        if (wallets.isEmpty()) {
            tonConnectManager.sendBridgeError(connection, BridgeError.UNKNOWN_APP, eventId)
            return
        }
        val wallet = wallets.find { it.hasPrivateKey } ?: wallets.first()
        try {
            val boc = SendTransactionScreen.run(context, wallet, signRequest)
            tonConnectManager.sendTransactionResponseSuccess(connection, boc, eventId)
        } catch (e: Throwable) {
            if (e is BridgeError.Exception) {
                tonConnectManager.sendBridgeError(connection, e.error, eventId)
            } else {
                tonConnectManager.sendBridgeError(connection, BridgeError.USER_DECLINED_TRANSACTION, eventId)
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
            if (wallet == currentWallet) {
                continue
            }
            list.add(ShortcutHelper.shortcutWallet(context, wallet))
        }
        return list
    }

    private fun applyAnalyticsKeys(wallet: WalletEntity) {
        val crashlytics = Firebase.crashlytics
        // crashlytics.setUserId(wallet.accountId)
        crashlytics.setCustomKeys {
            key("testnet", wallet.testnet)
            key("walletType", wallet.type.name)
        }
    }

    private suspend fun submitWalletList(items: List<Item>) = withContext(Dispatchers.Main) {
        walletAdapter.submitList(items) {
            _hasWalletFlow.tryEmit(true)
        }
    }

    fun signOut() {
        viewModelScope.launch {
            accountRepository.logout()
            passcodeManager.reset()
            hidePasscode()
        }
    }

    private fun hidePasscode() {
        _passcodeFlow.value = Passcode(show = false, biometric = false)
    }

    fun connectLedger(connectData: LedgerConnectData, accounts: List<AccountItem>) {
        _eventFlow.tryEmit(RootEvent.Ledger(connectData, accounts))
    }

    fun checkPasscode(context: Context, code: String): Flow<Unit> = flow {
        val valid = passcodeManager.isValid(context, code)
        if (valid) {
            hidePasscode()
            emit(Unit)
        } else {
            throw Exception("invalid passcode")
        }
    }.take(1)

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
        processDeepLink(wallet, deeplink)
    }

    fun processDeepLink(
        uri: Uri,
        fromQR: Boolean,
        refSource: Uri?
    ): Boolean {
        savedState.returnUri = null
        val deeplink = DeepLink(uri, fromQR, refSource)
        accountRepository.selectedStateFlow.take(1).onEach { state ->
            delay(1000)
            if (deeplink.route is DeepLinkRoute.Signer) {
                processSignerDeepLink(deeplink.route, fromQR)
            } else if (state is AccountRepository.SelectedState.Wallet) {
                processDeepLink(state.wallet, deeplink)
            }
        }.launch()
        return true
    }

    private suspend fun processDeepLink(wallet: WalletEntity, deeplink: DeepLink) {
        val route = deeplink.route
        if (route is DeepLinkRoute.TonConnect) {
            savedState.returnUri = tonConnectManager.processDeeplink(
                context = context,
                uri = route.uri,
                fromQR = deeplink.fromQR,
                refSource = deeplink.referrer
            )
        } else if (route is DeepLinkRoute.Tabs) {
            _eventFlow.tryEmit(RootEvent.OpenTab(route.tabUri, wallet))
        } else if (route is DeepLinkRoute.Send) {
            openScreen(SendScreen.newInstance(wallet))
        } else if (route is DeepLinkRoute.Staking) {
            openScreen(StakingScreen.newInstance(wallet))
        } else if (route is DeepLinkRoute.StakingPool) {
            openScreen(StakeViewerScreen.newInstance(wallet, route.poolAddress, ""))
        } else if (route is DeepLinkRoute.AccountEvent) {
            if (route.address == null) {
                showTransaction(route.eventId)
            } else {
                showTransaction(route.address, route.eventId)
            }
        } else if (route is DeepLinkRoute.Transfer) {
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
        } else if (route is DeepLinkRoute.Battery) {
            openScreen(BatteryScreen.newInstance(wallet, route.promocode))
        } else if (route is DeepLinkRoute.Purchase) {
            openScreen(PurchaseScreen.newInstance(wallet))
        } else if (route is DeepLinkRoute.Exchange) {
            val method = purchaseRepository.getMethod(
                id = route.methodName,
                testnet = wallet.testnet,
                locale = settingsRepository.getLocale()
            )
            if (method == null) {
                toast(Localization.payment_method_not_found)
            } else {
                PurchaseWebScreen.open(context, WalletPurchaseMethodEntity(
                    method = method,
                    wallet = wallet,
                    currency = settingsRepository.currency.code,
                    config = api.config
                ))
            }
        } else if (route is DeepLinkRoute.Backups) {
            openScreen(BackupScreen.newInstance(wallet))
        } else if (route is DeepLinkRoute.Settings) {
            openScreen(SettingsScreen.newInstance(wallet))
        } else if (route is DeepLinkRoute.DApp) {
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
        } else if (route is DeepLinkRoute.SettingsSecurity) {
            openScreen(SettingsScreen.newInstance(wallet))
        } else if (route is DeepLinkRoute.SettingsCurrency) {
            openScreen(CurrencyScreen.newInstance())
        } else if (route is DeepLinkRoute.SettingsLanguage) {
            openScreen(LanguageScreen.newInstance())
        } else if (route is DeepLinkRoute.SettingsNotifications) {
            openScreen(SettingsScreen.newInstance(wallet))
        } else if (route is DeepLinkRoute.EditWalletLabel) {
            openScreen(EditNameScreen.newInstance(wallet))
        } else if (route is DeepLinkRoute.Camera) {
            openScreen(CameraScreen.newInstance())
        } else if (route is DeepLinkRoute.Receive) {
            openScreen(QRScreen.newInstance(wallet, TokenEntity.TON))
        } else if (route is DeepLinkRoute.ManageAssets) {
            openScreen(TokensManageScreen.newInstance(wallet))
        } else if (route is DeepLinkRoute.WalletPicker) {
            openScreen(PickerScreen.newInstance())
        } else {
            toast(Localization.invalid_link)
        }
    }

    private suspend fun processTransferDeepLink(wallet: WalletEntity, route: DeepLinkRoute.Transfer) {
        if (route.isExpired) {
            toast(Localization.expired_link)
            return
        }
        _eventFlow.tryEmit(RootEvent.Transfer(
            wallet = wallet,
            address = route.address,
            amount = route.amount,
            text = route.text,
            jettonAddress = route.jettonAddress,
        ))
    }

    private suspend fun processSignerDeepLink(route: DeepLinkRoute.Signer, fromQR: Boolean) {
        _eventFlow.tryEmit(RootEvent.Singer(
            publicKey = route.publicKey,
            name = route.name,
            qr = fromQR || !route.local
        ))
    }

    private suspend fun showTransaction(hash: String) {
        val wallet = selectedWalletFlow.firstOrNull() ?: return
        historyHelper.getEvent(wallet, hash)
            .filterIsInstance<HistoryItem.Event>()
            .firstOrNull()?.let {
                openScreen(TransactionScreen.newInstance(it))
            }
    }

    private suspend fun showTransaction(accountId: String, hash: String) {
        val wallet = accountRepository.getWalletByAccountId(accountId, false) ?: return
        val event = api.getTransactionEvents(wallet.accountId, wallet.testnet, hash) ?: return
        historyHelper.mapping(wallet, event)
            .find { it is HistoryItem.Event }?.let {
                openScreen(TransactionScreen.newInstance(it as HistoryItem.Event))
            }

    }
}