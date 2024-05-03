package com.tonapps.wallet.data.tonconnect.entities

import org.json.JSONArray
import org.json.JSONObject

data class DAppPayloadEntity(
    val manifestUrl: String,
    val items: List<DAppItemEntity>
) {

    constructor(json: JSONObject) : this(
        manifestUrl = json.getString("manifestUrl"),
        items = DAppItemEntity.parse(json.optJSONArray("items"))
    )

    fun toJSON(): JSONObject {
        val itemsArray = JSONArray()
        items.forEach { itemsArray.put(it.toJSON()) }
        return JSONObject().apply {
            put("manifestUrl", manifestUrl)
            put("items", itemsArray)
        }
    }
}