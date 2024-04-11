package com.tonapps.wallet.data.tonconnect.entities.reply

import org.json.JSONArray
import org.json.JSONObject

abstract class DAppReply {

    abstract fun toJSON(): JSONObject

    override fun toString(): String {
        return toJSON().toString()
    }

    companion object {
        fun toJSONArray(items: List<DAppReply>): JSONArray {
            val jsonArray = JSONArray()
            items.forEach { jsonArray.put(it.toJSON()) }
            return jsonArray
        }
    }
}
