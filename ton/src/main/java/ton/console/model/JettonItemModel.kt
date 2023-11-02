package ton.console.model

import okhttp3.internal.toLongOrDefault
import org.json.JSONObject

data class JettonItemModel(
    val balance: String,
    val jetton: JettonModel
) {

    val imageURL: String
        get() = jetton.image

    val name: String
        get() = jetton.name

    val symbol: String
        get() = jetton.symbol

    val amount: Float
        get() = balance.toLongOrDefault(0) / 1000000000f

    constructor(json: JSONObject) : this(
        json.getString("balance"),
        JettonModel(json.getJSONObject("jetton"))
    )
}