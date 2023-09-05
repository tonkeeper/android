package com.tonkeeper.api.model

import androidx.collection.ArrayMap
import com.tonkeeper.SupportedCurrency
import com.tonkeeper.extensions.getFloat
import org.json.JSONObject

data class RatesPricesModel(
    val values: ArrayMap<String, Float>
) {

    companion object {

        fun parse(json: JSONObject): RatesPricesModel {
            val values = ArrayMap<String, Float>()
            val keys = json.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                values[key] = json.getFloat(key)
            }
            return RatesPricesModel(values)
        }
    }

    val usd: Float
        get() = get(SupportedCurrency.USD)

    val eur: Float
        get() = get(SupportedCurrency.EUR)

    fun get(supportedCurrency: SupportedCurrency): Float {
        return get(supportedCurrency.code)
    }

    fun get(key: String): Float {
        return values[key.uppercase()] ?: 0f
    }
}

