package com.tonapps.tonkeeper.manager.tonconnect

import android.content.Context
import android.net.Uri
import android.util.Base64
import android.view.View
import androidx.collection.ArrayMap
import androidx.core.net.toUri
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.tonapps.blockchain.ton.TonNetwork
import com.tonapps.blockchain.ton.connect.TONProof
import com.tonapps.blockchain.ton.extensions.equalsAddress
import com.tonapps.extensions.appVersionName
import com.tonapps.extensions.bestMessage
import com.tonapps.extensions.filterList
import com.tonapps.extensions.flatter
import com.tonapps.extensions.hasQuery
import com.tonapps.extensions.isEmptyQuery
import com.tonapps.extensions.mapList
import com.tonapps.network.OkHttpError
import com.tonapps.network.simple
import com.tonapps.security.CryptoBox
import com.tonapps.tonkeeper.client.safemode.SafeModeClient
import com.tonapps.tonkeeper.core.DevSettings
import com.tonapps.tonkeeper.core.FirebaseHelper
import com.tonapps.tonkeeper.extensions.isSafeModeEnabled
import com.tonapps.tonkeeper.extensions.showToast
import com.tonapps.tonkeeper.manager.push.PushManager
import com.tonapps.tonkeeper.manager.tonconnect.bridge.Bridge
import com.tonapps.tonkeeper.manager.tonconnect.bridge.JsonBuilder
import com.tonapps.tonkeeper.manager.tonconnect.bridge.model.BridgeError
import com.tonapps.tonkeeper.manager.tonconnect.bridge.model.BridgeEvent
import com.tonapps.tonkeeper.manager.tonconnect.bridge.model.BridgeMethod
import com.tonapps.tonkeeper.manager.tonconnect.bridge.model.SignDataRequestPayload
import com.tonapps.tonkeeper.manager.tonconnect.exceptions.ManifestException
import com.tonapps.tonkeeper.ui.component.SnackBarView
import com.tonapps.tonkeeper.ui.screen.browser.dapp.DAppBridge
import com.tonapps.tonkeeper.ui.screen.tonconnect.TonConnectResponse
import com.tonapps.tonkeeper.ui.screen.tonconnect.TonConnectSafeModeDialog
import com.tonapps.tonkeeper.ui.screen.tonconnect.TonConnectScreen
import com.tonapps.tonkeeper.worker.DAppPushToggleWorker
import com.tonapps.wallet.api.API
import com.tonapps.wallet.api.readBody
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.wallet.data.dapps.DAppsRepository
import com.tonapps.wallet.data.dapps.entities.AppConnectEntity
import com.tonapps.wallet.data.dapps.entities.AppEntity
import com.tonapps.wallet.data.settings.SettingsRepository
import com.tonapps.wallet.localization.Localization
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import uikit.extensions.activity
import uikit.extensions.addForResult
import uikit.navigation.NavigationActivity
import java.util.concurrent.CancellationException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class TonConnectManager(
    private val scope: CoroutineScope,
    private val api: API,
    private val accountRepository: AccountRepository,
    private val dAppsRepository: DAppsRepository,
    private val pushManager: PushManager,
    private val safeModeClient: SafeModeClient,
    private val settingsRepository: SettingsRepository,
) : ITonConnectBridge {

    private val bridge: Bridge = Bridge(api)
    private var bridgeJob: Job? = null
    private val recentlyConnectedClients = ConcurrentHashMap<String, Long>()

    private val _eventsFlow = MutableSharedFlow<BridgeEvent>(replay = 1)
    private val eventsFlow = _eventsFlow.asSharedFlow().mapNotNull { event ->
        val lastAppRequestId = dAppsRepository.getLastAppRequestId(event.connection.clientId)
        if (lastAppRequestId >= event.message.id) {
            DevSettings.tonConnectLog("Last app event id: $lastAppRequestId\nIgnore event: $event", error = true)
            return@mapNotNull null
        }
        event
    }.shareIn(scope, SharingStarted.Eagerly, 1)

    override val transactionRequestFlow = eventsFlow.mapNotNull { event ->
        if (event.method == BridgeMethod.SEND_TRANSACTION) {
            val connectTime = recentlyConnectedClients[event.connection.clientId] ?: 0L
            val timeSinceConnect = System.currentTimeMillis() - connectTime
            if (timeSinceConnect < THROTTLE_DELAY_MS) {
                delay(THROTTLE_DELAY_MS)
            }
            Pair(event.connection, event.message)
        } else {
            if (event.method == BridgeMethod.DISCONNECT) {
                deleteConnection(event.connection, event.message.id)
            } else if (event.method != BridgeMethod.SIGN_DATA) {
                sendBridgeError(event.connection, BridgeError.methodNotSupported("Method \"${event.method}\" not supported"), event.message.id)
            }
            null
        }
    }.flowOn(Dispatchers.IO).shareIn(scope, SharingStarted.Eagerly, 0)

    override val signDataRequestFlow = eventsFlow
        .filter { it.method == BridgeMethod.SIGN_DATA }
        .map { it }
        .shareIn(scope, SharingStarted.Eagerly, 0)

    private val mutex = ReentrantLock()

    override fun connectBridge() {
        mutex.withLock {
            if (bridgeJob?.isActive == true) {
                return
            }

            startBridge()
        }
    }

    override fun disconnectBridge() {
        mutex.withLock {
            bridgeJob?.cancel()
            bridgeJob = null
        }
    }

    override fun reconnectBridge() {
        mutex.withLock {
            if (bridgeJob != null) {
                bridgeJob?.cancel()
                startBridge()
            }
        }
    }

    private fun startBridge() {
        bridgeJob = scope.launch(Dispatchers.IO) {
            val connections = dAppsRepository.getConnections().chunked(50)
            if (connections.isEmpty()) {
                return@launch
            }

            val flow = connections.map {
                bridge.eventsFlow(it, dAppsRepository.lastEventId)
            }.flatter()

            flow.collect {
                _eventsFlow.emit(it)
            }
        }
    }

    override fun walletConnectionsFlow(wallet: WalletEntity) = accountConnectionsFlow(wallet.accountId, wallet.network)

    fun accountConnectionsFlow(accountId: String, network: TonNetwork = TonNetwork.MAINNET) = dAppsRepository.connectionsFlow.filterList { connection ->
        connection.network == network && connection.accountId.equalsAddress(accountId)
    }

    override fun setLastAppRequestId(clientId: String, messageId: Long) {
        dAppsRepository.setLastAppRequestId(clientId, messageId)
    }

    override fun walletAppsFlow(wallet: WalletEntity) = walletConnectionsFlow(wallet).mapList { it.appUrl }.map { it.distinct() }.map { urls ->
        dAppsRepository.getApps(urls)
    }.flowOn(Dispatchers.IO)

    private suspend fun deleteConnection(connection: AppConnectEntity, messageId: Long) {
        val deleted = dAppsRepository.deleteConnect(connection)
        if (!deleted) {
            sendBridgeError(connection, BridgeError.unknownApp("App not found. Request user to reconnect wallet for access restoration."), messageId)
        } else {
            bridge.sendDisconnectResponseSuccess(connection, messageId)
        }

        accountRepository.getWalletByAccountId(connection.accountId, connection.network)?.let {
            pushManager.dAppUnsubscribe(it, listOf(connection))
        }
    }

    override suspend fun getConnection(
        accountId: String,
        network: TonNetwork,
        appUrl: Uri,
        type: AppConnectEntity.Type
    ): AppConnectEntity? {
        val apps = dAppsRepository.getConnections(accountId, network)
        if (apps.isEmpty()) {
            return null
        }
        val connections = apps.filter {
            it.key.url == appUrl || it.key.url.host == appUrl.host
        }.values.flatten()
        for (connection in connections) {
            if (connection.type == type) {
                return connection
            }
        }
        return null
    }

    override fun disconnect(wallet: WalletEntity, appUrl: Uri, type: AppConnectEntity.Type?) {
        scope.launch(Dispatchers.IO) {
            val connections = dAppsRepository.deleteApp(wallet.accountId, wallet.network, appUrl, type)
            if (connections.isNotEmpty()) {
                for (connection in connections) {
                    bridge.sendDisconnect(connection)
                }
                pushManager.dAppUnsubscribe(wallet, connections)
            }
            withContext(Dispatchers.Main.immediate) {
                reconnectBridge()
            }
        }
    }

    override suspend fun clear(wallet: WalletEntity) {
        withContext(Dispatchers.IO) {
            val connections = dAppsRepository.deleteApps(wallet.accountId, wallet.network)
            for (connection in connections) {
                bridge.sendDisconnect(connection)
            }
            pushManager.dAppUnsubscribe(wallet, connections)
        }
    }

    override suspend fun sendBridgeError(connection: AppConnectEntity, error: BridgeError, id: Long) {
        bridge.sendError(connection, error, id)
        setLastAppRequestId(connection.clientId, id)
    }

    override suspend fun sendTransactionResponseSuccess(connection: AppConnectEntity, boc: String, id: Long) {
        bridge.sendTransactionResponseSuccess(connection, boc, id)
        setLastAppRequestId(connection.clientId, id)
    }

    override suspend fun sendSignDataResponseSuccess(connection: AppConnectEntity, proof: TONProof.Result, address: String, payload: SignDataRequestPayload, id: Long) {
        bridge.sendSignDataResponseSuccess(connection, proof, address, payload, id)
        setLastAppRequestId(connection.clientId, id)
    }

    override fun isPushEnabled(wallet: WalletEntity, appUrl: Uri): Boolean {
        return dAppsRepository.isPushEnabled(wallet.accountId, wallet.network, appUrl)
    }

    private suspend fun newConnect(
        wallet: WalletEntity,
        keyPair: CryptoBox.KeyPair,
        clientId: String,
        appUrl: Uri,
        proof: TONProof.Result?,
        pushEnabled: Boolean,
        type: AppConnectEntity.Type
    ): AppConnectEntity = withContext(Dispatchers.IO) {
        val timestamp = proof?.timestamp ?: (System.currentTimeMillis() / 1000L)
        val connection = AppConnectEntity(
            accountId = wallet.accountId,
            network = wallet.network,
            clientId = clientId,
            type = type,
            appUrl = appUrl,
            keyPair = keyPair,
            proofSignature = proof?.signature,
            timestamp = timestamp,
            proofPayload = proof?.payload,
            pushEnabled = pushEnabled
        )
        if (!dAppsRepository.newConnect(connection)) {
            throw Exception("Failed to save connection")
        }
        connection
    }

    override fun processDeeplink(
        context: Context,
        uri: Uri,
        fromQR: Boolean,
        refSource: Uri?,
        fromPackageName: String?
    ): Uri? {
        val returnUri = TonConnect.parseReturn(uri.getQueryParameter("ret"), refSource)
        try {
            val activity = context.activity ?: throw IllegalArgumentException("Context must be an Activity")
            val normalizedUri = normalizeUri(uri)
            val tonConnect = TonConnect.parse(
                uri = normalizedUri,
                refSource = refSource,
                fromQR = fromQR,
                returnUri = returnUri,
                fromPackageName = fromPackageName
            )

            scope.launch {
                if (!isScam(context, WalletEntity.EMPTY, uri, normalizedUri, tonConnect.manifestUrl.toUri())) {
                    connectRemoteApp(activity, tonConnect)
                }
            }
            return null
        } catch (e: Exception) {
            if (uri.isEmptyQuery || uri.hasQuery("open") || uri.hasQuery("ret")) {
                return returnUri
            } else {
                context.showToast(Localization.invalid_link)
                return null
            }
        }
    }

    private suspend fun connectRemoteApp(activity: NavigationActivity, tonConnect: TonConnect) {
        val keyPair = CryptoBox.keyPair()
        val message = launchConnectFlow(activity, tonConnect, keyPair, null, false)
        bridge.send(tonConnect.clientId, keyPair, message.toString())
    }

    override suspend fun launchConnectFlow(
        activity: NavigationActivity,
        tonConnect: TonConnect,
        keyPair: CryptoBox.KeyPair,
        wallet: WalletEntity?,
        forceConnect: Boolean
    ): JSONObject = withContext(Dispatchers.IO) {
        if (tonConnect.request.items.isEmpty()) {
            return@withContext JsonBuilder.connectEventError(BridgeError.badRequest("Empty value provided in required field \"items\""))
        }

        val clientId = tonConnect.clientId
        var appUrl = Uri.EMPTY
        try {
            val app = readManifest(tonConnect.manifestUrl)
            appUrl = app.url
            if (isScam(activity, wallet ?: WalletEntity.EMPTY, app.iconUrl.toUri(), app.url)) {
                return@withContext JsonBuilder.connectEventError(BridgeError.badRequest("client error"))
            }

            val response = if (forceConnect && wallet != null) {
                TonConnectResponse(
                    notifications = true,
                    proof = null,
                    wallet = wallet,
                    proofError = null
                )
            } else {
                val screen = TonConnectScreen.newInstance(
                    app = app,
                    proofPayload = tonConnect.proofPayload,
                    returnUri = tonConnect.returnUri,
                    wallet = wallet,
                    fromPackageName = tonConnect.fromPackageName
                )
                val bundle = activity.addForResult(screen)
                screen.contract.parseResult(bundle)
            }

            newConnect(
                wallet = response.wallet,
                keyPair = keyPair,
                clientId = clientId,
                appUrl = app.url,
                proof = response.proof,
                pushEnabled = response.notifications,
                type = if (tonConnect.jsInject) AppConnectEntity.Type.Internal else AppConnectEntity.Type.External
            )

            recentlyConnectedClients[clientId] = System.currentTimeMillis()

            withContext(Dispatchers.Main.immediate) {
                reconnectBridge()

                DAppPushToggleWorker.run(
                    context = activity,
                    wallet = response.wallet,
                    appUrl = app.url,
                    enable = response.notifications
                )
            }

            JsonBuilder.connectEventSuccess(
                wallet = response.wallet,
                proof = response.proof,
                proofError = response.proofError,
                activity.appVersionName
            )
        } catch (e: CancellationException) {
            wallet?.let { showLogoutAppBar(it, activity, appUrl) }
            JsonBuilder.connectEventError(BridgeError.userDeclinedTransaction())
        } catch (e: ManifestException) {
            FirebaseHelper.manifestFetchFailed(
                kind = if (e is ManifestException.NotFound) "not_found" else "content_error",
                url = tonConnect.manifestUrl,
            )
            withContext(Dispatchers.Main) { activity.showToast(Localization.dapp_manifest_error) }
            if (e is ManifestException.NotFound) {
                JsonBuilder.connectEventError(BridgeError.appManifestNotFound())
            } else {
                JsonBuilder.connectEventError(BridgeError.appManifestContentError())
            }
        } catch (e: Throwable) {
            FirebaseCrashlytics.getInstance().recordException(e)
            JsonBuilder.connectEventError(BridgeError.unknown(e.bestMessage))
        }
    }

    override suspend fun showLogoutAppBar(wallet: WalletEntity, context: Context, url: Uri) = withContext(Dispatchers.Main.immediate) {
        val connections = dAppsRepository.getConnections(wallet.accountId, wallet.network).flatMap {
            it.value
        }.filter { it.accountId.equalsAddress(wallet.accountId) && it.network == wallet.network }

        if (connections.isNotEmpty()) {
            val text = context.getString(Localization.disconnect_dapp_confirm, url.host)
            SnackBarView.show(
                context = context,
                text = text,
                buttonText = context.getString(Localization.disconnect),
                onClickListener = View.OnClickListener {
                    disconnect(wallet, url, null)
                }
            )
        }
    }

    override suspend fun isScam(context: Context, wallet: WalletEntity, vararg uris: Uri): Boolean {
        if (settingsRepository.isSafeModeEnabled(wallet.network) && safeModeClient.isHasScamUris(*uris)) {
            withContext(Dispatchers.Main) {
                TonConnectSafeModeDialog(context).show(wallet)
            }
            return true
        }
        return false
    }

    private suspend fun readManifest(url: String): AppEntity {
        return fetchManifest(url)
    }

    /**
     * Shared http path with [fetchManifest], also used by the WalletKit `fetchManifest` callback.
     * Fetches the manifest directly; on failure retries once via the c.tonapi.io proxy. If the
     * proxy retry also fails the [ManifestException] propagates and the manifest-error toast shows.
     */
    suspend fun fetchManifestRaw(url: String): String {
        val headers = ArrayMap<String, String>().apply {
            set("Connection", "close")
        }
        return try {
            httpGetManifest(url, headers)
        } catch (ignored: Throwable) {
            FirebaseHelper.manifestFetchRetry(url)
            httpGetManifest(manifestProxyUrl(url), headers)
        }
    }

    // `.simple()` throws OkHttpError on any non-2xx; translate to ManifestException so the
    // manifest-error catch in launchConnectFlow (and the wallet-kit callback) shows the toast.
    private suspend fun httpGetManifest(url: String, headers: ArrayMap<String, String>): String {
        val response = try {
            api.defaultHttpClient.simple(url, headers)
        } catch (e: OkHttpError) {
            throw ManifestException.NotFound(e.statusCode)
        }
        if (response.code != 200) {
            throw ManifestException.NotFound(response.code)
        }
        return response.readBody()
    }

    private suspend fun fetchManifest(url: String): AppEntity {
        val body = fetchManifestRaw(url)
        try {
            val app = AppEntity(body)
            dAppsRepository.insertApp(app)
            return app
        } catch (e: Throwable) {
            throw ManifestException.FailedParse(e)
        }
    }

    // Routes the manifest fetch through c.tonapi.io (CORS bypass / reachability). base64(url)
    // matches the `btoa(url)` shape the proxy expects — Tonkeeper Web uses the same endpoint.
    private fun manifestProxyUrl(url: String): String {
        val encoded = Base64.encodeToString(url.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        return "https://c.tonapi.io/json?url=$encoded&no-cache=true"
    }

    override fun createInjector(
        bridge: DAppBridge,
        wallet: WalletEntity?
    ): ITonConnectWebViewInjector = TonConnectWebViewInjector(bridge)

    companion object {
        private const val THROTTLE_DELAY_MS = 5000L

        private const val TONCONNECT_PREFIX = "tonkeeper://ton-connect"

        private val othersPrefix = listOf("tc://", "https://app.tonkeeper.com/ton-connect")

        fun isTonConnectDeepLink(
            uri: Uri
        ): Boolean {
            return uri.scheme?.lowercase() == "tc" || uri.path?.lowercase() == "/ton-connect" || uri.host?.lowercase() == "ton-connect"
        }

        fun normalizeUri(uri: Uri): Uri {
            val value = uri.toString()
            for (prefix in othersPrefix) {
                if (value.startsWith(prefix, ignoreCase = true)) {
                    return value.replace(prefix, TONCONNECT_PREFIX, ignoreCase = true).toUri()
                }
            }
            return uri
        }

    }
}
