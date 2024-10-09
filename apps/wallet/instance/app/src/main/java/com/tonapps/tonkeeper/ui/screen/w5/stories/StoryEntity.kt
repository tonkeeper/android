package com.tonapps.tonkeeper.ui.screen.w5.stories

import com.facebook.common.util.UriUtil
import com.facebook.drawee.backends.pipeline.Fresco
import com.facebook.imagepipeline.request.ImageRequest
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
            StoryEntity(R.drawable.w5_story_3, Localization.w5_story_title_3, Localization.w5_story_description_3),
            StoryEntity(R.drawable.w5_story_4, Localization.w5_story_title_4, Localization.w5_story_description_4, showButton = true),
        )

        fun prefetchImages() {
            for (story in all) {
                Fresco.getImagePipeline().prefetchToBitmapCache(ImageRequest.fromUri(UriUtil.getUriForResourceId(story.imageResId)), null)
            }
        }
    }
}