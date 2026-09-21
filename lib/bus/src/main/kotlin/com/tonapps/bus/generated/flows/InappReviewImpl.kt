package com.tonapps.bus.generated.flows

import androidx.annotation.AnyThread
import com.tonapps.bus.core.contract.EventExecutor
import com.tonapps.bus.generated.Events
import com.tonapps.bus.generated.Events.InappReview.InappReviewAction

/**
 * Auto-generated from OpenAPI analytics schemas.
 * Do not edit manually — re-run the generator instead.
 */
class InappReviewImpl(
    private val eventExecutor: EventExecutor,
) : Events.InappReview {

    private fun trackEvent(name: String, params: Map<String, Any>) {
        eventExecutor.trackEvent(name, params)
    }

    /**
     * inapp_review
     *
     * Sent when user prompted for mobile store review
     */
    @AnyThread
    override fun inappReview(action: InappReviewAction?) {
        val props = mutableMapOf<String, Any>()
        action?.let { props["action"] = it.key }
        trackEvent("inapp_review", props)
    }
}
