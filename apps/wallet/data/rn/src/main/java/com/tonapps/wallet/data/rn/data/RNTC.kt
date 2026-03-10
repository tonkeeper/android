package com.tonapps.wallet.data.rn.data

import org.json.JSONObject

data class RNTC(
    val mainnet: List<RNTCApps>,
    val testnet: List<RNTCApps>
) {

    constructor(json: JSONObject) : this(
        mainnet = RNTCApps.parse(json.optJSONObject("mainnet")),
        testnet = RNTCApps.parse(json.optJSONObject("testnet"))
    )

    fun toJSON(): JSONObject {
        val json = JSONObject()
        json.put("mainnet", RNTCApps.toJSON(mainnet))
        json.put("testnet", RNTCApps.toJSON(testnet))
        return json
    }
}