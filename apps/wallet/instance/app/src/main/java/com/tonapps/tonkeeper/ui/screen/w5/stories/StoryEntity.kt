package com.tonapps.tonkeeper.ui.screen.w5.stories

import com.tonapps.tonkeeperx.R
import com.tonapps.wallet.localization.Localization

data class StoryEntity(
    val imageResId: Int,
    val titleResId: Int,
    val descriptionResId: Int,
    val showButton: Boolean = false
) {

    companion object {
        val all = listOf(
            StoryEntity(R.drawable.w5_story_1, Localization.w5_story_title_1, Localization.w5_story_description_1),
            StoryEntity(R.drawable.w5_story_2, Localization.w5_story_title_2, Localization.w5_story_description_2),
            StoryEntity(R.drawable.w5_story_3, Localization.w5_story_title_3, Localization.w5_story_description_3),
            StoryEntity(R.drawable.w5_story_4, Localization.w5_story_title_4, Localization.w5_story_description_4),
            StoryEntity(R.drawable.w5_story_5, Localization.w5_story_title_5, Localization.w5_story_description_5, true),
        )
    }
}