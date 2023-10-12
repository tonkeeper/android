package com.tonkeeper.ton.console.model

import org.json.JSONObject

data class JettonPreviewModel(
    val address: String,
    val name: String,
    val symbol: String,
    val decimals: Int,
    val image: String,
    val verification: String
) {

    constructor(json: JSONObject) : this(
        json.getString("address"),
        json.getString("name"),
        json.getString("symbol"),
        json.getInt("decimals"),
        json.getString("image"),
        json.getString("verification")
    )
}