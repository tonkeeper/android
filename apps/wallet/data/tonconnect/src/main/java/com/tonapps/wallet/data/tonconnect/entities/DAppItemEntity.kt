package com.tonapps.wallet.data.tonconnect.entities

import org.json.JSONArray
import org.json.JSONObject

data class DAppItemEntity(
    val name: String,
    val payload: String?
) {

    companion object {
        const val TON_ADDR = "ton_addr"
        const val TON_PROOF = "ton_proof"

        fun parse(array: JSONArray?): List<DAppItemEntity> {
            if (array == null || array.length() == 0) return emptyList()
            val list = mutableListOf<DAppItemEntity>()
            for (i in 0 until array.length()) {
                list.add(DAppItemEntity(array.getJSONObject(i)))
            }
            return list
        }
    }

    constructor(json: JSONObject) : this(
        name = json.getString("name"),
        payload = json.optString("payload")
    )

    fun toJSON(): JSONObject {
        return JSONObject().apply {
            put("name", name)
            payload?.let { put("payload", it) }
        }
    }

}