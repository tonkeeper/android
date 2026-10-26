package com.tonapps.tonkeeper.ui.base

import android.app.Application
import android.net.Uri
import android.webkit.WebResourceRequest
import androidx.annotation.LayoutRes
import androidx.core.net.toUri
import androidx.lifecycle.viewModelScope
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.bus.core.AnalyticsHelper
import com.tonapps.bus.generated.Events
import com.tonapps.core.deeplink.DeepLink
import com.tonapps.core.deeplink.DeepLinkRoute
import com.tonapps.extensions.appVersionName
import com.tonapps.extensions.bestMessage
import com.tonapps.extensions.currentTimeMillis
import com.tonapps.extensions.currentTimeSecondsInt
import com.tonapps.extensions.filterList
import com.tonapps.extensions.generateUuid
import com.tonapps.tonkeeper.extensions.normalizeTONSites
import com.tonapps.tonkeeper.extensions.toast
import com.tonapps.tonkeeper.extensions.tonConnectSignMessageMaxMessages
import com.tonapps.tonkeeper.helper.BrowserHelper
import com.tonapps.tonkeeper.manager.tonconnect.ConnectRequest
import com.tonapps.tonkeeper.manager.tonconnect.TonConnect
import com.tonapps.tonkeeper.manager.tonconnect.TonConnectManager
import com.tonapps.tonkeeper.manager.tonconnect.TonConnectSignMessageError
import com.tonapps.tonkeeper.manager.tonconnect.bridge.BridgeException
import com.tonapps.tonkeeper.manager.tonconnect.bridge.JsonBuilder
import com.tonapps.tonkeeper.manager.tonconnect.bridge.model.BridgeError
import com.tonapps.tonkeeper.manager.tonconnect.bridge.model.BridgeEvent
import com.tonapps.tonkeeper.manager.tonconnect.bridge.model.BridgeMethod
import com.tonapps.tonkeeper.manager.tonconnect.bridge.model.SignDataRequestPayload
import com.tonapps.tonkeeper.manager.tonconnect.signMessageRejection
import com.tonapps.tonkeeper.ui.component.TonConnectWebView
import com.tonapps.tonkeeper.ui.screen.root.RootViewModel
import com.tonapps.tonkeeper.ui.screen.send.transaction.SendTransactionScreen
import com.tonapps.tonkeeper.ui.screen.sign.SignDataScreen
import com.tonapps.tonkeeper.ui.screen.watchonly.WatchInfoScreen
import com.tonapps.wallet.api.API
import com.tonapps.wallet.data.core.entity.SignRequestEntity
import com.tonapps.wallet.data.dapps.entities.AppConnectEntity
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.activityViewModel
import uikit.extensions.activity
import java.util.concurrent.CancellationException

abstract class InjectedTonConnectScreen(@LayoutRes layoutId: Int, wallet: WalletEntity): WalletContextScreen(layoutId, wallet) {

    private val tonConnectManager: TonConnectManager by inject()
    private val api: API by inject()
    private val rootViewModel: RootViewModel by activityViewModel()

    abstract var webView: TonConnectWebView

    abstract val startUri: Uri

    protected open val isDAppBrowser: Boolean
        get() = false

    private val uri: Uri
        get() = webView.url?.toUri() ?: startUri

    val deviceInfo: JSONObject by lazy {
        JsonBuilder.device(
            maxMessages = wallet.maxMessages,
            appVersion = requireContext().appVersionName,
            signMessageMaxMessages = wallet.tonConnectSignMessageMaxMessages(),
        )
    }

    val installId: String
        get() = rootViewModel.installId

    var lastDeepLinkTime: Long = 0

    override val predictiveBackPreviewEnabled: Boolean
        get() = !webView.canGoBack()

