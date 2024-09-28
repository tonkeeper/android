package com.tonapps.tonkeeper.manager.tonconnect

import android.content.Context
import android.net.Uri
import android.util.ArrayMap
import android.util.Log
import androidx.core.net.toUri
import com.tonapps.blockchain.ton.extensions.equalsAddress
import com.tonapps.blockchain.ton.proof.TONProof
import com.tonapps.extensions.appVersionName
import com.tonapps.extensions.filterList
import com.tonapps.extensions.flat
import com.tonapps.extensions.getQueryLong
import com.tonapps.extensions.hasQuery
import com.tonapps.extensions.mapList
import com.tonapps.network.simple
import com.tonapps.security.CryptoBox
import com.tonapps.tonkeeper.extensions.toast
import com.tonapps.tonkeeper.manager.push.PushManager
import com.tonapps.tonkeeper.manager.tonconnect.bridge.Bridge
import com.tonapps.tonkeeper.manager.tonconnect.bridge.JsonBuilder
import com.tonapps.tonkeeper.manager.tonconnect.bridge.model.BridgeError
import com.tonapps.tonkeeper.manager.tonconnect.bridge.model.BridgeMethod
import com.tonapps.tonkeeper.manager.tonconnect.exceptions.ManifestException
import com.tonapps.tonkeeper.ui.screen.tonconnect.TonConnectScreen
import com.tonapps.wallet.api.API
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.dapps.DAppsRepository
import com.tonapps.wallet.data.dapps.entities.AppConnectEntity
import com.tonapps.wallet.data.dapps.entities.AppEntity
import com.tonapps.wallet.localization.Localization
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import uikit.extensions.activity
import uikit.extensions.addForResult
import uikit.navigation.Navigation.Companion.navigation
import uikit.navigation.NavigationActivity
import java.util.concurrent.CancellationException

