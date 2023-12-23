package com.tonkeeper.core

import android.icu.text.DecimalFormat
import android.icu.text.NumberFormat
import androidx.collection.arrayMapOf
import com.tonkeeper.App
import java.util.concurrent.ConcurrentHashMap

object CurrencyFormatter {

    private const val CURRENCY_SIGN = "¤"
    private const val SMALL_SPACE = " "
    private val symbols = arrayMapOf<String, String>().apply {
        put("USD", "$")
        put("EUR", "€")
        put("RUB", "₽")
        put("AED", "AED")
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
        put("BTC", "₿")
        put("TON", "TON")
    }

    private val format = NumberFormat.getCurrencyInstance() as DecimalFormat
    private val pattern = format.toPattern()
    private val cache = ConcurrentHashMap<String, DecimalFormat>(symbols.size, 1.0f, 2)

    fun format(
        currency: String = "",
        value: Float,
        decimals: Int
    ): String {
        val format = getFormat(currency, decimals)
        return format.format(value).removeSuffix(".00")
    }

    fun format(
        currency: String = "",
        value: Float,
    ): String {
        val decimals = decimalCount(value)
        return format(currency, value, decimals)
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
        if (0.00009 >= value) {
            return 9
        }
        if (0.009 >= value) {
            return 4
        }
        if (value % 1.0f == 0.0f) {
            return 0
        }
        return 2
    }

    private fun getSymbols(currency: String): String {
        return symbols[currency] ?: currency
    }

    private fun getFormat(currency: String, decimals: Int): DecimalFormat {
        val key = cacheKey(currency, decimals)
        var format = cache[key]
        if (format == null) {
            format = createFormat(currency, decimals)
            cache[key] = format
        }
        return format
    }

    private fun cacheKey(currency: String, decimals: Int): String {
        return "$currency:$decimals"
    }

    private fun createFormat(currency: String, decimals: Int): DecimalFormat {
        val symbol = SMALL_SPACE + getSymbols(currency) + SMALL_SPACE
        val pattern = pattern.replace(CURRENCY_SIGN, symbol).trim()
        val decimalFormat = DecimalFormat(pattern)
        decimalFormat.maximumFractionDigits = decimals
        decimalFormat.minimumFractionDigits = decimals
        return decimalFormat
    }
}