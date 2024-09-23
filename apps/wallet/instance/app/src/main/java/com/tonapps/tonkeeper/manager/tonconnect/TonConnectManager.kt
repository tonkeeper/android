package com.tonapps.tonkeeper.manager.tonconnect

import android.content.Context
import android.net.Uri
import android.util.ArrayMap
import android.util.Log
import androidx.core.net.toUri
import com.tonapps.blockchain.ton.extensions.equalsAddress
import com.tonapps.blockchain.ton.proof.TONProof
import com.tonapps.extensions.filterList
import com.tonapps.extensions.flat
import com.tonapps.extensions.mapList
import com.tonapps.network.simple
import com.tonapps.security.CryptoBox
import com.tonapps.tonkeeper.extensions.toastLoading
import com.tonapps.tonkeeper.manager.tonconnect.bridge.Bridge
import com.tonapps.tonkeeper.manager.tonconnect.bridge.JsonBuilder
import com.tonapps.tonkeeper.manager.tonconnect.bridge.model.BridgeError
import com.tonapps.tonkeeper.manager.tonconnect.bridge.model.BridgeError.Companion.asException
import com.tonapps.tonkeeper.manager.tonconnect.bridge.model.BridgeMethod
import com.tonapps.tonkeeper.manager.tonconnect.exceptions.ManifestException
import com.tonapps.tonkeeper.ui.screen.tonconnect.TonConnectResponse
import com.tonapps.tonkeeper.ui.screen.tonconnect.TonConnectScreen
import com.tonapps.wallet.api.API
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.account.Wallet
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.dapps.DAppsRepository
import com.tonapps.wallet.data.dapps.entities.AppConnectEntity
import com.tonapps.wallet.data.dapps.entities.AppEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
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
    private val accountRepository: AccountRepository,
    private val dAppsRepository: DAppsRepository
) {

    private val bridge: Bridge = Bridge(api)

    private val eventsFlow = dAppsRepository.connectionsFlow
        .map {
            Log.d("TonConnectManager", "connections: ${it.size}")
            it.chunked(10)
        }
        .flat { chunks ->
            chunks.map { bridge.eventsFlow(it, dAppsRepository.lastEventId) }
        }.mapNotNull { event ->
            val lastAppRequestId = dAppsRepository.getLastAppRequestId(event.connection.clientId)
            if (lastAppRequestId >= event.message.id) {
                return@mapNotNull null
            }
            dAppsRepository.setLastAppRequestId(event.connection.clientId, event.message.id)
            event
        }.shareIn(scope, SharingStarted.Eagerly, 1)

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
    }.flowOn(Dispatchers.IO)

    fun walletConnectionsFlow(wallet: WalletEntity) = dAppsRepository.connectionsFlow.filterList { connection ->
        connection.testnet == wallet.testnet && connection.accountId.equalsAddress(wallet.accountId)
    }

    fun walletAppsFlow(wallet: WalletEntity) = walletConnectionsFlow(wallet).mapList { it.host }.map { it.distinct() }.map { hosts ->
        dAppsRepository.getApps(hosts)
    }.flowOn(Dispatchers.IO)

    private suspend fun deleteConnection(connection: AppConnectEntity, messageId: Long) {
        val deleted = dAppsRepository.deleteConnect(connection)
        if (!deleted) {
            sendBridgeError(connection, BridgeError.UNKNOWN_APP, messageId)
        } else {
            bridge.sendDisconnectResponseSuccess(connection, messageId)
        }
    }

    fun disconnect(wallet: WalletEntity, host: String, type: AppConnectEntity.Type? = null) {
        scope.launch(Dispatchers.IO) {
            val connections = dAppsRepository.deleteApp(wallet.accountId, wallet.testnet, host, type)
            for (connection in connections) {
                bridge.sendDisconnect(connection)
            }
        }
    }

    fun clear(wallet: WalletEntity) {
        scope.launch(Dispatchers.IO) {
            val connections = dAppsRepository.deleteApps(wallet.accountId, wallet.testnet)
            for (connection in connections) {
                bridge.sendDisconnect(connection)
            }
        }
    }

    suspend fun sendBridgeError(connection: AppConnectEntity, error: BridgeError, id: Long) {
        bridge.sendError(connection, error, id)
    }

    suspend fun sendTransactionResponseSuccess(connection: AppConnectEntity, boc: String, id: Long) {
        bridge.sendTransactionResponseSuccess(connection, boc, id)
    }

    fun isPushEnabled(wallet: WalletEntity, host: String): Boolean {
        return dAppsRepository.isPushEnabled(wallet.accountId, wallet.testnet, host)
    }

    private suspend fun newConnect(
        wallet: WalletEntity,
        keyPair: CryptoBox.KeyPair,
        clientId: String,
        host: String,
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
            host = host,
            keyPair = keyPair,
            proofSignature = proof?.signature,
            timestamp = timestamp,
            proofPayload = proof?.payload,
            pushEnabled = pushEnabled
        )
        if (!dAppsRepository.newConnect(connection)) {
            throw Exception("Failed to save connection")
        }
        setPushEnabled(wallet.accountId, wallet.testnet, host, pushEnabled)
        return connection
    }

    suspend fun setPushEnabled(accountId: String, testnet: Boolean, host: String, enabled: Boolean) {
        dAppsRepository.setPushEnabled(accountId, testnet, host, enabled)
    }

    fun processDeeplink(
        context: Context,
        uri: Uri,
        fromQR: Boolean,
        refSource: Uri?
    ): Boolean {
        try {
            val activity = context.activity ?: throw IllegalArgumentException("Context must be an Activity")
            val normalizedUri = normalizeUri(uri)
            val tonConnect = TonConnect.parse(normalizedUri, refSource, fromQR)
            scope.launch {
                connectRemoteApp(activity, tonConnect)
            }
            return true
        } catch (e: Exception) {
            return false
        }
    }

    private suspend fun connectRemoteApp(activity: NavigationActivity, tonConnect: TonConnect) {
        val keyPair = CryptoBox.keyPair()
        val message = launchConnectFlow(activity, tonConnect, keyPair)
        bridge.send(tonConnect.clientId, keyPair, message.toString())
    }

    suspend fun launchConnectFlow(
        activity: NavigationActivity,
        tonConnect: TonConnect,
        keyPair: CryptoBox.KeyPair = CryptoBox.keyPair()
    ): JSONObject = withContext(Dispatchers.IO) {
        val clientId = tonConnect.clientId
        try {
            val app = readManifest(tonConnect.manifestUrl)
            val screen = TonConnectScreen.newInstance(
                app = app,
                proofPayload = tonConnect.proofPayload,
                returnUri = tonConnect.returnUri,
            )
            val bundle = activity.addForResult(screen)
            val response = screen.contract.parseResult(bundle)
            newConnect(
                wallet = response.wallet,
                keyPair = keyPair,
                clientId = clientId,
                host = app.host,
                proof = response.proof,
                pushEnabled = response.notifications,
                type = if (tonConnect.jsInject) AppConnectEntity.Type.Internal else AppConnectEntity.Type.External
            )
            JsonBuilder.connectEventSuccess(
                wallet = response.wallet,
                proof = response.proof,
                proofError = response.proofError
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
            val value = uri.toString().lowercase()
            for (prefix in othersPrefix) {
                if (value.startsWith(prefix)) {
                    return value.replace(prefix, TONCONNECT_PREFIX).toUri()
                }
            }
            return uri
        }

    }
}