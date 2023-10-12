package com.tonkeeper.ton.console.model

import org.json.JSONObject

data class ReFoundModel(
    val type: String,
    val origin: String
) {

    companion object {
        fun parse(json: JSONObject?): ReFoundModel? {
            if (json == null) return null
            return ReFoundModel(json)
        }
    }

    constructor(json: JSONObject) : this(
        type = json.getString("type"),
        origin = json.getString("origin")
    )
}