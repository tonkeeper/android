package ton.console.model

import org.json.JSONObject

data class RatesTokenModel(
    val prices: RatesPricesModel,
    val diff24h: RatesDiffModel,
    val diff7d: RatesDiffModel,
    val diff30d: RatesDiffModel,
) {

    constructor(json: JSONObject) : this(
        prices = RatesPricesModel.parse(json.getJSONObject("prices")),
        diff24h = RatesDiffModel.parse(json.getJSONObject("diff_24h")),
        diff7d = RatesDiffModel.parse(json.getJSONObject("diff_7d")),
        diff30d = RatesDiffModel.parse(json.getJSONObject("diff_30d")),
    )

}