package com.tonapps.bus.generated.flows

import androidx.annotation.AnyThread
import com.tonapps.bus.core.contract.EventExecutor
import com.tonapps.bus.generated.Events
import com.tonapps.bus.generated.Events.HomeBanner.HomeBannerAction

/**
 * Auto-generated from OpenAPI analytics schemas.
 * Do not edit manually — re-run the generator instead.
 */
class HomeBannerImpl(
    private val eventExecutor: EventExecutor,
) : Events.HomeBanner {

    private fun trackEvent(name: String, params: Map<String, Any>) {
        eventExecutor.trackEvent(name, params)
    }

    /**
     * banner_view
     *
     * Fired when a promo banner on the wallet home screen becomes the front, fully-visible item of the banner deck (an impression). The deck is a swipeable stack where only the front banner is fully visible, so only the front banner counts; deduped per appearance, so swiping back to a banner already seen in this appearance does not re-fire. Geo and user id are auto-attached by the Aptabase SDK. Together with banner_click this yields impressions, CTR (clicks / views matched on banner_id) and unique viewers / clickers (via the auto-attached user id).

     */
    @AnyThread
    override fun bannerView(bannerId: String) {
        trackEvent("banner_view", hashMapOf("banner_id" to bannerId))
    }

    /**
     * banner_click
     *
     * Fired when the user taps a home-screen banner that has an action (a deeplink or an external link). Banners without an action do not fire. CTR is banner_click / banner_view matched on banner_id; unique clickers come from the auto-attached user id. The action field is the intended destination, letting CTR and drop-off be segmented by action type without joining to the banner config.

     */
    @AnyThread
    override fun bannerClick(bannerId: String, action: HomeBannerAction) {
        trackEvent("banner_click", hashMapOf("banner_id" to bannerId, "action" to action.key))
    }
}
