package com.tonapps.tonkeeper.manager.tonconnect.bridge

import com.tonapps.blockchain.ton.proof.TONProof
import com.tonapps.extensions.base64
import com.tonapps.extensions.optStringCompat
import com.tonapps.security.CryptoBox
import com.tonapps.security.hex
import com.tonapps.tonkeeper.manager.tonconnect.bridge.model.BridgeError
import com.tonapps.tonkeeper.manager.tonconnect.bridge.model.BridgeEvent
import com.tonapps.wallet.api.API
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.dapps.entities.AppConnectEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.withContext

internal class Bridge(private val api: API) {

    suspend fun sendDisconnectResponseSuccess(
        connection: AppConnectEntity,
        id: Long
    ): String {
        val message = JsonBuilder.responseDisconnect(id).toString()
        send(connection, message)
        return message
    }

    suspend fun sendTransactionResponseSuccess(
        connection: AppConnectEntity,
        boc: String,
        id: Long
    ): String {
        val message = JsonBuilder.responseSendTransaction(id, boc).toString()
        send(connection, message)
        return message
    }

    suspend fun sendError(
        connection: AppConnectEntity,
        error: BridgeError,
        id: Long,
    ): String {
        val message = JsonBuilder.responseError(id, error).toString()
        send(connection, message)
        return message
    }

    suspend fun sendConnectError(
        connection: AppConnectEntity,
        error: BridgeError
    ): String {
        val message = JsonBuilder.connectEventError(error).toString()
        send(connection, message)
        return message
    }

    suspend fun sendConnectSuccess(
        connection: AppConnectEntity,
        wallet: WalletEntity,
        proof: TONProof.Result?,
        proofError: BridgeError?,
    ): String {
        val message = JsonBuilder.connectEventSuccess(wallet, proof, proofError).toString()
        send(connection, message)
        return message
    }

    suspend fun sendDisconnect(connection: AppConnectEntity): String {
        val message = JsonBuilder.disconnectEvent().toString()
        send(connection, message)
        return message
    }

    suspend fun send(
        connection: AppConnectEntity,
        message: String
    ) = withContext(Dispatchers.IO) {
        if (connection.type != AppConnectEntity.Type.Internal) {
            val encrypted = connection.encryptMessage(message.toByteArray())
            api.tonconnectSend(connection.publicKeyHex, connection.clientId, encrypted.base64)
        }
    }

    suspend fun send(
        clientId: String,
        keyPair: CryptoBox.KeyPair,
        unencryptedMessage: String
    ) = withContext(Dispatchers.IO) {
        val encryptedMessage = AppConnectEntity.encryptMessage(clientId.hex(), keyPair.privateKey, unencryptedMessage.toByteArray())
        api.tonconnectSend(hex(keyPair.publicKey), clientId, encryptedMessage.base64)
    }

    fun eventsFlow(
        connections: List<AppConnectEntity>,
        lastEventId: Long,
    ): Flow<BridgeEvent> {
        val publicKeys = connections.map { it.publicKeyHex }
        return api.tonconnectEvents(publicKeys, lastEventId)
            .mapNotNull { event ->
                val id = event.id?.toLongOrNull() ?: return@mapNotNull null
                val from = event.json.optStringCompat("from") ?: return@mapNotNull null
                val message = event.json.optStringCompat("message") ?: return@mapNotNull null
                val connection = connections.find { it.clientId == from } ?: return@mapNotNull null
                val decryptedMessage = BridgeEvent.Message(connection.decryptEventMessage(message))
                BridgeEvent(
                    eventId = id,
                    message = decryptedMessage,
                    connection = connection.copy(),
                )
            }
    }
}