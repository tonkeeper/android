package com.tonapps.tonkeeper.core.fiat.models

import org.json.JSONArray
import org.json.JSONObject

data class FiatButton(
    val title: String,
    val url: String
): BaseFiat() {

    companion object {
        fun parse(array: JSONArray): List<FiatButton> {
            val buttons = mutableListOf<FiatButton>()
            for (i in 0 until array.length()) {
                buttons.add(FiatButton(array.getJSONObject(i)))
            }
            return buttons
        }
    }

    constructor(json: JSONObject) : this(
        title = json.getString("title"),
        url = json.getString("url")
    )

    override fun toJSON(): JSONObject {
        return JSONObject().apply {
            put("title", title)
            put("url", url)
        }
    }
}