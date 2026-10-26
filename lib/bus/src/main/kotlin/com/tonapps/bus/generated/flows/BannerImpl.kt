package com.tonapps.bus.generated.flows

import androidx.annotation.AnyThread
import com.tonapps.bus.core.contract.EventExecutor
import com.tonapps.bus.generated.Events
import com.tonapps.bus.generated.Events.Banner.BannerAction
import com.tonapps.bus.generated.Events.Banner.BannerFrom
import com.tonapps.bus.generated.Events.Banner.BannerWalletMode

/**
 * Auto-generated from OpenAPI analytics schemas.
 * Do not edit manually — re-run the generator instead.
 */
class BannerImpl(
    private val eventExecutor: EventExecutor,
) : Events.Banner {

    private fun trackEvent(name: String, params: Map<String, Any>) {
        eventExecutor.trackEvent(name, params)
    }

    /**
     * banner_view
     *
     * Fired when a promo banner becomes the front, fully-visible item of the banner deck (an impression). The deck is a swipeable stack where only the front banner is fully visible, so only the front banner counts; deduped per appearance, so swiping back to a banner already seen in this appearance does not re-fire. The deck is rendered on the wallet main screen and in the Browser section — `from` says which surface fired. Geo and user id are auto-attached by the Aptabase SDK. Together with banner_click this yields impressions, CTR (clicks / views matched on banner_id) and unique viewers / clickers (via the auto-attached user id). `wallet_mode` distinguishes TON-only from multichain wallets, matching `wallet_open`.

     */
    @AnyThread
    override fun bannerView(bannerId: String, walletMode: BannerWalletMode, from: BannerFrom) {
        val props = hashMapOf(
            "banner_id" to bannerId,
            "wallet_mode" to walletMode.key,
            "from" to from.key
        )
        trackEvent("banner_view", props)
    }

    /**
     * banner_click
     *
     * Fired when the user taps a banner that has an action (a deeplink or an external link). Banners without an action do not fire. CTR is banner_click / banner_view matched on banner_id; unique clickers come from the auto-attached user id. The action field is the intended destination, letting CTR and drop-off be segmented by action type without joining to the banner config. `wallet_mode` distinguishes TON-only from multichain wallets, matching `wallet_open`; `from` says whether the banner was on the wallet main screen or in the Browser section.

     */
    @AnyThread
    override fun bannerClick(
        bannerId: String,
        action: BannerAction,
        walletMode: BannerWalletMode,
        from: BannerFrom
    ) {
        val props = hashMapOf(
            "banner_id" to bannerId,
            "action" to action.key,
            "wallet_mode" to walletMode.key,
            "from" to from.key
        )
        trackEvent("banner_click", props)
    }
}
