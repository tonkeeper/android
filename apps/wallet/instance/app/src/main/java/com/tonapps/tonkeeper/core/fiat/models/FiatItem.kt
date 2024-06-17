package com.tonapps.tonkeeper.core.fiat.models

import org.json.JSONArray
import org.json.JSONObject

data class FiatItem(
    val id: String,
    val title: String,
    val iconUrl: String,
    val actionButton: FiatButton,
    val description: String,
    val disabled: Boolean,
    val infoButtons: List<FiatButton>,
    val subtitle: String,
    val successUrlPattern: FiatSuccessUrlPattern?,
): BaseFiat() {

    companion object {
        fun parse(array: JSONArray): List<FiatItem> {
            val items = mutableListOf<FiatItem>()
            for (i in 0 until array.length()) {
                items.add(FiatItem(array.getJSONObject(i)))
            }
            return items
        }

        fun toArray(list: List<FiatItem>): JSONArray {
            val array = JSONArray()
            for (item in list) {
                array.put(item.toJSON())
            }
            return array
        }
    }

    constructor(json: JSONObject) : this(
        id = json.getString("id"),
        title = json.getString("title"),
        iconUrl = json.getString("icon_url"),
        actionButton = FiatButton(json.getJSONObject("action_button")),
        description = json.getString("description"),
        disabled = json.optBoolean("disabled"),
        infoButtons = FiatButton.parse(json.getJSONArray("info_buttons")),
        subtitle = json.getString("subtitle"),
        successUrlPattern = json.optJSONObject("successUrlPattern")?.let { FiatSuccessUrlPattern(it) },
    )

    override fun toJSON(): JSONObject {
        val json = JSONObject()
        json.put("id", id)
        json.put("title", title)
        json.put("icon_url", iconUrl)
        json.put("action_button", actionButton.toJSON())
        json.put("description", description)
        json.put("disabled", disabled)
        json.put("info_buttons", JSONArray(infoButtons.map { it.toJSON() }))
        json.put("subtitle", subtitle)
        successUrlPattern?.toJSON()?.let {
            json.put("successUrlPattern", it)
        }
        return json
    }
}

