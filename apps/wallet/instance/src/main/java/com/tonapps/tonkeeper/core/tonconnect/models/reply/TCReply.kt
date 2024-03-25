package com.tonapps.tonkeeper.core.tonconnect.models.reply

import org.json.JSONArray

abstract class TCReply: TCBase() {

    companion object {
        fun toJSONArray(items: List<TCReply>): JSONArray {
            val jsonArray = JSONArray()
            items.forEach { jsonArray.put(it.toJSON()) }
            return jsonArray
        }
    }
}