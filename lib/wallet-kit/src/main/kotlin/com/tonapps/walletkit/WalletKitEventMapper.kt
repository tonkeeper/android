package com.tonapps.walletkit

import io.ton.walletkit.api.generated.TONSendTransactionRequestEvent
import io.ton.walletkit.api.generated.TONSignData
import io.ton.walletkit.api.generated.TONSignDataRequestEvent
import org.json.JSONArray
import org.json.JSONObject

/**
 * Maps WalletKit SDK event types to JSON strings consumable by the app layer.
 */
object WalletKitEventMapper {

    /** Serializes a WalletKit transaction request to a JSON string. */
    fun serializeTransactionRequest(event: TONSendTransactionRequestEvent): String {
        val request = event.request

        return JSONObject().apply {
            request.validUntil?.let { put("valid_until", it) }
            request.network?.chainId?.let { put("network", it) }
            event.walletAddress?.value?.let { put("from", it) }
            put("messages", JSONArray().apply {
                for (msg in request.messages) {
                    put(JSONObject().apply {
                        put("address", msg.address)
                        put("amount", msg.amount)
                        msg.payload?.let { put("payload", it) }
                        msg.stateInit?.let { put("stateInit", it) }
                    })
                }
            })
        }.toString()
    }

    /** Serializes a WalletKit sign-data request to a JSON string. */
    fun serializeSignDataPayload(event: TONSignDataRequestEvent): String {
        return when (val signData = event.payload.data) {
            is TONSignData.Cell -> JSONObject().apply {
                put("type", "cell")
                put("schema", signData.value.schema)
                put("cell", signData.value.content.value)
            }.toString()
            is TONSignData.Binary -> JSONObject().apply {
                put("type", "binary")
                put("bytes", signData.value.content.value)
            }.toString()
            is TONSignData.Text -> JSONObject().apply {
                put("type", "text")
                put("text", signData.value.content)
            }.toString()
        }
    }
}
