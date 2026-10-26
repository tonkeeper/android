package com.tonapps.bus.generated.flows

import androidx.annotation.AnyThread
import com.tonapps.bus.core.contract.EventExecutor
import com.tonapps.bus.generated.Events
import com.tonapps.bus.generated.Events.DeeplinkOpen.DeeplinkOpenFrom
import com.tonapps.bus.generated.Events.DeeplinkOpen.DeeplinkOpenLinkType

/**
 * Auto-generated from OpenAPI analytics schemas.
 * Do not edit manually — re-run the generator instead.
 */
class DeeplinkOpenImpl(
    private val eventExecutor: EventExecutor,
) : Events.DeeplinkOpen {

    private fun trackEvent(name: String, params: Map<String, Any>) {
        eventExecutor.trackEvent(name, params)
    }

    /**
     * deeplink_open
     *
     * A link opened the app: a `tonkeeper://` / `ton://` app link, or a universal `https://app.tonkeeper.com/...` link — either launching the app or arriving while it was already running. Sent once per link, before the app routes it, so links the app cannot use are visible too (`link_type: unknown`). A push tap is not counted here: it reports `push_click`, and links opened from inside the app (banners, stories, dapps) report their own placement events.

     */
    @AnyThread
    override fun deeplinkOpen(from: DeeplinkOpenFrom, linkType: DeeplinkOpenLinkType, isColdStart: Boolean) {
        val props = hashMapOf(
            "from" to from.key,
            "link_type" to linkType.key,
            "is_cold_start" to isColdStart
        )
        trackEvent("deeplink_open", props)
    }
}
