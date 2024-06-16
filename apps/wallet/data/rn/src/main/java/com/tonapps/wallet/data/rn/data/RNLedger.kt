package com.tonapps.wallet.data.rn.data

import org.json.JSONObject

data class RNLedger(
    val deviceId: String,
    val deviceModel: String,
    val accountIndex: Int
): RNData() {

    constructor(json: JSONObject) : this(
        json.getString("deviceId"),
        json.getString("deviceModel"),
        json.getInt("accountIndex")
    )

    override fun toJSON(): JSONObject {
        return JSONObject().apply {
            put("deviceId", deviceId)
            put("deviceModel", deviceModel)
            put("accountIndex", accountIndex)
        }
    }

}