    fun back() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            finish()
        }
    }

    override fun onBackPressed(): Boolean {
        back()
        return false
    }

    fun overrideUrlLoading(request: WebResourceRequest): Boolean {
        val refererUri = request.requestHeaders?.get("Referer")?.toUri()
        val url = DeepLink.fixBadUri(request.url).normalizeTONSites()
        val scheme = url.scheme ?: ""
        if (scheme == "https" && !DeepLinkRoute.isUniversalHost(url.host)) {
            return false
        }
        val deeplink = DeepLink(url, false, refererUri)
        if (deeplink.route is DeepLinkRoute.Unknown) {
            BrowserHelper.open(requireActivity(), url)
            return true
        }
        if (deeplink.route is DeepLinkRoute.Internal) {
            return true
        }
        val now = System.currentTimeMillis()
        if ((now - lastDeepLinkTime) > 1000) {
            lastDeepLinkTime = now
            processDeeplink(deeplink, url.toString())
        }
        return true
    }

    fun processDeeplink(deeplink: DeepLink, url: String) {
        when (deeplink.route) {
            is DeepLinkRoute.TonConnect -> rootViewModel.processTonConnectDeepLink(deeplink, null)
            is DeepLinkRoute.Unknown -> navigation?.openURL(url)
            else -> rootViewModel.processDeepLink(uri = url.toUri(), fromQR = false, refSource = deeplink.referrer, internal = false, fromPackageName = null, fromBrowser = isDAppBrowser)
        }
    }

    suspend fun tonapiFetch(
        url: String,
        options: String
    ) = api.tonapiFetch(url, options, wallet.network)

    suspend fun tonconnect(
        version: Int,
        request: ConnectRequest,
        forceConnect: Boolean = false
    ): JSONObject {
        if (version != 2) {
            return JsonBuilder.connectEventError(BridgeError.badRequest("Version $version is not supported"))
        }
        val activity = requireContext().activity ?: return JsonBuilder.connectEventError(BridgeError.unknown("internal client error"))
        if (tonConnectManager.isScam(requireContext(), wallet, request.manifestUrl.toUri(), webView.url!!.toUri(), startUri)) {
            return JsonBuilder.connectEventError(BridgeError.unknown("internal client error"))
        }

        return tonConnectManager.launchConnectFlow(
            activity = activity,
            tonConnect = TonConnect.fromJsInject(request, webView.url?.toUri()),
            wallet = wallet,
            forceConnect = forceConnect,
        )
    }

    private suspend fun tonconnectSignData(message: BridgeEvent.Message): JSONObject {
        try {
            val params = message.params.firstOrNull() ?: return JsonBuilder.responseError(message.id, BridgeError.methodNotSupported("Unknown params"))
            val payload = SignDataRequestPayload.parse(params) ?: return JsonBuilder.responseError(message.id, BridgeError.methodNotSupported("Unknown payload"))
            val proof = SignDataScreen.run(requireContext(), wallet, uri, payload)
            return JsonBuilder.responseSignData(message.id, proof, wallet.address, payload)
        } catch (e: CancellationException) {
            context?.let { tonConnectManager.showLogoutAppBar(wallet, it, uri) }
            return JsonBuilder.responseError(message.id, BridgeError.userDeclinedTransaction())
        } catch (e: Throwable) {
            return JsonBuilder.responseError(message.id, BridgeError.unknown(e.bestMessage))
        }
    }

    /**
     * TON Connect `signMessage` from the in-app browser: validates the request the way iOS does
     * (TK-4003), shows the sign sheet and replies with the signed internal message for the relayer.
     */
    private suspend fun tonconnectSignMessage(message: BridgeEvent.Message, showLogout: Boolean): JSONObject {
        if (message.params.size != 1) {
            return JsonBuilder.responseError(message.id, BridgeError.badRequest("Request contains excess messages. Required: 1, Provided: ${message.params.size}"))
        }
        fun reject(error: TonConnectSignMessageError): JSONObject {
            navigation?.toast(getString(error.toastRes))
            return JsonBuilder.responseError(message.id, error.bridgeError)
        }

        val request = try {
            SignRequestEntity.parse(message.params.first(), uri)
        } catch (e: Throwable) {
            null
        } ?: return reject(TonConnectSignMessageError.InvalidMessages)
        request.signMessageRejection(wallet, api)?.let { return reject(it) }
        return try {
            val internalBoc = SendTransactionScreen.runSignMessage(
                context = requireContext(),
                wallet = wallet,
                request = request,
                appName = uri.host ?: uri.toString(),
            )
            JsonBuilder.responseSignMessage(message.id, internalBoc)
        } catch (e: CancellationException) {
            if (showLogout) {
                context?.let { tonConnectManager.showLogoutAppBar(wallet, it, uri) }
            }
            JsonBuilder.responseError(message.id, BridgeError.userDeclinedTransaction())
        } catch (e: BridgeException) {
            JsonBuilder.responseError(message.id, BridgeError.badRequest(e.bestMessage))
        } catch (e: Throwable) {
            FirebaseCrashlytics.getInstance().recordException(e)
            JsonBuilder.responseError(message.id, BridgeError.unknown(e.bestMessage))
        }
    }

    suspend fun tonconnectSend(tx: JSONObject, showLogout: Boolean = true): JSONObject {
        var id = 0L
        try {
            val message = BridgeEvent.Message(tx)
            id = message.id
            if (tonConnectManager.getConnection(wallet.accountId, wallet.network, uri, AppConnectEntity.Type.Internal) == null) {
                return JsonBuilder.responseError(id, BridgeError.unknownApp())
            }
            if (wallet.isWatchOnly) {
                navigation?.add(WatchInfoScreen.newInstance(wallet))
                return JsonBuilder.responseError(id, BridgeError.userDeclinedTransaction())
            }
            if (message.method == BridgeMethod.SIGN_DATA) {
                return tonconnectSignData(message)
            } else if (message.method == BridgeMethod.SIGN_MESSAGE) {
                return tonconnectSignMessage(message, showLogout)
            } else if (message.method != BridgeMethod.SEND_TRANSACTION) {
                return JsonBuilder.responseError(id, BridgeError.methodNotSupported("Method \"${message.method}\" not supported."))
            }
            if (message.params.size != 1) {
                return JsonBuilder.responseError(id, BridgeError.badRequest("Request contains excess transactions. Required: 1, Provided: ${message.params.size}"))
            }
            val signRequest = SignRequestEntity.parse(message.params.first(), uri)
                ?: return JsonBuilder.responseError(id, BridgeError.badRequest("Failed to parse message params"))
            val operationId = generateUuid()
            val startedAtMs = currentTimeMillis()
            AnalyticsHelper.Default.events.redOperations.opAttempt(
                operationId = operationId,
                flow = Events.RedOperations.RedOperationsFlow.TonConnect,
                operation = Events.RedOperations.RedOperationsOperation.ConfirmTransaction,
                attemptSource = null,
                startedAtMs = currentTimeSecondsInt(),
                otherMetadata = null,
            )
            return try {
                val boc = SendTransactionScreen.run(
                    requireContext(), wallet, signRequest,
                    sendNativeFrom = Events.SendNative.SendNativeFrom.TonconnectLocal
                )
                val finishedAtMs = currentTimeMillis()
                AnalyticsHelper.Default.events.redOperations.opTerminal(
                    operationId = operationId,
                    flow = Events.RedOperations.RedOperationsFlow.TonConnect,
                    operation = Events.RedOperations.RedOperationsOperation.ConfirmTransaction,
                    outcome = Events.RedOperations.RedOperationsOutcome.Success,
                    durationMs = (finishedAtMs - startedAtMs).toDouble(),
                    finishedAtMs = currentTimeSecondsInt(),
                    errorCode = null,
                    errorMessage = null,
                    errorType = null,
                    stage = null,
                    otherMetadata = null,
                )
                JsonBuilder.responseSendTransaction(id, boc)
            } catch (e: CancellationException) {
                val finishedAtMs = currentTimeMillis()
                AnalyticsHelper.Default.events.redOperations.opTerminal(
                    operationId = operationId,
                    flow = Events.RedOperations.RedOperationsFlow.TonConnect,
                    operation = Events.RedOperations.RedOperationsOperation.ConfirmTransaction,
                    outcome = Events.RedOperations.RedOperationsOutcome.Cancel,
                    durationMs = (finishedAtMs - startedAtMs).toDouble(),
                    finishedAtMs = currentTimeSecondsInt(),
                    errorCode = null,
                    errorMessage = null,
                    errorType = null,
                    stage = null,
                    otherMetadata = null,
                )
                if (showLogout) {
                    context?.let { tonConnectManager.showLogoutAppBar(wallet, it, uri) }
                }
                JsonBuilder.responseError(id, BridgeError.userDeclinedTransaction())
            } catch (e: BridgeException) {
                val finishedAtMs = currentTimeMillis()
                AnalyticsHelper.Default.events.redOperations.opTerminal(
                    operationId = operationId,
                    flow = Events.RedOperations.RedOperationsFlow.TonConnect,
                    operation = Events.RedOperations.RedOperationsOperation.ConfirmTransaction,
                    outcome = Events.RedOperations.RedOperationsOutcome.Fail,
                    durationMs = (finishedAtMs - startedAtMs).toDouble(),
                    finishedAtMs = currentTimeSecondsInt(),
                    errorCode = null,
                    errorMessage = e.bestMessage,
                    errorType = e.javaClass.simpleName,
                    stage = null,
                    otherMetadata = null,
                )
                JsonBuilder.responseError(id, BridgeError.badRequest(e.bestMessage))
            } catch (e: Throwable) {
                val finishedAtMs = currentTimeMillis()
                AnalyticsHelper.Default.events.redOperations.opTerminal(
                    operationId = operationId,
                    flow = Events.RedOperations.RedOperationsFlow.TonConnect,
                    operation = Events.RedOperations.RedOperationsOperation.ConfirmTransaction,
                    outcome = Events.RedOperations.RedOperationsOutcome.Fail,
                    durationMs = (finishedAtMs - startedAtMs).toDouble(),
                    finishedAtMs = currentTimeSecondsInt(),
                    errorCode = null,
                    errorMessage = e.bestMessage,
                    errorType = e.javaClass.simpleName,
                    stage = null,
                    otherMetadata = null,
                )
                FirebaseCrashlytics.getInstance().recordException(e)
                JsonBuilder.responseError(id, BridgeError.unknown(e.bestMessage))
            }
        } catch (e: Throwable) {
            navigation?.toast(e.bestMessage)
            FirebaseCrashlytics.getInstance().recordException(e)
            return JsonBuilder.responseError(id, BridgeError.unknown(e.bestMessage))
        }
    }

    abstract class ViewModel(
        app: Application,
        private val wallet: WalletEntity,
        private val tonConnectManager: TonConnectManager
    ): BaseWalletVM(app) {

        abstract val url: Uri

        val connectionFlow = tonConnectManager.walletConnectionsFlow(wallet).filterList { connection ->
            connection.type == AppConnectEntity.Type.Internal && connection.appUrl.host == url.host
        }.map {
            it.firstOrNull()
        }

        fun disconnect() {
            viewModelScope.launch {
                // The repository matches by host + path segments, so the resolved appUrl only
                // short-circuits that lookup.
                val connection = withTimeoutOrNull(CONNECTION_LOOKUP_TIMEOUT_MS) {
                    connectionFlow.firstOrNull()
                }
                tonConnectManager.disconnect(
                    wallet,
                    connection?.appUrl ?: url,
                    AppConnectEntity.Type.Internal
                )
            }
        }

        suspend fun restoreConnection(currentUri: Uri?): JSONObject {
            val connection = loadConnection(currentUri = currentUri)
            return if (connection == null) {
                JsonBuilder.connectEventError(BridgeError.unknownApp())
            } else {
                JsonBuilder.connectEventSuccess(wallet, null, null, context.appVersionName)
            }
        }

        private suspend fun loadConnection(attempt: Int = 0, currentUri: Uri?): AppConnectEntity? {
            if (attempt > 3) {
                val firstApp = tonConnectManager.getConnection(wallet.accountId, wallet.network, url, AppConnectEntity.Type.Internal)
                if (firstApp != null) {
                    return firstApp
                }
                if (currentUri != null) {
                    return tonConnectManager.getConnection(wallet.accountId, wallet.network, currentUri, AppConnectEntity.Type.Internal)
                }
                return null
            }
            val connection = connectionFlow.firstOrNull()
            if (connection != null) {
                return connection
            }
            delay(140)
            return loadConnection(attempt + 1, currentUri)
        }

        private companion object {
            private const val CONNECTION_LOOKUP_TIMEOUT_MS = 500L
        }
    }

}