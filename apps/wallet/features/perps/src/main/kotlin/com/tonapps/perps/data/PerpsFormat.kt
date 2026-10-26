package com.tonapps.perps.data

import com.tonapps.chainkit.core.chain.model.num.FormatOptions
import com.tonapps.chainkit.core.chain.model.num.ScaleSpec
import com.tonapps.core.components.sanitizeAmountInput
import com.tonapps.extensions.fiatSymbol
import com.tonapps.wallet.data.core.HIDDEN_BALANCE
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import com.ionspin.kotlin.bignum.decimal.BigDecimal as ChainKitBigDecimal
import com.tonapps.chainkit.core.chain.model.num.Formatter as ChainKitFormatter

/**
 * Formatting helpers for the perps screens. The `Double` overloads still back the placeholder
 * deposit flow; everything bound to the API is `BigDecimal`.
 */

// TODO: move this file into chain-kit Formatter (com.tonapps.chainkit.core.chain.model.num.Formatter) to keep formatting in one place

const val PERPS_EMPTY_VALUE = "—"

const val USD_CURRENCY = "USD"

const val PERPS_QUOTE_INPUT_SCALE = 2

fun perpsTicker(symbol: String): String = "${symbol.uppercase()}/USD"

fun bidiIsolate(text: String): String = "\u2068$text\u2069"

// Latin digits in every locale, as CurrencyFormatter does for the rest of the wallet.
private fun groupingSymbols(): DecimalFormatSymbols = DecimalFormatSymbols(Locale.US).apply {
    groupingSeparator = '\u00A0'
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

fun formatUsdAmount(amount: BigDecimal): String = formatFiatAmount(amount, USD_CURRENCY)

fun formatFiatAmount(amount: BigDecimal, currencyCode: String): String {
    return "${minusOrEmpty(amount)}${currencyCode.fiatSymbol()}${compactDigits(amount.abs())}"
}

fun formatTokenCompact(amount: BigDecimal, symbol: String): String = "${compactDigits(amount)} $symbol"

fun formatSignedUsdAmount(amount: BigDecimal): String {
    return "${signPrefix(amount)}${USD_CURRENCY.fiatSymbol()}${compactDigits(amount.abs())}"
}

private val compactFormatOptions = FormatOptions(groupingSeparator = "\u00A0")

private fun compactDigits(value: BigDecimal): String {
    return ChainKitFormatter.formatNumber(
        value = ChainKitBigDecimal.parseString(value.toPlainString()),
        spec = ScaleSpec.Compact,
        isShorten = true,
        options = compactFormatOptions,
    )
}

fun formatQuoteAmount(amount: BigDecimal): String {
    return twoDecimals(amount.setScale(AMOUNT_SCALE, RoundingMode.HALF_UP))
}

private fun twoDecimals(value: BigDecimal): String {
    val format = DecimalFormat("#,##0.00", groupingSymbols()).apply {
        roundingMode = RoundingMode.HALF_UP
    }
    return format.format(value)
}

fun formatCompactUsd(amount: BigDecimal): String = formatCompactPrice(amount, USD_CURRENCY)

fun formatAutoClosePrice(value: BigDecimal, priceDecimals: Int): String {
    return if (value.abs() >= THOUSAND) {
        formatCompactUsd(value)
    } else {
        formatUsdPrice(value.toDouble(), priceDecimals)
    }
}

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

fun formatOffsetPercent(value: BigDecimal): String {
    val rounded = value.setScale(OFFSET_SCALE, RoundingMode.HALF_UP)
    val formatted = DecimalFormat("0.##", groupingSymbols()).format(rounded.abs())
    return "${signPrefix(rounded)}$formatted %"
}

/** The wire value is a fraction, so it is scaled by 100; funding rates are small enough to need 8 places. */
fun formatFundingPercent(value: BigDecimal): String {
    val percent = value.multiply(HUNDRED)
        .setScale(FUNDING_SCALE, RoundingMode.HALF_UP)
        .stripTrailingZeros()
    return "${percent.toPlainString()}%"
}

fun formatSlippagePercent(ppm: Long): String {
    val percent = BigDecimal.valueOf(ppm).movePointLeft(PPM_PERCENT_SHIFT).stripTrailingZeros()

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
private const val OFFSET_SCALE = 2
private const val AMOUNT_SCALE = 2
private const val PPM_PERCENT_SHIFT = 4
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

fun limitFractionDigits(input: String, maxDigits: Int): String {
    val separator = input.indexOf('.')
    if (separator < 0 || input.length - separator - 1 <= maxDigits) {
        return input
    }
    if (maxDigits == 0) {
        return input.substring(0, separator)
    }

    return input.substring(0, separator + 1 + maxDigits)
}

/** Keeps digits and a single decimal separator; mirrors the ramp amount field. */
fun sanitizeAmount(input: String): String {
    return input.sanitizeAmountInput().replace(',', '.')
}

internal inline fun maskIf(hidden: Boolean, format: () -> String): String = if (hidden) {
    HIDDEN_BALANCE
} else {
    format()
}
