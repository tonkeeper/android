package com.tonapps.tonkeeper.core.fiat.models

import org.json.JSONArray
import org.json.JSONObject

data class FiatCategory(
    val items: List<FiatItem>,
    val subtitle: String,
    val title: String,
    val type: String,
): BaseFiat() {

    companion object {
        fun parse(array: JSONArray): List<FiatCategory> {
            val items = mutableListOf<FiatCategory>()
            for (i in 0 until array.length()) {
                items.add(FiatCategory(array.getJSONObject(i)))
            }
            return items
        }
    }

    constructor(json: JSONObject) : this(
        items = FiatItem.parse(json.getJSONArray("items")),
        subtitle = json.getString("subtitle"),
        title = json.getString("title"),
        type = json.getString("type"),
    )

    override fun toJSON(): JSONObject {
        return JSONObject().apply {
            put("items", FiatItem.toArray(items))
            put("subtitle", subtitle)
            put("title", title)
            put("type", type)
        }
    }


}