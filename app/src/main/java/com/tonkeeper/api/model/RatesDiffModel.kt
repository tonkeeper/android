package com.tonkeeper.api.model

import androidx.collection.ArrayMap
import com.tonkeeper.SupportedCurrency
import org.json.JSONObject

data class RatesDiffModel(
    val values: ArrayMap<String, String>
) {

    companion object {
        fun parse(json: JSONObject): RatesDiffModel {
            val values = ArrayMap<String, String>()
            val keys = json.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                values[key] = json.getString(key)
            }
            return RatesDiffModel(values)
        }
    }

    val usd: String
        get() = get(SupportedCurrency.USD)

    val eur: String
        get() = get(SupportedCurrency.EUR)

    fun get(supportedCurrency: SupportedCurrency): String {
        return get(supportedCurrency.code)
    }

    fun get(key: String): String {
        return values[key.uppercase()] ?: ""
    }
}