package com.tonkeeper.ton.console.model

import org.json.JSONObject

data class JettonModel(
    val name: String,
    val image: String,
    val symbol: String
) {

    constructor(json: JSONObject) : this(
        json.getString("name"),
        json.getString("image"),
        json.getString("symbol")
    )
}