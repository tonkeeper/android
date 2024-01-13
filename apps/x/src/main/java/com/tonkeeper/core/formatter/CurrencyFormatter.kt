package com.tonkeeper.core.formatter

import android.icu.text.DecimalFormat
import android.icu.text.NumberFormat
import androidx.collection.arrayMapOf
import com.tonkeeper.App
import java.util.concurrent.ConcurrentHashMap

object CurrencyFormatter {

    private const val CURRENCY_SIGN = "¤"
    private const val SMALL_SPACE = " "
    private const val APOSTROPHE = "'"

    private val symbols = arrayMapOf<String, String>().apply {
        put("USD", "$")
        put("EUR", "€")
        put("RUB", "₽")
        put("UAH", "₴")
        put("UZS", "лв")
        put("GBP", "£")
        put("CHF", "₣")
        put("CNY", "¥")
        put("JPY", "¥")
        put("KRW", "₩")
        put("IDR", "Rp")
        put("INR", "₹")
        put("TRY", "₺")
        put("THB", "฿")
        put("KZT", "₸")
        put("AED", "د.إ")
    }

    private val format = NumberFormat.getCurrencyInstance() as DecimalFormat
    private val pattern = format.toPattern().replace(CURRENCY_SIGN, "").trim()
    private val cache = ConcurrentHashMap<String, DecimalFormat>(symbols.size, 1.0f, 2)

    val monetarySymbolFirstPosition = format.toPattern().startsWith(CURRENCY_SIGN)
    val monetaryDecimalSeparator = format.decimalFormatSymbols.monetaryDecimalSeparatorString
    var emptyAmount = "0${monetaryDecimalSeparator}00"

    fun format(
        currency: String = "",
        value: Float,
        decimals: Int,
        modifier: ((String) -> String) = { it }
    ): String {
        val amount = modifier(getFormat(decimals).format(value))
        if (amount == emptyAmount && value > 0f) {
            return format(currency, value, decimalCount(value), modifier)
        }

        val symbol = symbols[currency]
        val stringBuilder = StringBuilder()
        if (symbol != null) {
            if (monetarySymbolFirstPosition) {
                stringBuilder.append(symbol)
                stringBuilder.append(SMALL_SPACE)
                stringBuilder.append(amount)
            } else {
                stringBuilder.append(amount)
                stringBuilder.append(SMALL_SPACE)
                stringBuilder.append(symbol)
            }
            return stringBuilder.toString()
        } else if (currency == "") {
            return amount
        } else {
            stringBuilder.append(amount)
            stringBuilder.append(SMALL_SPACE)
            stringBuilder.append(currency)
            return stringBuilder.toString()
        }
    }

    fun format(
        currency: String = "",
        value: Float,
        modifier: ((String) -> String) = { it }
    ): String {
        val decimals = decimalCount(value)
        return format(currency, value, decimals, modifier)
    }

    fun formatRate(
        currency: String,
        value: Float
    ): String {
        return format(currency, value, 4)
    }

    fun formatFiat(
        currency: String,
        value: Float
    ): String {
        return format(currency, value, 2)
    }

    fun formatFiat(
        value: Float
    ): String {
        val currency = App.settings.currency
        return formatFiat(currency.code, value)
    }

    private fun decimalCount(value: Float): Int {
        if (0f >= value || value % 1.0f == 0.0f) {
            return 0
        }
        if (0.0001f > value) {
            return 8
        }
        if (0.01f > value) {
            return 4
        }
        return 2
    }

    private fun getFormat(decimals: Int): DecimalFormat {
        val key = cacheKey(decimals)
        var format = cache[key]
        if (format == null) {
            format = createFormat(decimals)
            cache[key] = format
        }
        return format
    }

    private fun cacheKey(decimals: Int): String {
        return decimals.toString()
    }

    private fun createFormat(decimals: Int): DecimalFormat {
        val decimalFormat = DecimalFormat(pattern)
        decimalFormat.maximumFractionDigits = decimals
        decimalFormat.minimumFractionDigits = decimals
        decimalFormat.groupingSize = 3
        decimalFormat.isGroupingUsed = true
        return decimalFormat
    }
}