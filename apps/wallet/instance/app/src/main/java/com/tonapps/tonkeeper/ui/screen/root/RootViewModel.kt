package com.tonapps.tonkeeper.ui.screen.root

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.lifecycle.viewModelScope
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.crashlytics.setCustomKeys
import com.google.firebase.ktx.Firebase
import com.tonapps.blockchain.ton.TonNetwork
import com.tonapps.extensions.MutableEffectFlow
import com.tonapps.extensions.getQueryLong
import com.tonapps.extensions.setLocales
import com.tonapps.ledger.ton.LedgerConnectData
import com.tonapps.tonkeeper.core.AnalyticsHelper
import com.tonapps.tonkeeper.core.deeplink.DeepLink
import com.tonapps.tonkeeper.core.entities.WalletPurchaseMethodEntity
import com.tonapps.tonkeeper.core.history.HistoryHelper
import com.tonapps.tonkeeper.core.history.list.item.HistoryItem
import com.tonapps.tonkeeper.core.signer.SingerArgs
import com.tonapps.tonkeeper.core.widget.Widget
import com.tonapps.tonkeeper.helper.ShortcutHelper
import com.tonapps.tonkeeper.manager.push.FirebasePush
import com.tonapps.tonkeeper.manager.tonconnect.TonConnectManager
import com.tonapps.tonkeeper.manager.tonconnect.bridge.model.BridgeError
import com.tonapps.tonkeeper.manager.tonconnect.bridge.model.BridgeEvent
import com.tonapps.tonkeeper.ui.base.BaseWalletVM
import com.tonapps.tonkeeper.ui.screen.init.list.AccountItem
import com.tonapps.tonkeeper.ui.screen.main.MainScreen
import com.tonapps.tonkeeper.ui.screen.send.transaction.SendTransactionScreen
import com.tonapps.tonkeeper.ui.screen.wallet.main.WalletViewModel.Companion.getWalletScreen
import com.tonapps.tonkeeper.ui.screen.wallet.main.list.Item
import com.tonapps.tonkeeper.ui.screen.wallet.main.list.WalletAdapter
import com.tonapps.tonkeeperx.R
import com.tonapps.wallet.api.API
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.core.ScreenCacheSource
import com.tonapps.wallet.data.core.Theme
import com.tonapps.wallet.data.core.entity.SignRequestEntity
import com.tonapps.wallet.data.dapps.entities.AppConnectEntity
import com.tonapps.wallet.data.passcode.PasscodeManager
import com.tonapps.wallet.data.purchase.PurchaseRepository
import com.tonapps.wallet.data.settings.SettingsRepository
import com.tonapps.wallet.localization.Localization
import kotlinx.coroutines.Dispatchers
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uikit.extensions.collectFlow

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
): BaseWalletVM(app) {

    data class Passcode(
        val show: Boolean,
        val biometric: Boolean,
    )

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

    fun processDeepLink(
        uri: Uri,
        fromQR: Boolean,
        refSource: Uri?
    ): Boolean {
        if (TonConnectManager.isTonConnectDeepLink(uri)) {
            return tonConnectManager.processDeeplink(context, uri, fromQR, refSource)
        } else if (DeepLink.isSupportedUri(uri)) {
            resolveDeepLink(uri, fromQR, refSource)
            return true
        }
        return false
    }

    private fun resolveDeepLink(
        uri: Uri,
        fromQR: Boolean,
        refSource: Uri?
    ) {
        if (uri.host == "signer") {
            collectFlow(hasWalletFlow.take(1)) {
                delay(1000)
                resolveSignerLink(uri, fromQR)
            }
        } else {
            collectFlow(accountRepository.selectedWalletFlow.take(1)) { wallet ->
                resolveOther(refSource, uri, wallet)
            }
        }
    }

    private fun resolveSignerLink(uri: Uri, fromQR: Boolean) {
        try {
            val args = SingerArgs(uri)
            _eventFlow.tryEmit(RootEvent.Singer(args.publicKeyEd25519, args.name, fromQR))
        } catch (e: Throwable) {
            toast(Localization.invalid_link)
        }
    }

    private fun resolveOther(
        refSource: Uri?,
        _uri: Uri,
        wallet: WalletEntity
    ) {
        val url = _uri.toString().replace("ton://", "https://app.tonkeeper.com/").replace("tonkeeper://", "https://app.tonkeeper.com/")
        val uri = Uri.parse(url)
        val path = uri.path

        if (MainScreen.isSupportedDeepLink(url) || MainScreen.isSupportedDeepLink(_uri.toString())) {
            _eventFlow.tryEmit(RootEvent.OpenTab(_uri.toString(), wallet))
        } else if (path?.startsWith("/send") == true) {
            _eventFlow.tryEmit(RootEvent.OpenSend(wallet))
        } else if (path?.startsWith("/staking") == true) {
            _eventFlow.tryEmit(RootEvent.Staking(wallet))
        } else if (path?.startsWith("/pool/") == true) {
            _eventFlow.tryEmit(RootEvent.StakingPool(wallet, uri.pathSegments.last()))
        } else if (path?.startsWith("/action/") == true) {
            val actionId = uri.pathSegments.last()
            val accountAddress = uri.getQueryParameter("account")
            if (accountAddress == null) {
                showTransaction(actionId)
            } else {
                showTransaction(accountAddress, actionId)
            }
        } else if (path?.startsWith("/transfer/") == true) {
            val exp = uri.getQueryLong("exp") ?: 0
            if (exp > 0 && exp < System.currentTimeMillis() / 1000) {
                toast(Localization.transaction_expired)
                return
            }

            _eventFlow.tryEmit(RootEvent.Transfer(
                wallet = wallet,
                address = uri.pathSegments.last(),
                amount = uri.getQueryParameter("amount"),
                text = uri.getQueryParameter("text"),
                jettonAddress = uri.getQueryParameter("jetton"),
            ))
        } else if (path?.startsWith("/action/") == true) {
            val account = uri.getQueryParameter("account") ?: return
            val hash = uri.pathSegments.lastOrNull() ?: return
            showTransaction(account, hash)
        } else if (path?.startsWith("/pick/") == true) {
            val walletId = uri.pathSegments.lastOrNull() ?: return
            viewModelScope.launch { accountRepository.setSelectedWallet(walletId) }
        } else if (path?.startsWith("/swap") == true) {
            val ft = uri.getQueryParameter("ft") ?: "TON"
            val tt = uri.getQueryParameter("tt")
            _eventFlow.tryEmit(RootEvent.Swap(wallet, api.config.swapUri, wallet.address, ft, tt))
        } else if (path?.startsWith("/battery") == true) {
            val promocode = uri.getQueryParameter("promocode")
            _eventFlow.tryEmit(RootEvent.Battery(wallet, promocode))
        } else if (path?.startsWith("/buy-ton") == true || uri.path == "/exchange" || uri.path == "/exchange/") {
            _eventFlow.tryEmit(RootEvent.BuyOrSell(wallet))
        } else if (path?.startsWith("/exchange") == true) {
            val name = uri.pathSegments.lastOrNull() ?: return
            val method = purchaseRepository.getMethod(name, wallet.testnet, settingsRepository.getLocale())
            val methodWrapped: WalletPurchaseMethodEntity? = if (method != null) {
                WalletPurchaseMethodEntity(
                    method = method,
                    wallet = wallet,
                    currency = settingsRepository.currency.code,
                    config = api.config
                )
            } else {
                null
            }
            _eventFlow.tryEmit(RootEvent.BuyOrSell(wallet, methodWrapped))
        } else if (path?.startsWith("/backups") == true) {
            _eventFlow.tryEmit(RootEvent.OpenBackups(wallet))
        } else if (path?.startsWith("/dapp") == true) {
            val dAppUrl = uri.toString().replace("https://app.tonkeeper.com/dapp/", "https://")
            _eventFlow.tryEmit(RootEvent.Browser(wallet, Uri.parse(dAppUrl)))
        } else {
            toast(Localization.invalid_link)
        }
    }

    private fun showTransaction(hash: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val wallet = selectedWalletFlow.firstOrNull() ?: return@launch
            val item = historyHelper.getEvent(wallet, hash).filterIsInstance<HistoryItem.Event>().firstOrNull() ?: return@launch
            _eventFlow.tryEmit(RootEvent.Transaction(item))
        }
    }

    private fun showTransaction(accountId: String, hash: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val wallet = accountRepository.getWalletByAccountId(accountId, false) ?: return@launch
            val event = api.getTransactionEvents(wallet.accountId, wallet.testnet, hash) ?: return@launch
            val item = historyHelper.mapping(wallet, event).find { it is HistoryItem.Event } as? HistoryItem.Event ?: return@launch
            _eventFlow.tryEmit(RootEvent.Transaction(item))
        }
    }

    private fun toast(resId: Int) {
        _eventFlow.tryEmit(RootEvent.Toast(resId))
    }
}