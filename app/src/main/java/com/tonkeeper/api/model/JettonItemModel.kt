package com.tonkeeper.api.model

import com.tonkeeper.extensions.toCoin
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
        get() = balance.toLongOrDefault(0).toCoin()

    constructor(json: JSONObject) : this(
        json.getString("balance"),
        JettonModel(json.getJSONObject("jetton"))
    )
}