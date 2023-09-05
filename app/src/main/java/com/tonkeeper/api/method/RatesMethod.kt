package com.tonkeeper.api.method

import androidx.collection.ArrayMap
import com.tonkeeper.SupportedCurrency
import com.tonkeeper.SupportedTokens
import com.tonkeeper.api.model.RatesModel
import com.tonkeeper.api.model.RatesTokenModel
import org.json.JSONObject

class RatesMethod: BaseMethod<RatesModel>("rates") {

    init {
        querySet("tokens", SupportedTokens.TON)
        querySet("currencies", SupportedCurrency.values().joinToString(","))
    }

    override fun parseJSON(response: JSONObject): RatesModel {
        val rates = response.getJSONObject("rates")
        val tokenKeys = rates.keys()
        val map = ArrayMap<String, RatesTokenModel>()
        for (key in tokenKeys) {
            map[key] = RatesTokenModel(rates.getJSONObject(key))
        }
        return RatesModel(map)
    }

}