package com.tonapps.bus.core.contract

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Builds the `other_metadata` JSON string of RED events: a value that is not known
 * (null) is left out, keys are sorted, and no metadata is sent when no value is known.
 */
object RedMetadata {

    const val SOURCE = "source"
    const val ASSET = "asset"
    const val AMOUNT = "amount"
    const val FEE_PAID_IN = "fee_paid_in"
    const val ASSET_NETWORK = "asset_network"
    const val TOKEN_SYMBOL = "token_symbol"
    const val FROM_TOKEN = "from_token"
    const val TO_TOKEN = "to_token"
    const val FROM_AMOUNT = "from_amount"
    const val TO_AMOUNT = "to_amount"
    const val PROVIDER_NAME = "provider_name"
    const val ASSET_FROM = "asset_from"
    const val ASSET_TO = "asset_to"

    fun json(vararg entries: Pair<String, Any?>): String? {
        val values = entries
            .mapNotNull { (key, value) -> primitive(value)?.let { key to it } }
            .sortedBy { it.first }
        if (values.isEmpty()) {
            return null
        }
        return JsonObject(values.toMap()).toString()
    }

    private fun primitive(value: Any?): JsonPrimitive? {
        return when (value) {
            null -> null
            is Boolean -> JsonPrimitive(value)
            // NaN and infinities have no JSON form.
            is Double -> value.takeIf { it.isFinite() }?.let(::JsonPrimitive)
            is Float -> value.takeIf { it.isFinite() }?.let(::JsonPrimitive)
            is Number -> JsonPrimitive(value)
            else -> JsonPrimitive(value.toString())
        }
    }
}

/** `attempt_source` values of RED `op_attempt`. */
object RedAttemptSource {
    const val NATIVE_UI = "native_ui"
}