class TonConnectManager(
    private val scope: CoroutineScope,
    private val api: API,
    private val dAppsRepository: DAppsRepository,
    private val pushManager: PushManager,
) {

    private val bridge: Bridge = Bridge(api)

    private val eventsFlow = dAppsRepository.connectionsFlow
        .map { it.chunked(10) }
        .flat { chunks ->
            chunks.map { bridge.eventsFlow(it, dAppsRepository.lastEventId) }
        }.mapNotNull { event ->
            val lastAppRequestId = dAppsRepository.getLastAppRequestId(event.connection.clientId)
            if (lastAppRequestId >= event.message.id) {
                return@mapNotNull null
            }
            dAppsRepository.setLastAppRequestId(event.connection.clientId, event.message.id)
            event
        }.shareIn(scope, SharingStarted.Eagerly)

    val transactionRequestFlow = eventsFlow.mapNotNull { event ->
        if (event.method == BridgeMethod.SEND_TRANSACTION) {
            Pair(event.connection, event.message)
        } else {
            if (event.method == BridgeMethod.DISCONNECT) {
                deleteConnection(event.connection, event.message.id)
            } else {
                sendBridgeError(event.connection, BridgeError.METHOD_NOT_SUPPORTED, event.message.id)
            }
            null
        }
    }.flowOn(Dispatchers.IO).shareIn(scope, SharingStarted.Eagerly)

    fun walletConnectionsFlow(wallet: WalletEntity) = dAppsRepository.connectionsFlow.filterList { connection ->
        connection.testnet == wallet.testnet && connection.accountId.equalsAddress(wallet.accountId)
    }

    fun walletAppsFlow(wallet: WalletEntity) = walletConnectionsFlow(wallet).mapList { it.appUrl }.map { it.distinct() }.map { urls ->
        dAppsRepository.getApps(urls)
    }.flowOn(Dispatchers.IO)

    private suspend fun deleteConnection(connection: AppConnectEntity, messageId: Long) {
        val deleted = dAppsRepository.deleteConnect(connection)
        if (!deleted) {
            sendBridgeError(connection, BridgeError.UNKNOWN_APP, messageId)
        } else {
            bridge.sendDisconnectResponseSuccess(connection, messageId)
        }
    }

    fun disconnect(wallet: WalletEntity, appUrl: Uri, type: AppConnectEntity.Type? = null) {
        scope.launch(Dispatchers.IO) {
            val connections = dAppsRepository.deleteApp(wallet.accountId, wallet.testnet, appUrl, type)
            for (connection in connections) {
                bridge.sendDisconnect(connection)
            }
            pushManager.dAppUnsubscribe(wallet, connections)
        }
    }

    fun clear(wallet: WalletEntity) {
        scope.launch(Dispatchers.IO) {
            val connections = dAppsRepository.deleteApps(wallet.accountId, wallet.testnet)
            for (connection in connections) {
                bridge.sendDisconnect(connection)
            }
            pushManager.dAppUnsubscribe(wallet, connections)
        }
    }

    suspend fun sendBridgeError(connection: AppConnectEntity, error: BridgeError, id: Long) {
        bridge.sendError(connection, error, id)
    }

    suspend fun sendTransactionResponseSuccess(connection: AppConnectEntity, boc: String, id: Long) {
        bridge.sendTransactionResponseSuccess(connection, boc, id)
    }

    fun isPushEnabled(wallet: WalletEntity, appUrl: Uri): Boolean {
        return dAppsRepository.isPushEnabled(wallet.accountId, wallet.testnet, appUrl)
    }

    private suspend fun newConnect(
        wallet: WalletEntity,
        keyPair: CryptoBox.KeyPair,
        clientId: String,
        appUrl: Uri,
        proof: TONProof.Result?,
        pushEnabled: Boolean,
        type: AppConnectEntity.Type
    ): AppConnectEntity {
        val timestamp = proof?.timestamp ?: (System.currentTimeMillis() / 1000L)
        val connection = AppConnectEntity(
            accountId = wallet.accountId,
            testnet = wallet.testnet,
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
        setPushEnabled(wallet, appUrl, pushEnabled)
        return connection
    }

    suspend fun setPushEnabled(wallet: WalletEntity, appUrl: Uri, enabled: Boolean) {
        val connections = dAppsRepository.setPushEnabled(wallet.accountId, wallet.testnet, appUrl, enabled)
        if (!pushManager.dAppPush(wallet, connections, enabled)) {
            dAppsRepository.setPushEnabled(wallet.accountId, wallet.testnet, appUrl, !enabled)
        }
    }

    fun processDeeplink(
        context: Context,
        uri: Uri,
        fromQR: Boolean,
        refSource: Uri?
    ): Boolean {
        if (uri.hasQuery("open")) {
            return true
        }
        try {
            val activity = context.activity ?: throw IllegalArgumentException("Context must be an Activity")
            val normalizedUri = normalizeUri(uri)
            val tonConnect = TonConnect.parse(normalizedUri, refSource, fromQR)
            scope.launch {
                connectRemoteApp(activity, tonConnect)
            }
            return true
        } catch (e: Exception) {
            context.navigation?.toast(Localization.invalid_link)
            return false
        }
    }

    private suspend fun connectRemoteApp(activity: NavigationActivity, tonConnect: TonConnect) {
        val keyPair = CryptoBox.keyPair()
        val message = launchConnectFlow(activity, tonConnect, keyPair, null)
        bridge.send(tonConnect.clientId, keyPair, message.toString())
    }

    suspend fun launchConnectFlow(
        activity: NavigationActivity,
        tonConnect: TonConnect,
        keyPair: CryptoBox.KeyPair = CryptoBox.keyPair(),
        wallet: WalletEntity?
    ): JSONObject = withContext(Dispatchers.IO) {
        if (tonConnect.request.items.isEmpty()) {
            return@withContext JsonBuilder.connectEventError(BridgeError.BAD_REQUEST)
        }

        val clientId = tonConnect.clientId
        try {
            val app = readManifest(tonConnect.manifestUrl)
            val screen = TonConnectScreen.newInstance(
                app = app,
                proofPayload = tonConnect.proofPayload,
                returnUri = tonConnect.returnUri,
                wallet = wallet,
            )
            val bundle = activity.addForResult(screen)
            val response = screen.contract.parseResult(bundle)
            newConnect(
                wallet = response.wallet,
                keyPair = keyPair,
                clientId = clientId,
                appUrl = app.url,
                proof = response.proof,
                pushEnabled = response.notifications,
                type = if (tonConnect.jsInject) AppConnectEntity.Type.Internal else AppConnectEntity.Type.External
            )
            JsonBuilder.connectEventSuccess(
                wallet = response.wallet,
                proof = response.proof,
                proofError = response.proofError,
                activity.appVersionName
            )
        } catch (e: CancellationException) {
            JsonBuilder.connectEventError(BridgeError.USER_DECLINED_TRANSACTION)
        } catch (e: ManifestException) {
            JsonBuilder.connectEventError(BridgeError.resolve(e))
        } catch (e: Throwable) {
            JsonBuilder.connectEventError(BridgeError.UNKNOWN)
        }
    }

    private suspend fun readManifest(url: String): AppEntity {
        return fetchManifest(url)
    }

    private suspend fun fetchManifest(url: String): AppEntity {
        val headers = ArrayMap<String, String>().apply {
            set("Connection", "close")
        }
        val response = api.defaultHttpClient.simple(url, headers)
        if (response.code != 200) {
            throw ManifestException.NotFound(response.code)
        }
        val body = response.body?.string() ?: throw ManifestException.FailedParse(NullPointerException())
        try {
            val app = AppEntity(body)
            dAppsRepository.insertApp(app)
            return app
        } catch (e: Throwable) {
            throw ManifestException.FailedParse(e)
        }
    }

    companion object {
        private const val TONCONNECT_PREFIX = "tonkeeper://ton-connect"

        private val othersPrefix = listOf("tc://", "https://app.tonkeeper.com/ton-connect")

        fun isTonConnectDeepLink(
            uri: Uri
        ): Boolean {
            return uri.scheme?.lowercase() == "tc" || uri.path?.lowercase() == "/ton-connect" || uri.host?.lowercase() == "ton-connect"
        }

        private fun normalizeUri(uri: Uri): Uri {
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