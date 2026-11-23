package com.tonapps.deposit.multicoin.analytics

import com.tonapps.bus.core.contract.UtmParameters
import com.tonapps.bus.generated.Events.DepositFlow.DepositFlowAddFundsOption
import com.tonapps.bus.generated.Events.DepositFlow.DepositFlowDepositScreen
import com.tonapps.bus.generated.Events.DepositFlow.DepositFlowFailureReason
import com.tonapps.bus.generated.Events.DepositFlow.DepositFlowFrom
import com.tonapps.bus.generated.Events.DepositFlow.DepositFlowOutcome
import com.tonapps.bus.generated.Events.DepositFlow.DepositFlowProviderMode
import com.tonapps.bus.generated.Events.DepositFlow.DepositFlowWalletMode
import com.tonapps.deposit.multicoin.domain.RampOffer
import com.tonapps.deposit.multicoin.domain.RampPaymentMethod
import com.tonapps.deposit.multicoin.domain.RampTicket
import com.tonapps.deposit.screens.ramp.RampType
import kotlinx.serialization.Serializable

// What a step of a purchase on «Buy crypto» was for: [provider] is the merchant's name or MoonPay. [mode] tells MoonPay's
// checkout in the app (`headless`) from a page that opens outside it (`merchant`, MoonPay's widget included).
@Serializable
data class BuyCryptoPurchase(
    val assetId: String,
    val fiat: String,
    val paymentMethod: String,
    val provider: String,
    val mode: DepositFlowProviderMode,
) {
    fun withMode(mode: DepositFlowProviderMode): BuyCryptoPurchase = copy(mode = mode)

    // Continue signs in to MoonPay first and pays in the app, whatever merchant quotes meanwhile, as on iOS.
    fun signingInToMoonPay(): BuyCryptoPurchase = copy(provider = BuyCryptoAnalytics.MOONPAY, mode = DepositFlowProviderMode.Headless)
}

// What MoonPay's status sheet needs to report how the purchase it shows ended.
@Serializable
data class BuyCryptoReport(
    val from: DepositFlowFrom,
    val utm: UtmParameters,
    val purchase: BuyCryptoPurchase,
)

sealed interface BuyCryptoTrack {
    data class AmountShown(val purchase: BuyCryptoPurchase) : BuyCryptoTrack
    data class Continued(val purchase: BuyCryptoPurchase) : BuyCryptoTrack
    data class ToProvider(val purchase: BuyCryptoPurchase, val txId: String) : BuyCryptoTrack
    data object ToP2P : BuyCryptoTrack
}

// «Buy crypto» reports through the deposit flow, as the buying screens it replaces do, so both compare in one funnel.
class BuyCryptoAnalytics(from: DepositFlowFrom, utm: UtmParameters) {

    private val deposit = DepositAnalytics(
        RampType.RampOn,
        from,
        DepositFlowWalletMode.Multi,
        utm,
        screen = DepositFlowDepositScreen.BuyCrypto,
    )

    fun started() {
        deposit.started(listOf(DepositFlowAddFundsOption.BuyWithFiat))
    }

    fun methodsShown(assetId: String, fiat: String, methods: List<RampPaymentMethod>) {
        deposit.viewFiatPaymentMethod(assetId, fiat, methods.map(::paymentMethod).distinct())
    }

    fun methodPicked(assetId: String, fiat: String, method: RampPaymentMethod) {
        deposit.clickFiatPaymentMethod(assetId, fiat, paymentMethod(method))
    }

    fun result(purchase: BuyCryptoPurchase, txId: String, outcome: DepositFlowOutcome, failure: DepositFlowFailureReason?) {
        with(purchase) {
            deposit.rampResult(assetId, fiat, paymentMethod, provider, mode, txId, outcome, failure)
        }
    }

    fun track(track: BuyCryptoTrack) {
        when (track) {
            is BuyCryptoTrack.AmountShown -> with(track.purchase) {
                deposit.viewRampInsertAmount(assetId, fiat, paymentMethod, provider, mode)
            }
            is BuyCryptoTrack.Continued -> with(track.purchase) {
                deposit.clickRampInsertAmountContinue(assetId, fiat, paymentMethod, provider, mode)
            }
            is BuyCryptoTrack.ToProvider -> with(track.purchase) {
                deposit.continueToRampProvider(assetId, fiat, paymentMethod, provider, mode, track.txId)
            }
            is BuyCryptoTrack.ToP2P -> deposit.continueToP2pMarket(DepositFlowAddFundsOption.BuyWithFiat)
        }
    }

    companion object {

        const val MOONPAY = "MoonPay"

        // The backend's slug for a merchant's method; MoonPay's own card is `card`, or `saved_card` for a card it keeps.
        fun paymentMethod(method: RampPaymentMethod): String {
            return when (val details = method.details) {
                is RampPaymentMethod.Details.Merchant -> details.method.type.value
                is RampPaymentMethod.Details.MoonPay -> when {
                    method.card != null -> "saved_card"
                    else -> details.type
                }
            }
        }

        fun provider(method: RampPaymentMethod, offer: RampOffer.Available?): String? {
            return when (val ticket = offer?.ticket) {
                is RampTicket.Merchant -> ticket.title
                is RampTicket.MoonPay -> MOONPAY
                null -> MOONPAY.takeIf { method.isMoonPay }
            }
        }

        // As [provider]: the offer's ticket, which Continue pays by. The checkout, once known, decides instead.
        fun mode(method: RampPaymentMethod, offer: RampOffer.Available?): DepositFlowProviderMode? {
            return when (offer?.ticket) {
                is RampTicket.Merchant -> DepositFlowProviderMode.Merchant
                is RampTicket.MoonPay -> DepositFlowProviderMode.Headless
                null -> DepositFlowProviderMode.Headless.takeIf { method.isMoonPay }
            }
        }

        fun purchase(assetId: String?, fiat: String, method: RampPaymentMethod?, offer: RampOffer.Available?): BuyCryptoPurchase? {
            method ?: return null
            return BuyCryptoPurchase(
                assetId = assetId ?: return null,
                fiat = fiat,
                paymentMethod = paymentMethod(method),
                provider = provider(method, offer) ?: return null,
                mode = mode(method, offer) ?: return null,
            )
        }
    }
}
