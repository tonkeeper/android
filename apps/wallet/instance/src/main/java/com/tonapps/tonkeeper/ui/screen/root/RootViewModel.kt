package com.tonapps.tonkeeper.ui.screen.root

import android.app.Application
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tonapps.blockchain.Coin
import com.tonapps.extensions.getQueryLong
import com.tonapps.tonkeeper.core.deeplink.DeepLink
import com.tonapps.tonkeeper.core.history.HistoryHelper
import com.tonapps.tonkeeper.core.history.list.item.HistoryItem
import com.tonapps.tonkeeper.core.signer.SingerArgs
import com.tonapps.tonkeeper.password.PasscodeRepository
import com.tonapps.wallet.data.push.GooglePushService
import com.tonapps.wallet.data.push.PushManager
import com.tonapps.tonkeeper.sign.SignManager
import com.tonapps.tonkeeper.sign.SignRequestEntity
import com.tonapps.tonkeeper.ui.screen.main.MainScreen
import com.tonapps.wallet.api.API
import com.tonapps.wallet.data.account.WalletRepository
import com.tonapps.wallet.data.account.WalletSource
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.settings.SettingsRepository
import com.tonapps.wallet.data.tonconnect.TonConnectRepository
import com.tonapps.wallet.data.tonconnect.entities.DAppEntity
import com.tonapps.wallet.data.tonconnect.entities.DAppRequestEntity
import com.tonapps.wallet.localization.Localization
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import uikit.navigation.Navigation.Companion.navigation

