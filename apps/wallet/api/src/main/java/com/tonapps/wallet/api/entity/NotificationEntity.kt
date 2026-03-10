package com.tonapps.wallet.api.entity

import org.json.JSONObject

data class NotificationEntity(
    val id: String,
    val title: String,
    val caption: String,
    val mode: String,
    val action: ActionEntity?
) {

    data class ActionEntity(
        val type: String,
        val label: String,
        val url: String,
    ) {

        constructor(json: JSONObject) : this(
            type = json.getString("type"),
            label = json.getString("label"),
            url = json.getString("url")
        )
    }

    constructor(json: JSONObject) : this(
        id = json.getString("id"),
        title = json.getString("title"),
        caption = json.getString("caption"),
        mode = json.getString("mode"),
        action = json.optJSONObject("action")?.let { ActionEntity(it) }
    )
}
