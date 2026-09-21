package com.tonapps.bus.generated.flows

import androidx.annotation.AnyThread
import com.tonapps.bus.core.contract.EventExecutor
import com.tonapps.bus.generated.Events
import com.tonapps.bus.generated.Events.Stories.StoriesButtonType
import com.tonapps.bus.generated.Events.Stories.StoriesFrom

/**
 * Auto-generated from OpenAPI analytics schemas.
 * Do not edit manually — re-run the generator instead.
 */
class StoriesImpl(
    private val eventExecutor: EventExecutor,
) : Events.Stories {

    private fun trackEvent(name: String, params: Map<String, Any>) {
        eventExecutor.trackEvent(name, params)
    }

    /**
     * story_open
     *
     * A user opened an in-app Story.
     */
    @AnyThread
    override fun storyOpen(storyId: String, from: StoriesFrom) {
        trackEvent("story_open", hashMapOf("story_id" to storyId, "from" to from.key))
    }

    /**
     * story_page_view
     *
     * A single page / slide within a Story was viewed. Fires once per page, so a single story_open is typically followed by several story_page_view events. NOTE: only story_id is carried — the page index / position within the story is NOT recorded, so individual pages cannot be distinguished from this event today (a candidate for adding a page_index property).

     */
    @AnyThread
    override fun storyPageView(storyId: String) {
        trackEvent("story_page_view", hashMapOf("story_id" to storyId))
    }

    /**
     * story_click
     *
     * A call-to-action button on a Story page was tapped. Only fires for stories that have CTA buttons (a subset of all stories).

     */
    @AnyThread
    override fun storyClick(
        storyId: String,
        buttonType: StoriesButtonType,
        buttonPayload: String,
        buttonTitle: String
    ) {
        val props = hashMapOf(
            "story_id" to storyId,
            "button_type" to buttonType.key,
            "button_payload" to buttonPayload,
            "button_title" to buttonTitle
        )
        trackEvent("story_click", props)
    }
}
