package com.tonapps.bus.generated.flows

import androidx.annotation.AnyThread
import com.tonapps.bus.core.contract.EventExecutor
import com.tonapps.bus.generated.Events
import com.tonapps.bus.generated.Events.Perps.PerpsAmountBucket
import com.tonapps.bus.generated.Events.Perps.PerpsEntryPoint
import com.tonapps.bus.generated.Events.Perps.PerpsErrorType
import com.tonapps.bus.generated.Events.Perps.PerpsOrderType
import com.tonapps.bus.generated.Events.Perps.PerpsScreen
import com.tonapps.bus.generated.Events.Perps.PerpsSide
import com.tonapps.bus.generated.Events.Perps.PerpsSourceScreen
import com.tonapps.bus.generated.Events.Perps.PerpsWalletMode

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
     * User entered the Perps section: the perps flow starts, on the Perps home screen or straight on a market page. Every entry sends it, the ones from outside the in-app placements with entry_point other, so each user with a later perps event has this first step. Moving around inside the section (home to a market, the search inside the section, history) does not send it again.

     */
    @AnyThread
    override fun perpsSectionOpened(entryPoint: PerpsEntryPoint) {
        trackEvent("perps_section_opened", hashMapOf("entry_point" to entryPoint.key))
    }

    /**
     * perps_deposit_started
     *
     * User opened the screen that tops up the Perps balance (moves funds from the wallet into the perps account). Sent once per opening, as soon as the screen knows how many assets it can pay from: when its list of assets loads or fails to load, or, without eligible_assets_count, when the screen is closed first. Picking another source asset on that screen does not send it again. wallet_mode is the wallet the balance is topped up from: multi for a multichain wallet, single for a TON wallet once Perps supports one.

     */
    @AnyThread
    override fun perpsDepositStarted(walletMode: PerpsWalletMode, eligibleAssetsCount: Int?) {
        val props = hashMapOf<String, Any>("wallet_mode" to walletMode.key)
        eligibleAssetsCount?.let { props["eligible_assets_count"] = it }
        trackEvent("perps_deposit_started", props)
    }

    /**
     * perps_deposit_continue_clicked
     *
     * User tapped the button under the amount on the top-up screen to go on to the confirmation. Sent on each tap while the button is on (an amount is entered, within the balance and not under the minimum the screen shows; Android also turns it off after a quote error a retry cannot fix, while iOS keeps it on and the tap asks again), whatever comes of it: the confirmation opens, or the request fails and perps_deposit_failed tells why. A later tap sends it again, also one that retries a failed quote, so count users or openings, not taps. A tap that finishes a payment signed earlier (iOS shows the button for it when the screen opens on such a payment) is not sent: it is not a new attempt, and perps_deposit_confirmed skips it too. asset is the asset the amount is paid in, as on perps_deposit_confirmed. amount_bucket is the entered amount in US dollars at the price the screen shows for the asset, as a range rather than the exact amount, which could single out the payment on chain; absent when the asset has no price.

     */
    @AnyThread
    override fun perpsDepositContinueClicked(asset: String, amountBucket: PerpsAmountBucket?) {
        val props = hashMapOf<String, Any>("asset" to asset)
        amountBucket?.let { props["amount_bucket"] = it.key }
        trackEvent("perps_deposit_continue_clicked", props)
    }

    /**
     * perps_deposit_failed
     *
     * The top-up screen or its confirmation shows the user an error that stops the deposit, or (screen = background) finds one before anything is shown. On the top-up screen (screen = amount): the quote of the entered amount failed, asked for as the amount is typed or by the tap on its button; the form itself refuses the amount the user stopped typing on, after the same 0.5 s pause that asks for a quote: over the balance (insufficient_balance) or under the minimum it shows (below_minimum); or, on Android, a payment of this wallet is still in flight (deposit_in_progress, once per opening). On the confirmation (screen = confirm): its check of the network fee, as it opens or after a fee method is picked, finds nothing that covers the fee (insufficient_network_fee); preparing the payment fails (Android); a new quote fails; or the payment fails. Sent each time such an error appears, once per refused amount on the form, so one opening can send it several times: count openings or users, not events. Not sent for a cancelled passcode or a declined review, or when the asset list fails to load (perps_deposit_started then has no eligible_assets_count). Found before it is shown (screen = background; Android only for now, iOS sends the same block as failed on the amount screen until its client adopts these values): the quote the top-up screen asks for on its own to learn the minimum of the picked asset (as it opens, and again when another asset is picked or the list is reloaded) finds that the deposit provider refuses this wallet's address (address_blocked) before the quote of a typed amount has found it for that asset. Sent at most once per opening, for the first probe that finds it; after an asset switch that can be when an amount is already typed. When a typed amount's quote finds the block first, and for the gateway asset, USDC on Ethereum, which asks no such quote (its minimum is known), the block is sent only with screen = amount. The screen shows the error as soon as an amount is typed and sends it with screen = amount, so a blocked opening can send background and then amount for the same block: count distinct users.

     */
    @AnyThread
    override fun perpsDepositFailed(screen: PerpsScreen, errorType: PerpsErrorType) {
        trackEvent("perps_deposit_failed", hashMapOf("screen" to screen.key, "error_type" to errorType.key))
    }

    /**
     * perps_deposit_confirmed
     *
     * User confirmed a top-up of the Perps balance and the app paid it: sent once the payment has left the wallet. Not sent when the confirmation is cancelled at the passcode or the payment fails before it is sent, and not sent when the app finishes a payment signed earlier (the user confirms it again from the resume notice): that payment keeps its first operation_id. The amount, and whether and when the funds were credited, come from the perps backend, joined on operation_id. asset is the asset paid from the wallet, e.g. ton/mainnet/coin or eth/mainnet/erc20/{address}.

     */
    @AnyThread
    override fun perpsDepositConfirmed(sourceScreen: PerpsSourceScreen, asset: String, operationId: String) {
        val props = hashMapOf(
            "source_screen" to sourceScreen.key,
            "asset" to asset,
            "operation_id" to operationId
        )
        trackEvent("perps_deposit_confirmed", props)
    }

    /**
     * perps_order_submitted
     *
     * User confirmed an order that opens a perps position, and the app sent its request to the Perps service: sent once the request has gone out, also when the app could not confirm that it arrived. A failure before the send (preparing or signing the order, a cancelled passcode, an account that still needs activation) sends nothing. A retry after the app reports the order as not placed is a new attempt and sends it again once its request goes out. Only orders that open a position: changes to an open one (add, reduce, close, margin, take profit / stop loss) are not sent. The margin, and whether the order was filled or refused, come from the perps backend, joined on client_order_index. asset is the market, lighter/mainnet/market/{market_index}.

     */
    @AnyThread
    override fun perpsOrderSubmitted(
        side: PerpsSide,
        asset: String,
        leverage: Double,
        orderType: PerpsOrderType,
        clientOrderIndex: Long
    ) {
        val props = hashMapOf(
            "side" to side.key,
            "asset" to asset,
            "leverage" to leverage,
            "order_type" to orderType.key,
            "client_order_index" to clientOrderIndex
        )
        trackEvent("perps_order_submitted", props)
    }

    /**
     * perps_trading_data_error
     *
     * The market data of a perps asset page failed to load, and the page shows its load error (with Retry) instead of the market. Sent each time the page switches to this error, e.g. again after a Retry that fails. A refresh that fails after the page has loaded, the chart failing on its own and an unknown market are not sent. asset is the market, lighter/mainnet/market/{market_index}.

     */
    @AnyThread
    override fun perpsTradingDataError(asset: String) {
        trackEvent("perps_trading_data_error", hashMapOf("asset" to asset))
    }
}
