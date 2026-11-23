package com.tonapps.tonkeeper.ui.screen.stories.remote

import com.tonapps.perps.screens.common.PERPS_STORY_ID
import com.tonapps.wallet.api.entity.StoryEntity

fun perpsCloseNeedsConfirm(stories: StoryEntity.Stories, isGate: Boolean, termsAccepted: Boolean): Boolean =
    isGate && !termsAccepted && isPerpsAgreement(stories, stories.list.lastIndex)

fun isPerpsAgreement(stories: StoryEntity.Stories, index: Int): Boolean =
    stories.id == PERPS_STORY_ID &&
        index == stories.list.lastIndex &&
        stories.list.getOrNull(index)?.button?.title?.isNotBlank() == true

fun StoryEntity.Stories.withoutPerpsAgreement(): StoryEntity.Stories? =
    if (!isPerpsAgreement(this, list.lastIndex)) {
        this
    } else if (list.size > 1) {
        copy(list = list.dropLast(1))
    } else {
        null
    }
