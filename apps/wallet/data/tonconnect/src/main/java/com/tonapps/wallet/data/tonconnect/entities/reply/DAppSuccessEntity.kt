package com.tonapps.wallet.data.tonconnect.entities.reply

import org.json.JSONObject

data class DAppSuccessEntity(
    val id: String,
    val result: String,
): DAppReply() {

    override fun toJSON(): JSONObject {
        val json = JSONObject()
        json.put("result", result)
        json.put("id", id)
        return json
    }

}