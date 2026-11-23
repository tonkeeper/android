package com.tonapps.deposit.multicoin.domain

import com.tonapps.deposit.multicoin.data.RampMethod
import com.tonapps.deposit.multicoin.moonpay.api.MOONPAY_CARD_TYPE
import com.tonapps.deposit.multicoin.moonpay.api.MoonPayAsset
import com.tonapps.deposit.multicoin.moonpay.api.MoonPayBuyQuote
import com.tonapps.deposit.multicoin.screens.moonpay.MoonPayPaymentData
import io.exchangeapi.models.ExchangeMerchantSlug
import java.math.BigDecimal
import kotlin.time.Duration

interface RampPayments {

    // Null: the provider has no method of its own for the asset, and the merchants take over.
    suspend fun loadMethods(assetId: String, symbol: String, fiat: String): Result<List<RampPaymentMethod>?>

    suspend fun loadAmountRange(method: RampPaymentMethod, fiat: String): Result<RampAmountRange>

    suspend fun requestOffer(request: RampQuoteRequest): Result<RampOffer>

    // [offer] is null only for a method that needs none to go on, a new MoonPay card.
    suspend fun checkout(method: RampPaymentMethod, offer: RampOffer.Available?): Result<RampCheckout>

    // Removes a saved card ([RampSavedCard.id]); only a provider that keeps cards has any.
    suspend fun removeCard(id: String): Result<Unit>

    // The payment a provider's own buy button runs for [quote] in place of Continue (MoonPay's Google Pay); null when
    // the provider pays through Continue.
    fun buttonPayment(quote: MoonPayBuyQuote, title: String, externalTransactionId: String): MoonPayPaymentData?
}

data class RampPaymentMethod(
    val id: String,
    val title: String,
    val subtitle: String?,
    val iconUrl: String?,
    val badge: String?,
    val details: Details,
    val card: RampSavedCard? = null,
) {

    val isP2P: Boolean
        get() = id == P2P_ID

    val isMoonPay: Boolean
        get() = details is Details.MoonPay

    // A card that MoonPay no longer takes (expired, blocked) is shown but cannot be picked.
    val selectable: Boolean
        get() = card?.active != false

    // MoonPay's card method without a saved card: Continue adds one.
    val isNewCard: Boolean
        get() = (details as? Details.MoonPay)?.let { it.type == MOONPAY_CARD_TYPE && it.cardId == null } == true

    sealed interface Details {
        data class Merchant(val method: RampMethod) : Details

        // [cardId] is the saved card a card payment goes with; none means a new card.
        data class MoonPay(
            val type: String,
            val assetId: String,
            val destination: MoonPayAsset,
            val cardId: String? = null,
            val cardType: String? = null,
        ) : Details
    }

    companion object {
        const val P2P_ID = "p2p"
    }
}

// A card MoonPay keeps for the customer. [kind] is MoonPay's credit or debit; [removable] is whether MoonPay lets the
// customer remove it.
data class RampSavedCard(
    val id: String,
    val kind: String?,
    val brand: String?,
    val last4: String?,
    val expiryMonth: Int?,
    val expiryYear: Int?,
    val active: Boolean,
    val removable: Boolean,
)

// A saved card's method id: the card method's own id is a new card.
fun savedCardId(cardId: String): String = "$MOONPAY_CARD_TYPE:$cardId"

// The requested method if it can be picked, else the first of the provider's own: a saved card only stands in for
// the card method when that comes first, as on iOS.
fun List<RampPaymentMethod>.preferred(requested: String?): RampPaymentMethod? {
    firstOrNull { it.id == requested && !it.isP2P && it.selectable }?.let { return it }
    val first = firstOrNull { it.card == null } ?: return firstOrNull { it.selectable }
    if (first.isNewCard) {
        firstOrNull { it.card?.active == true }?.let { return it }
    }
    return first
}

data class RampQuoteRequest(
    val assetId: String,
    val fiat: String,
    val method: RampPaymentMethod,
    val amount: BigDecimal,
)

sealed interface RampOffer {

    data class Available(
        val request: RampQuoteRequest,
        val receive: BigDecimal,
        val rate: BigDecimal?,
        val lifetime: Duration?,
        val badgeUrl: String?,
        val ticket: RampTicket,
    ) : RampOffer

    data class OutOfLimits(
        val badgeUrl: String?,
        val violation: RampLimitViolation,
    ) : RampOffer
}

sealed interface RampTicket {
    // [title] is the merchant's name as the backend gives it, the slug when it gives none.
    data class Merchant(val slug: ExchangeMerchantSlug, val merchantTransactionId: String, val title: String) : RampTicket
    data class MoonPay(val quote: MoonPayBuyQuote) : RampTicket
}

sealed interface RampCheckout {
    data class OpenUrl(val url: String) : RampCheckout
    data class Pay(val data: MoonPayPaymentData) : RampCheckout

    data object Reload : RampCheckout

    // A new card was added: the screen loads again and pays with it.
    data class CardAdded(val cardId: String) : RampCheckout

    // A challenge failed: the screen shows it and loads again.
    data object ReloadAfterFailure : RampCheckout

    data object Cancelled : RampCheckout
}

// [suggested] is put in an empty amount field; [max] is the most the method takes, when known up front.
data class RampAmountRange(
    val suggested: BigDecimal?,
    val max: BigDecimal?,
)
