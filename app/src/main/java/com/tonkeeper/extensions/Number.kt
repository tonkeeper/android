package com.tonkeeper.extensions

import com.tonkeeper.SupportedCurrency
import java.text.DecimalFormat
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

private val usdCurrency = Currency.getInstance(SupportedCurrency.USD.code)

private val currencyUSDFormat = NumberFormat.getCurrencyInstance(Locale.US).apply {
    val decimalFormatSymbols = (this as DecimalFormat).decimalFormatSymbols
    decimalFormatSymbols.currencySymbol = usdCurrency.symbol + " "
    this.decimalFormatSymbols = decimalFormatSymbols
}

private val currencyTONFormat = NumberFormat.getCurrencyInstance(Locale.US).apply {
    val decimalFormatSymbols = (this as DecimalFormat).decimalFormatSymbols
    decimalFormatSymbols.currencySymbol = ""
    this.decimalFormatSymbols = decimalFormatSymbols
}

fun Float.toUserLikeUSD(): String {
    return currencyUSDFormat.format(this)
}

fun Float.toUserLikeTON(): String {
    return currencyTONFormat.format(this)
}