package com.tonapps.bus.generated.flows

import androidx.annotation.AnyThread
import com.tonapps.bus.core.contract.EventExecutor
import com.tonapps.bus.generated.Events
import com.tonapps.bus.generated.Events.TwaSunset.TwaSunsetDestination
import com.tonapps.bus.generated.Events.TwaSunset.TwaSunsetTelegramPlatform

/**
 * Auto-generated from OpenAPI analytics schemas.
 * Do not edit manually — re-run the generator instead.
 */
class TwaSunsetImpl(
    private val eventExecutor: EventExecutor,
) : Events.TwaSunset {

    private fun trackEvent(name: String, params: Map<String, Any>) {
        eventExecutor.trackEvent(name, params)
    }

    /**
     * twa_sunset_open
     *
     * Sent once per session when the deprecated Telegram Mini App sunset screen ("Mini App closed") renders. The primary reach metric for the wind-down - distinct sessions and users still opening the mini app.

     */
    @AnyThread
    override fun twaSunsetOpen(
        telegramPlatform: TwaSunsetTelegramPlatform?,
        hasWallets: Boolean?,
        walletsCount: Int?
    ) {
        val props = mutableMapOf<String, Any>()
        telegramPlatform?.let { props["telegram_platform"] = it.key }
        hasWallets?.let { props["has_wallets"] = it }
        walletsCount?.let { props["wallets_count"] = it }
        trackEvent("twa_sunset_open", props)
    }

    /**
     * twa_sunset_download_click
     *
     * Sent when the user taps the primary button to leave the mini app for a full Tonkeeper client.

     */
    @AnyThread
    override fun twaSunsetDownloadClick(destination: TwaSunsetDestination) {
        trackEvent("twa_sunset_download_click", hashMapOf("destination" to destination.key))
    }

    /**
     * twa_sunset_reveal_start
     *
     * Sent when the user selects a wallet to reveal its recovery phrase, opening the password gate. Funnel entry for seed recovery.

     */
    @AnyThread
    override fun twaSunsetRevealStart() {
        trackEvent("twa_sunset_reveal_start", emptyMap())
    }

    /**
     * twa_sunset_reveal_success
     *
     * Sent when the password is accepted and the recovery phrase is shown. Funnel completion for seed recovery.

     */
    @AnyThread
    override fun twaSunsetRevealSuccess() {
        trackEvent("twa_sunset_reveal_success", emptyMap())
    }

    /**
     * twa_sunset_sign_out
     *
     * Sent when the user removes a wallet from the mini app after confirming they have backed up its recovery phrase.

     */
    @AnyThread
    override fun twaSunsetSignOut() {
        trackEvent("twa_sunset_sign_out", emptyMap())
    }
}
