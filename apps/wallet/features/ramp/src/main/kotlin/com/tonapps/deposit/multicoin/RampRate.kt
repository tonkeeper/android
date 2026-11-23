package com.tonapps.deposit.multicoin

import com.tonapps.icu.Coins
import com.tonapps.icu.CurrencyFormatter
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

private const val RATE_SIGNIFICANT_DIGITS = 4
private const val RATE_DECIMALS = 3
private val RATE_SIGNIFICANT_BELOW = BigDecimal("0.1")

// As iOS's formatRate: up to three decimals, and four significant digits below 0.1, which three decimals would
// cut to zeros. Unlike iOS, the grouping separator is the app's, as on every other amount.
internal fun roundRate(value: BigDecimal): BigDecimal {
    val rounded = if (value < RATE_SIGNIFICANT_BELOW) {
        value.round(MathContext(RATE_SIGNIFICANT_DIGITS, RoundingMode.HALF_EVEN))
    } else {
        value.setScale(RATE_DECIMALS, RoundingMode.HALF_EVEN)
    }
    return rounded.stripTrailingZeros()
}

internal fun formatRate(value: BigDecimal, currency: String): String {
    val rounded = roundRate(value)
    return CurrencyFormatter.formatFull(currency, Coins.of(rounded), rounded.scale().coerceAtLeast(0)).toString()
}