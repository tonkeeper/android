package com.tonapps.tonkeeper.core.tonconnect.models.reply

import org.json.JSONObject

data class TCResultSuccess(
    val id: String,
    val result: String,
): TCBase() {

    override fun toJSON(): JSONObject {
        val json = JSONObject()
        json.put("result", result)
        json.put("id", id)
        return json
    }
}