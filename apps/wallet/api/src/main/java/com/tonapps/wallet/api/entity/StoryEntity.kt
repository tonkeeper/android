package com.tonapps.wallet.api.entity

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import org.json.JSONObject

@Parcelize
data class StoryEntity(
    val title: String,
    val description: String,
    val image: String,
    val button: Button?,
    val links: List<Link> = emptyList()
): Parcelable {

    @Parcelize
    data class Stories(
        val id: String,
        val list: List<StoryEntity>,
        val isAutoShow: Boolean = false
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

    @Parcelize
    data class Link(
        val text: String,
        val url: String
    ): Parcelable {

        constructor(json: JSONObject) : this(
            text = (json.opt("text") as? String).orEmpty(),
            url = (json.opt("url") as? String).orEmpty()
        )
    }

    constructor(json: JSONObject) : this(
        title = json.getString("title"),
        description = json.getString("description"),
        image = json.getString("image"),
        button = json.optJSONObject("button")?.let { Button(it) },
        links = json.optJSONArray("links")
            ?.let { array -> (0 until array.length()).mapNotNull { array.optJSONObject(it)?.let(::Link) } }
            ?.filter { it.text.isNotBlank() && it.url.isNotBlank() }
            .orEmpty()
    )
}
