package com.tonapps.wallet.data.rn.data

import org.json.JSONObject

data class RNKeystone(
    val xfp: String,
    val path: String
): RNData() {

    constructor(json: JSONObject) : this(
        json.getString("xfp"),
        json.getString("path")
    )

    override fun toJSON(): JSONObject {
        return JSONObject().apply {
            put("xfp", xfp)
            put("path", path)
        }
    }
}
