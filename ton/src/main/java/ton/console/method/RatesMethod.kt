package com.tonkeeper.ton.console.method

import android.util.ArrayMap
import com.tonkeeper.ton.SupportedCurrency
import com.tonkeeper.ton.SupportedTokens
import com.tonkeeper.ton.console.model.RatesModel
import com.tonkeeper.ton.console.model.RatesTokenModel
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