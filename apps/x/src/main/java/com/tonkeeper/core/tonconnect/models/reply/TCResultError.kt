package com.tonkeeper.core.tonconnect.models.reply

import org.json.JSONObject

data class TCResultError(
    val id: String,
    val errorCode: Int,
    val errorMessage: String
): TCBase() {

    override fun toJSON(): JSONObject {
        val error = JSONObject()
        error.put("code", errorCode)
        error.put("message", errorMessage)

        val json = JSONObject()
        json.put("id", id)
        json.put("error", error)
        return json
    }
}
