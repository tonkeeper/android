package com.tonapps.deposit.multicoin.domain

import com.tonapps.deposit.multicoin.data.RampProvider
import com.tonapps.deposit.multicoin.data.RampQuote
import com.tonapps.deposit.multicoin.data.RampQuotes
import io.exchangeapi.models.ExchangeMerchantSlug
import java.math.BigDecimal
import java.math.RoundingMode

sealed interface RampLimitViolation {
    data class BelowMin(val min: BigDecimal) : RampLimitViolation
    data class AboveMax(val max: BigDecimal) : RampLimitViolation
}

// The merchants of one payment method and fiat, and the limits on the amount they take. A quote's
// limits win over the provider's; the minimum is rounded down and the maximum up to the fiat's
// precision, so an amount the user typed at that precision is never rejected by a fraction.
class RampLimits(
    private val providers: List<RampProvider>,
    private val quotes: RampQuotes?,
    private val fiatScale: Int,
) {

    data class Range(val min: BigDecimal?, val max: BigDecimal?)

    private val merchants: List<ExchangeMerchantSlug> = providers.map { it.merchant }.distinct()

    val orderedMerchants: List<ExchangeMerchantSlug>
        get() {
            val quotes = quotes ?: return merchants
            return (quotes.items + quotes.suggested).map { it.merchant }.distinct().filter { it in merchants }
        }

    // As on iOS, the lowest minimum and the highest maximum among the merchants that set one.
    val widest: Range
        get() {
            val ranges = merchants.mapNotNull { limits(it) }
            return Range(min = ranges.mapNotNull { it.min }.minOrNull(), max = ranges.mapNotNull { it.max }.maxOrNull())
        }

    // The most any merchant takes, or null when one of them sets no maximum.
    val ceiling: BigDecimal?
        get() {
            val maxima = merchants.map { limits(it)?.max }
            return if (null in maxima) {
                null
            } else {
                maxima.filterNotNull().maxOrNull()
            }
        }

    fun quote(merchant: ExchangeMerchantSlug): RampQuote? {
        return quotes?.items?.firstOrNull { it.merchant == merchant }
            ?: quotes?.suggested?.firstOrNull { it.merchant == merchant }
    }

    fun limits(merchant: ExchangeMerchantSlug): Range? {
        val provider = providers.firstOrNull { it.merchant == merchant }?.limits
        val quote = quote(merchant)
        val min = positive(quote?.minAmount ?: provider?.min)
        val max = positive(quote?.maxAmount ?: provider?.max)
        return if (min == null && max == null) {
            null
        } else {
            Range(min, max)
        }
    }

    fun canServe(merchant: ExchangeMerchantSlug, amount: BigDecimal): Boolean {
        return violation(limits(merchant), amount) == null
    }

    fun hasServiceableMerchant(amount: BigDecimal): Boolean {
        return merchants.any { canServe(it, amount) }
    }

    // When no merchant takes the amount, the one with the nearest minimum above it shows how much to add.
    fun bestMerchant(amount: BigDecimal): ExchangeMerchantSlug? {
        val ordered = orderedMerchants
        return ordered.firstOrNull { canServe(it, amount) }
            ?: closestBelowMin(amount)
            ?: ordered.firstOrNull()
    }

    fun violation(merchant: ExchangeMerchantSlug?, amount: BigDecimal): RampLimitViolation? {
        val range = if (merchant != null) {
            limits(merchant)
        } else {
            widest
        }
        return violation(range, amount)
    }

    private fun closestBelowMin(amount: BigDecimal): ExchangeMerchantSlug? {
        return orderedMerchants
            .mapNotNull { merchant ->
                val min = limits(merchant)?.min ?: return@mapNotNull null
                if (amount < floor(min)) {
                    merchant to min
                } else {
                    null
                }
            }
            .minByOrNull { it.second }
            ?.first
    }

    private fun violation(range: Range?, amount: BigDecimal): RampLimitViolation? {
        if (amount.signum() <= 0 || range == null) {
            return null
        }
        val min = range.min
        val max = range.max
        return when {
            min != null && amount < floor(min) -> RampLimitViolation.BelowMin(min)
            max != null && amount > ceil(max) -> RampLimitViolation.AboveMax(max)
            else -> null
        }
    }

    private fun floor(value: BigDecimal): BigDecimal = value.setScale(fiatScale, RoundingMode.DOWN)

    private fun ceil(value: BigDecimal): BigDecimal = value.setScale(fiatScale, RoundingMode.UP)

    private fun positive(value: String?): BigDecimal? {
        return value?.toBigDecimalOrNull()?.takeIf { it.signum() > 0 }
    }
}
