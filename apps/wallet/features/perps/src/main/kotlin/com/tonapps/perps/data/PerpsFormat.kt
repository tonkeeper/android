package com.tonapps.perps.data

import com.tonapps.core.components.sanitizeAmountInput
import com.tonapps.extensions.fiatSymbol
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols

/**
 * Formatting helpers for the perps screens. The `Double` overloads still back the placeholder
 * deposit flow; everything bound to the API is `BigDecimal`.
 */

const val PERPS_EMPTY_VALUE = "—"

const val USD_CURRENCY = "USD"

fun perpsTicker(symbol: String): String = "${symbol.uppercase()}/USD"

private fun groupingSymbols(): DecimalFormatSymbols = DecimalFormatSymbols().apply {
    groupingSeparator = ' '
    decimalSeparator = '.'
}

/** e.g. 3906.0, "USDT" -> "3 906 USDT" */
fun formatTokenAmount(amount: Double, symbol: String): String {
    val formatted = DecimalFormat("#,##0.######", groupingSymbols()).format(amount)
    return "$formatted $symbol"
}

fun formatTokenAmount(amount: BigDecimal, symbol: String, decimals: Int?): String {
    val fractionDigits = decimals?.coerceIn(0, MAX_SIZE_DECIMALS) ?: DEFAULT_SIZE_DECIMALS
    val value = amount.setScale(fractionDigits, RoundingMode.HALF_UP).stripTrailingZeros()
    val format = DecimalFormat("#,##0", groupingSymbols()).apply {
        maximumFractionDigits = fractionDigits
    }
    return "${format.format(value)} $symbol"
}

fun formatPlainDecimal(value: BigDecimal): String = value.stripTrailingZeros().toPlainString()

/** e.g. 3902.0 -> "$3 902", 712.56 -> "$712.56" */
fun formatUsd(amount: Double): String {
    val formatted = DecimalFormat("#,##0.##", groupingSymbols()).format(amount)
    return "$$formatted"
}

/** Fixed 2-decimal USD price, e.g. 64141.75 -> "$64 141.75" */
fun formatUsdPrice(amount: Double): String {
    val formatted = DecimalFormat("#,##0.00", groupingSymbols()).format(amount)
    return "$$formatted"
}

/** USD price at the market's own precision, floored at 2 to match the chart plugin's labels. */
fun formatUsdPrice(amount: Double, decimals: Int): String {
    val fractionDigits = decimals.coerceIn(2, MAX_PRICE_DECIMALS)
    val format = DecimalFormat("#,##0", groupingSymbols()).apply {
        minimumFractionDigits = fractionDigits
        maximumFractionDigits = fractionDigits
    }
    return "$${format.format(amount)}"
}

fun formatUsd(amount: BigDecimal): String = formatPrice(amount, USD_CURRENCY)

fun formatPrice(amount: BigDecimal, currencyCode: String): String {
    val pattern = if (amount.abs() >= BigDecimal.ONE) {
        "#,##0.00"
    } else {
        "#,##0.00####"
    }
    val formatted = DecimalFormat(pattern, groupingSymbols()).format(amount.abs())
    return "${minusOrEmpty(amount)}${currencyCode.fiatSymbol()}$formatted"
}

fun formatSignedUsd(amount: BigDecimal): String {
    return "${signPrefix(amount)}${formatUsd(amount.abs())}"
}

fun formatCompactUsd(amount: BigDecimal): String = formatCompactPrice(amount, USD_CURRENCY)

fun formatCompactPrice(amount: BigDecimal, currencyCode: String): String {
    val value = amount.abs()
    val (scaled, suffix) = when {
        value >= BILLION -> value.movePointLeft(9) to "B"
        value >= MILLION -> value.movePointLeft(6) to "M"
        value >= THOUSAND -> value.movePointLeft(3) to "K"
        else -> value to ""
    }
    val formatted = DecimalFormat("#,##0.##", groupingSymbols()).format(scaled)
    return "${minusOrEmpty(amount)}${currencyCode.fiatSymbol()}$formatted$suffix"
}

fun formatSignedPercent(value: BigDecimal): String {
    val formatted = DecimalFormat("0.00", groupingSymbols()).format(value.abs())
    return "${signPrefix(value)}$formatted%"
}

/** The wire value is a fraction, so it is scaled by 100; funding rates are small enough to need 8 places. */
fun formatFundingPercent(value: BigDecimal): String {
    val percent = value.multiply(HUNDRED)
        .setScale(FUNDING_SCALE, RoundingMode.HALF_UP)
        .stripTrailingZeros()
    return "${percent.toPlainString()}%"
}

private fun signPrefix(value: BigDecimal): String {
    return when (value.signum()) {
        1 -> "+"
        -1 -> "−"
        else -> ""
    }
}

private fun minusOrEmpty(value: BigDecimal): String {
    if (value.signum() < 0) {
        return "−"
    }
    return ""
}

private val HUNDRED = BigDecimal(100)
private val THOUSAND = BigDecimal(1_000)
private val MILLION = BigDecimal(1_000_000)
private val BILLION = BigDecimal(1_000_000_000)
private const val FUNDING_SCALE = 8
private const val MAX_PRICE_DECIMALS = 8
private const val MAX_SIZE_DECIMALS = 8
private const val DEFAULT_SIZE_DECIMALS = 4

/** Plain, un-grouped string for the editable amount field, e.g. 3906.0 -> "3906". */
fun formatPlainAmount(value: Double): String {
    if (value <= 0.0) {
        return ""
    }
    return BigDecimal.valueOf(value)
        .setScale(6, RoundingMode.HALF_UP)
        .stripTrailingZeros()
        .toPlainString()
}

/** Keeps digits and a single decimal separator; mirrors the ramp amount field. */
fun sanitizeAmount(input: String): String {
    return input.sanitizeAmountInput().replace(',', '.')
}
