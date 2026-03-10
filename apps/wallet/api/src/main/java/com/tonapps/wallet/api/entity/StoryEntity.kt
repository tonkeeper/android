package com.tonapps.wallet.api.entity

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import org.json.JSONObject

@Parcelize
data class StoryEntity(
    val title: String,
    val description: String,
    val image: String,
    val button: Button?
): Parcelable {

    @Parcelize
    data class Stories(
        val id: String,
        val list: List<StoryEntity>
    ): Parcelable

    @Parcelize
    data class Button(
        val type: String,
        val payload: String,
        val title: String
    ): Parcelable {

        constructor(json: JSONObject) : this(
            type = json.getString("type"),
            payload = json.getString("payload"),
            title = json.getString("title")
        )
    }

    constructor(json: JSONObject) : this(
        title = json.getString("title"),
        description = json.getString("description"),
        image = json.getString("image"),
        button = json.optJSONObject("button")?.let { Button(it) }
    )
}
