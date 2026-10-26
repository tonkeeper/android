package com.tonapps.bus.generated.flows

import androidx.annotation.AnyThread
import com.tonapps.bus.core.contract.EventExecutor
import com.tonapps.bus.generated.Events
import com.tonapps.bus.generated.Events.Perps.PerpsEntryPoint
import com.tonapps.bus.generated.Events.Perps.PerpsOrderType
import com.tonapps.bus.generated.Events.Perps.PerpsReason
import com.tonapps.bus.generated.Events.Perps.PerpsSide
import com.tonapps.bus.generated.Events.Perps.PerpsWalletType

/**
 * Auto-generated from OpenAPI analytics schemas.
 * Do not edit manually — re-run the generator instead.
 */
class PerpsImpl(
    private val eventExecutor: EventExecutor,
) : Events.Perps {

    private fun trackEvent(name: String, params: Map<String, Any>) {
        eventExecutor.trackEvent(name, params)
    }

    /**
     * perps_section_opened
     *
     * User entered the Perps section from one of its in-app entry points: the perps flow starts, on the Perps home screen or straight on a market page. Moving around inside the section (home to a market, the search inside the section, history) does not send it again. Opens from outside these placements (an external link, a push, a QR scan, a story button) are not sent.

     */
    @AnyThread
    override fun perpsSectionOpened(entryPoint: PerpsEntryPoint) {
        trackEvent("perps_section_opened", hashMapOf("entry_point" to entryPoint.key))
    }

    /**
     * perps_deposit_started
     *
     * User opened the screen that tops up the Perps balance (moves funds from the wallet into the perps account). Sent once per opening: picking another source asset on that screen does not send it again.

     */
    @AnyThread
    override fun perpsDepositStarted(walletType: PerpsWalletType) {
        trackEvent("perps_deposit_started", hashMapOf("wallet_type" to walletType.key))
    }

    /**
     * perps_order_submitted
     *
     * User confirmed an order that opens a perps position, and the app started placing it: sent after the confirmation (and after the one-time activation of the perps account, when it was still needed), before the order is prepared, signed and sent to the venue, so every perps_order_failed of the same attempt follows one of these. A retry after the app reports the order as not placed is a new attempt and sends it again. Only orders that open a position: changes to an open one (add, reduce, close, margin, take profit / stop loss) are not sent.

     */
    @AnyThread
    override fun perpsOrderSubmitted(
        side: PerpsSide,
        asset: String,
        leverage: Double,
        margin: Double,
        orderType: PerpsOrderType
    ) {
        val props = hashMapOf(
            "side" to side.key,
            "asset" to asset,
            "leverage" to leverage,
            "margin" to margin,
            "order_type" to orderType.key
        )
        trackEvent("perps_order_submitted", props)
    }

    /**
     * perps_order_failed
     *
     * An order that opens a perps position did not go through: refused before it was sent, refused when sent, or rejected by the venue afterwards. Always follows the perps_order_submitted of the same attempt. Not sent when the account activation before it is cancelled or fails (no perps_order_submitted is sent then either), or when the outcome is still unknown after the app stopped waiting for it.

     */
    @AnyThread
    override fun perpsOrderFailed(reason: PerpsReason) {
        trackEvent("perps_order_failed", hashMapOf("reason" to reason.key))
    }

    /**
     * perps_trading_data_error
     *
     * The market data of a perps asset page failed to load, and the page shows its load error (with Retry) instead of the market. Sent each time the page switches to this error, e.g. again after a Retry that fails. A refresh that fails after the page has loaded, the chart failing on its own and an unknown market are not sent.

     */
    @AnyThread
    override fun perpsTradingDataError(asset: String) {
        trackEvent("perps_trading_data_error", hashMapOf("asset" to asset))
    }
}
