package ton.console.method

import android.util.ArrayMap
import ton.SupportedCurrency
import ton.SupportedTokens
import ton.console.model.RatesModel
import ton.console.model.RatesTokenModel
import org.json.JSONObject

class RatesMethod(
    tokens: List<String>,
    currency: List<String>
): BaseMethod<RatesModel>("rates") {

    constructor() : this(
        SupportedTokens.values().map { it.code },
        SupportedCurrency.values().map { it.code }
    )

    init {
        querySet("tokens", tokens.joinToString(","))
        querySet("currencies", currency.joinToString(","))
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