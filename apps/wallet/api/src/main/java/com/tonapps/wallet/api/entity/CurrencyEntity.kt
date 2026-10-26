package com.tonapps.wallet.api.entity

import org.json.JSONArray
import org.json.JSONObject

data class CurrencyEntity(
    val code: String,
    val name: String,
    val image: String?,
    val type: String,
    val symbol: String?,
) {

    val isFiat: Boolean
        get() = type.equals(TYPE_FIAT, ignoreCase = true)

    companion object {
        const val TYPE_FIAT = "fiat"
        const val TYPE_CRYPTO = "crypto"

        fun of(json: JSONObject): CurrencyEntity? {
            val code = json.optString("code").trim().uppercase()
            if (code.isEmpty()) {
                return null
            }
            return CurrencyEntity(
                code = code,
                name = json.optString("name").trim().ifEmpty { code },
                image = json.optString("image").takeIf { it.isNotBlank() },
                type = json.optString("type").ifBlank { TYPE_FIAT },
                symbol = json.optString("symbol").trim().takeIf { it.isNotEmpty() },
            )
        }

        fun parse(array: JSONArray): List<CurrencyEntity> {
            return (0 until array.length())
                .mapNotNull { index -> array.optJSONObject(index)?.let(::of) }
                .distinctBy { it.code }
        }
    }
}