class RootViewModel(
    application: Application,
    private val passcodeRepository: PasscodeRepository,
    private val settingsRepository: SettingsRepository,
    private val walletRepository: WalletRepository,
    private val signManager: SignManager,
    private val tonConnectRepository: TonConnectRepository,
    private val api: API,
    private val historyHelper: HistoryHelper,
): AndroidViewModel(application) {

    val hasWalletFlow = walletRepository.walletsFlow.map { it.isNotEmpty() }.distinctUntilChanged()

    private val _eventFlow = MutableSharedFlow<RootEvent>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val eventFlow = _eventFlow.asSharedFlow().filterNotNull()

    private val _lockFlow = MutableStateFlow<Boolean?>(null)
    val lockFlow = _lockFlow.asStateFlow().filterNotNull()

    val themeId: Int
        get() {
            return if (settingsRepository.theme == "dark") {
                uikit.R.style.Theme_App_Dark
            } else {
                uikit.R.style.Theme_App_Blue
            }
        }

    val themeFlow: Flow<Int> = settingsRepository.themeFlow.map {
        if (it == "dark") {
            uikit.R.style.Theme_App_Dark
        } else {
            uikit.R.style.Theme_App_Blue
        }
    }.drop(1)

    val tonConnectEventsFlow = tonConnectRepository.eventsFlow

    init {
        _lockFlow.value = settingsRepository.lockScreen && passcodeRepository.hasPinCode

        viewModelScope.launch(Dispatchers.IO) {
            settingsRepository.firebaseToken = GooglePushService.requestToken()
        }
    }

    fun signOut() {
        viewModelScope.launch {
            walletRepository.clear()
            passcodeRepository.clear()
            _lockFlow.value = false
        }
    }

    suspend fun tonconnectReject(requestId: String, app: DAppEntity) {
        tonConnectRepository.sendError(requestId, app, 300, "Reject Request")
    }

    suspend fun tonconnectBoc(
        requestId: String,
        app: DAppEntity,
        boc: String
    ) {
        tonConnectRepository.send(requestId, app, boc)
    }

    suspend fun requestSign(
        context: Context,
        request: SignRequestEntity
    ): String {
        val wallet = walletRepository.activeWalletFlow.firstOrNull() ?: throw Exception("wallet is null")
        return requestSign(context, wallet, request)
    }

    suspend fun requestSign(
        context: Context,
        wallet: WalletEntity,
        request: SignRequestEntity
    ): String {
        val navigation = context.navigation ?: throw Exception("navigation is null")
        return signManager.action(navigation, wallet, request)
    }

    fun checkPasscode(code: String): Flow<Unit> = flow {
        val valid = passcodeRepository.compare(code)
        if (valid) {
            _lockFlow.value = false
            emit(Unit)
        } else {
            throw Exception("invalid passcode")
        }
    }.take(1)

    fun processDeepLink(uri: Uri, fromQR: Boolean): Boolean {
        if (DeepLink.isSupportedUri(uri)) {
            resolveDeepLink(uri, fromQR)
            return true
        }
        return false
    }

    private fun resolveDeepLink(uri: Uri, fromQR: Boolean) {
        if (uri.host == "signer") {
            resolveSignerLink(uri, fromQR)
            return
        }
        walletRepository.activeWalletFlow.take(1).onEach { wallet ->
            resolveOther(uri, wallet)
        }.launchIn(viewModelScope)
    }

    private fun resolveSignerLink(uri: Uri, fromQR: Boolean) {
        try {
            val args = SingerArgs(uri)
            val walletSource = if (fromQR) {
                WalletSource.SingerQR
            } else {
                WalletSource.SingerApp
            }
            _eventFlow.tryEmit(RootEvent.Singer(args.publicKeyEd25519, args.name, walletSource))
        } catch (e: Throwable) {
            toast(Localization.invalid_link)
        }
    }

    private fun resolveOther(_uri: Uri, wallet: WalletEntity) {
        val url = _uri.toString().replace("ton://", "https://app.tonkeeper.com/").replace("tonkeeper://", "https://app.tonkeeper.com/")
        val uri = Uri.parse(url)
        if (DeepLink.isTonConnectUri(uri)) {
            resolveTonConnect(uri, wallet)
        } else if (MainScreen.isSupportedDeepLink(url) || MainScreen.isSupportedDeepLink(_uri.toString())) {
            _eventFlow.tryEmit(RootEvent.OpenTab(_uri.toString()))
        } else if (uri.path?.startsWith("/transfer/") == true) {
            _eventFlow.tryEmit(RootEvent.Transfer(
                address = uri.pathSegments.last(),
                amount = uri.getQueryLong("amount")?.let { Coin.toCoins(it) },
                text = uri.getQueryParameter("text"),
                jettonAddress = uri.getQueryParameter("jetton"),
            ))
        } else if (uri.path?.startsWith("/action/") == true) {
            val account = uri.getQueryParameter("account") ?: return
            val hash = uri.pathSegments.lastOrNull() ?: return
            showTransaction(account, hash)
        } else {
            Log.d("DeepLinkLog", "uri: $uri")
            Log.d("DeepLinkLog", "path segments: ${uri.pathSegments}")
            toast(Localization.invalid_link)
        }
    }

    private fun showTransaction(accountId: String, hash: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val wallet = walletRepository.getWallet(accountId) ?: return@launch
            val event = api.getTransactionEvents(wallet.accountId, wallet.testnet, hash) ?: return@launch
            val item = historyHelper.mapping(wallet, event).find { it is HistoryItem.Event } as? HistoryItem.Event ?: return@launch
            _eventFlow.tryEmit(RootEvent.Transaction(item))
        }
    }

    private fun resolveTonConnect(
        uri: Uri,
        wallet: WalletEntity
    ) {
        try {
            if (!wallet.hasPrivateKey) {
                toast(Localization.not_supported)
                return
            }
            val request = DAppRequestEntity(uri)
            _eventFlow.tryEmit(RootEvent.TonConnect(request))
        } catch (e: Throwable) {
            toast(Localization.invalid_link)
        }
    }

    private fun toast(resId: Int) {
        _eventFlow.tryEmit(RootEvent.Toast(resId))
    }
}