package com.tonapps.wallet.api.entity

import android.os.Parcelable
import io.tonendpointapi.models.StoryPage
import kotlinx.parcelize.Parcelize

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
    ): Parcelable

    @Parcelize
    data class Link(
        val text: String,
        val url: String
    ): Parcelable

    constructor(page: StoryPage) : this(
        title = page.title,
        description = page.description,
        image = page.image,
        button = page.button?.let { Button(it.type, it.payload, it.title) },
        links = page.links.orEmpty()
            .map { Link(it.text.orEmpty(), it.url.orEmpty()) }
            .filter { it.text.isNotBlank() && it.url.isNotBlank() }
    )
}
