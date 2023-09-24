package com.tonkeeper.ton.console.model

import android.util.ArrayMap
import com.tonkeeper.ton.SupportedCurrency
import com.tonkeeper.ton.extensions.getFloat
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

    fun get(supportedCurrency: SupportedCurrency): Float {
        return get(supportedCurrency.code)
    }

    fun get(key: String): Float {
        return values[key.uppercase()] ?: 0f
    }
}