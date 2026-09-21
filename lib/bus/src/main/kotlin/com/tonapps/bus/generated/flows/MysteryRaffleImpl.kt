package com.tonapps.bus.generated.flows

import androidx.annotation.AnyThread
import com.tonapps.bus.core.contract.EventExecutor
import com.tonapps.bus.generated.Events
import com.tonapps.bus.generated.Events.MysteryRaffle.MysteryRaffleAction
import com.tonapps.bus.generated.Events.MysteryRaffle.MysteryRaffleKind
import com.tonapps.bus.generated.Events.MysteryRaffle.MysteryRaffleSource

/**
 * Auto-generated from OpenAPI analytics schemas.
 * Do not edit manually — re-run the generator instead.
 */
class MysteryRaffleImpl(
    private val eventExecutor: EventExecutor,
) : Events.MysteryRaffle {

    private fun trackEvent(name: String, params: Map<String, Any>) {
        eventExecutor.trackEvent(name, params)
    }

    /**
     * raffle_banner_view
     *
     * Fired when the Mystery Raffle promo banner becomes visible on one of its sources. Together with raffle_banner_click this yields per-sources CTR (clicks / views matched on source).

     */
    @AnyThread
    override fun raffleBannerView(source: MysteryRaffleSource) {
        trackEvent("raffle_banner_view", hashMapOf("source" to source.key))
    }

    /**
     * raffle_banner_click
     *
     * Fired when the user taps the Mystery Raffle promo banner.
     */
    @AnyThread
    override fun raffleBannerClick(source: MysteryRaffleSource) {
        trackEvent("raffle_banner_click", hashMapOf("source" to source.key))
    }

    /**
     * raffle_banner_dismiss
     *
     * Fired when the user closes the Mystery Raffle banner with the "x" button. The banner is only dismissible on wallets_list and trade, so source only ever carries those two values for this event.

     */
    @AnyThread
    override fun raffleBannerDismiss(source: MysteryRaffleSource) {
        trackEvent("raffle_banner_dismiss", hashMapOf("source" to source.key))
    }

    /**
     * raffle_open
     *
     * Fired when the raffle modal renders, for every variant (active, migration, won, lost) - there are no separate won/lost events, the outcome is carried by kind and, when won, prize.

     */
    @AnyThread
    override fun raffleOpen(
        source: MysteryRaffleSource,
        kind: MysteryRaffleKind,
        ticketsTotal: Int,
        prize: String?
    ) {
        val props = hashMapOf<String, Any>(
            "source" to source.key,
            "kind" to kind.key,
            "tickets_total" to ticketsTotal
        )
        prize?.let { props["prize"] = it }
        trackEvent("raffle_open", props)
    }

    /**
     * raffle_click_cta
     *
     * Fired when the user taps the bottom CTA button on the raffle modal.
     */
    @AnyThread
    override fun raffleClickCta(action: MysteryRaffleAction, kind: MysteryRaffleKind, ticketsTotal: Int) {
        val props = hashMapOf(
            "action" to action.key,
            "kind" to kind.key,
            "tickets_total" to ticketsTotal
        )
        trackEvent("raffle_click_cta", props)
    }

    /**
     * raffle_click_task
     *
     * Fired when the user taps a row in the "How to earn tickets" list on the active raffle modal.

     */
    @AnyThread
    override fun raffleClickTask(taskId: String) {
        trackEvent("raffle_click_task", hashMapOf("task_id" to taskId))
    }

    /**
     * raffle_click_milestone
     *
     * Fired when the user taps a row in the "Milestone bonuses" list on the active raffle modal.

     */
    @AnyThread
    override fun raffleClickMilestone(milestoneId: String) {
        trackEvent("raffle_click_milestone", hashMapOf("milestone_id" to milestoneId))
    }

    /**
     * raffle_click_get_more
     *
     * Fired when the user taps "Get More" on the ticket card of the raffle modal.

     */
    @AnyThread
    override fun raffleClickGetMore(kind: MysteryRaffleKind, ticketsTotal: Int) {
        trackEvent("raffle_click_get_more", hashMapOf("kind" to kind.key, "tickets_total" to ticketsTotal))
    }
}
