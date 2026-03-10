package com.tonapps.wallet.data.rn.data

import org.json.JSONObject

data class RNFavorites(
    val name: String,
    val address: String
): RNData() {

    constructor(json: JSONObject) : this(
        json.getString("name"),
        json.getString("address")
    )

    override fun toJSON(): JSONObject {
        val json = JSONObject()
        json.put("name", name)
        json.put("address", address)
        return json
    }
}