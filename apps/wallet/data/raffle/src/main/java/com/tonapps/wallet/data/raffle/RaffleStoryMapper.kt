package com.tonapps.wallet.data.raffle

import com.tonapps.wallet.api.entity.StoryEntity
import com.tonapps.wallet.data.raffle.entities.RaffleEntity

fun RaffleEntity.ownsStoryId(id: String): Boolean =
    this.id == id || stories.any { it.id == id }

fun RaffleEntity.toStories(id: String): StoryEntity.Stories? {
    val story = stories.firstOrNull { it.id == id }
        ?: stories.firstOrNull().takeIf { this.id == id }
        ?: return null
    return StoryEntity.Stories(
        id = story.id,
        list = story.pages.map { page ->
            StoryEntity(
                title = page.title,
                description = page.description,
                image = page.image,
                button = page.buttons.firstOrNull()?.toStoryButton(),
            )
        },
    )
}

private fun RaffleEntity.Cta.toStoryButton() = StoryEntity.Button(
    type = when (action) {
        RaffleEntity.Cta.Action.Deeplink -> "deeplink"
        RaffleEntity.Cta.Action.Link -> "link"
    },
    payload = payload,
    title = title,
)
