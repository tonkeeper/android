package com.tonapps.tonkeeper.core.fiat.models

import org.json.JSONObject

data class FiatSuccessUrlPattern(
    val pattern: String?,
    val purchaseIdIndex: Int
): BaseFiat() {

    constructor(data: String) : this(
        JSONObject(data)
    )

    constructor(json: JSONObject) : this(
        pattern = json.optString("pattern", null),
        purchaseIdIndex = json.optInt("purchaseIdIndex", 0)
    )

    override fun toJSON(): JSONObject {
        return JSONObject().apply {
            put("pattern", pattern)
            put("purchaseIdIndex", purchaseIdIndex)
        }
    }
}