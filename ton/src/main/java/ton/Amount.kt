package com.tonkeeper.ton

import java.text.DecimalFormat
import java.text.NumberFormat
import java.util.Locale

class Amount(
    val currency: SupportedCurrency,
    val value: Float
) {

    private val format: NumberFormat = NumberFormat.getCurrencyInstance(Locale.US).apply {
        val decimalFormatSymbols = (this as DecimalFormat).decimalFormatSymbols
        decimalFormatSymbols.currencySymbol = this@Amount.currency.symbol + " "
        this.decimalFormatSymbols = decimalFormatSymbols
    }

    fun toUserLike(): String {
        return format.format(value)
    }

}