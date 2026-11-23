package com.tonapps.deposit.multicoin.domain

import com.tonapps.deposit.multicoin.moonpay.api.MoonPayBuyQuote
import com.tonapps.deposit.multicoin.screens.moonpay.MoonPayPaymentData
import com.tonapps.deposit.data.ExchangeRepository
import com.tonapps.deposit.multicoin.catchingResult
import com.tonapps.deposit.multicoin.data.RampMethod
import com.tonapps.deposit.multicoin.data.RampQuote
import com.tonapps.deposit.multicoin.data.RampRepository
import com.tonapps.deposit.multicoin.fiatScale
import com.tonapps.deposit.screens.ramp.RampType
import io.exchangeapi.models.ExchangeMerchantInfo
import io.exchangeapi.models.ExchangeMerchantSlug
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

class MerchantPayments(
    private val walletId: String,
    private val rampRepository: RampRepository,
    private val exchangeRepository: ExchangeRepository,
    private val walletAddress: RampWalletAddress,
) : RampPayments {

    override suspend fun loadMethods(assetId: String, symbol: String, fiat: String): Result<List<RampPaymentMethod>?> = catchingResult {
        val asset = nullIfNotSold { rampRepository.getAsset(walletId, RampType.RampOn, assetId, fiat) } ?: return@catchingResult emptyList()
        asset.methods
            .filter { method -> method.providers.any { it.fiat == fiat } }
            .sortedBy { it.isP2P }
            .map { it.toPaymentMethod() }
    }

    // As on iOS, the suggestion is the minimum of the merchant first in line for an empty amount. Both are
    // put at the fiat's precision on the side the merchants accept.
    override suspend fun loadAmountRange(method: RampPaymentMethod, fiat: String): Result<RampAmountRange> = catchingResult {
        val scale = fiatScale(fiat)
        val limits = RampLimits(method.merchantMethod.providers.filter { it.fiat == fiat }, quotes = null, scale)
        val merchant = limits.bestMerchant(BigDecimal.ZERO)
        RampAmountRange(
            suggested = merchant?.let { limits.limits(it)?.min }?.setScale(scale, RoundingMode.UP),
            max = limits.ceiling?.setScale(scale, RoundingMode.DOWN),
        )
    }

    override suspend fun requestOffer(request: RampQuoteRequest): Result<RampOffer> = catchingResult {
        val scale = fiatScale(request.fiat)
        val amount = request.amount.setScale(scale, RoundingMode.UP)
        require(amount.signum() > 0) { "No amount to quote" }
        val method = request.method.merchantMethod
        val providers = method.providers.filter { it.fiat == request.fiat }
        val withoutQuotes = RampLimits(providers, quotes = null, scale)
        if (!withoutQuotes.hasServiceableMerchant(amount)) {
            return@catchingResult outOfLimits(withoutQuotes, amount)
        }
        val quotes = rampRepository.getQuotes(
            walletId = walletId,
            rampType = RampType.RampOn,
            assetId = request.assetId,
            fiat = request.fiat,
            amount = amount.toPlainString(),
            paymentMethod = method.type,
        ).let { it.copy(items = it.items.filter(::hasReceive), suggested = it.suggested.filter(::hasReceive)) }
        val limits = RampLimits(providers, quotes, scale)
        val best = limits.bestMerchant(amount)
        if (best == null || !limits.canServe(best, amount)) {
            return@catchingResult outOfLimits(limits, amount)
        }
        val quote = requireNotNull(quotes.items.firstOrNull { it.merchant == best }) { "No quote from $best for $amount" }
        val paid = quote.amountIn.toBigDecimalOrNull()
        val receive = quote.amountOut.toBigDecimal()
        val merchant = exchangeRepository.merchant(walletId, quote.merchant)
        RampOffer.Available(
            request = request.copy(amount = amount),
            receive = receive,
            rate = quote.rate.toBigDecimalOrNull()?.takeIf { it.signum() > 0 }
                ?: paid?.takeIf { it.signum() > 0 }?.let { receive.divide(it, MathContext.DECIMAL64) },
            lifetime = null,
            badgeUrl = merchant?.image?.takeIf { it.isNotBlank() },
            ticket = RampTicket.Merchant(quote.merchant, quote.merchantTransactionId, merchant?.title?.takeIf { it.isNotBlank() } ?: quote.merchant.value),
        )
    }

    override suspend fun checkout(method: RampPaymentMethod, offer: RampOffer.Available?): Result<RampCheckout> = catchingResult {
        val request = requireNotNull(offer) { "No offer to order" }.request
        val ticket = requireNotNull(offer.ticket as? RampTicket.Merchant) { "Not a merchant offer" }
        val url = rampRepository.createOrder(
            walletId = walletId,
            rampType = RampType.RampOn,
            assetId = request.assetId,
            fiat = request.fiat,
            amount = request.amount.toPlainString(),
            address = walletAddress.of(walletId, request.assetId),
            paymentMethod = request.method.merchantMethod.type,
            merchant = ticket.slug,
            merchantTransactionId = ticket.merchantTransactionId,
        )
        RampCheckout.OpenUrl(url)
    }

    override suspend fun removeCard(id: String): Result<Unit> {
        return Result.failure(UnsupportedOperationException("The merchants keep no cards"))
    }

    // A merchant's widget takes every payment.
    override fun buttonPayment(quote: MoonPayBuyQuote, title: String, externalTransactionId: String): MoonPayPaymentData? = null

    private suspend fun outOfLimits(limits: RampLimits, amount: BigDecimal): RampOffer {
        val nearest = limits.bestMerchant(amount)
        val violation = requireNotNull(limits.violation(nearest, amount) ?: limits.violation(null, amount)) {
            "No merchant takes $amount, yet none of them has a limit it misses"
        }
        return RampOffer.OutOfLimits(nearest?.let { exchangeRepository.merchantImage(walletId, it) }, violation)
    }

    private fun hasReceive(quote: RampQuote): Boolean {
        return (quote.amountOut.toBigDecimalOrNull()?.signum() ?: 0) > 0
    }

}

internal suspend fun ExchangeRepository.merchantImage(walletId: String, slug: ExchangeMerchantSlug): String? {
    return merchant(walletId, slug)?.image?.takeIf { it.isNotBlank() }
}

// Null when the merchants failed to load or do not list this one: an offer goes on without its picture and name.
internal suspend fun ExchangeRepository.merchant(walletId: String, slug: ExchangeMerchantSlug): ExchangeMerchantInfo? {
    return catchingResult { getMerchants(walletId) }.getOrDefault(emptyList()).firstOrNull { it.id == slug.value }
}

internal fun RampMethod.toPaymentMethod(): RampPaymentMethod {
    return RampPaymentMethod(
        id = type.value,
        title = name,
        subtitle = subtitle,
        iconUrl = image.takeIf { it.isNotBlank() },
        badge = badge,
        details = RampPaymentMethod.Details.Merchant(this),
    )
}

private val RampPaymentMethod.merchantMethod: RampMethod
    get() = requireNotNull((details as? RampPaymentMethod.Details.Merchant)?.method) { "Not a merchant method: $id" }

internal fun RampPaymentMethod.offersMoonPay(fiat: String): Boolean {
    val method = (details as? RampPaymentMethod.Details.Merchant)?.method ?: return false
    return !method.isP2P && method.providers.any { it.merchant == ExchangeMerchantSlug.moonpay && it.fiat == fiat }
}
