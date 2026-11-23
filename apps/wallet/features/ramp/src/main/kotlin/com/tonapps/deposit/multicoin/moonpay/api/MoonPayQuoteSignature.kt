package com.tonapps.deposit.multicoin.moonpay.api

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import java.util.Base64

// A quote signature is a JWT whose payload is the quote itself. Payment frames reject it unless
// every field below is present, so a quote lacking one fails here instead of inside the frame.
internal object MoonPayQuoteSignature {

    private val requiredByPaymentFrames = listOf(
        "signature",
        "wallet.address",
        "paymentMethod.type",
        "source.amount",
        "source.asset",
        "destination.amount",
        "destination.asset",
    )

    fun payload(signature: String): JsonObject? {
        val segment = signature.split(".").getOrNull(1) ?: return null
        return try {
            Json.parseToJsonElement(Base64.getUrlDecoder().decode(segment).decodeToString()) as? JsonObject
        } catch (_: IllegalArgumentException) {
            null
        } catch (_: SerializationException) {
            null
        }
    }

    fun missingPaymentFrameFields(payload: JsonObject): List<String> {
        return requiredByPaymentFrames.filter { path ->
            val value = path.split(".").fold<String, Any?>(payload) { node, key -> (node as? JsonObject)?.get(key) }
            value == null || value == JsonNull
        }
    }
}
