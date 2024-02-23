package com.tonapps.icu

import android.util.ArrayMap
import android.util.Log
import java.math.BigInteger
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.NumberFormat
import java.util.concurrent.ConcurrentHashMap

object CurrencyFormatter {

    private const val CURRENCY_SIGN = "¤"
    private const val SMALL_SPACE = " "
    private const val APOSTROPHE = "'"

    private val symbols = ArrayMap<String, String>().apply {
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
        put("TON", "TON")
        put("BTC", "₿")
    }

    private fun isCrypto(currency: String): Boolean {
        return currency == "TON" || currency == "BTC"
    }

    private val format = NumberFormat.getCurrencyInstance() as DecimalFormat
    private val pattern = format.toPattern().replace(CURRENCY_SIGN, "").trim()
    private val cache = ConcurrentHashMap<String, DecimalFormat>(symbols.size, 1.0f, 2)

    private val monetarySymbolFirstPosition = format.toPattern().startsWith(CURRENCY_SIGN)
    private val monetaryDecimalSeparator = format.decimalFormatSymbols.monetaryDecimalSeparator.toString()
    private val zeroDecimalsValue = "${monetaryDecimalSeparator}00"
    private val zeroAmountValue = "0${zeroDecimalsValue}"

    fun format(
        currency: String = "",
        value: Float,
        decimals: Int,
    ): String {
        val amount = getFormat(decimals).format(value)
        return format(currency, amount)
    }

    fun format(
        currency: String = "",
        value: BigInteger,
        decimals: Int
    ): String {
        val amount = getFormat(decimals).format(value)
        return format(currency, amount)
    }

    fun format(
        currency: String = "",
        value: Float,
    ): String {
        val decimals = decimalCount(value)
        return format(currency, value, decimals)
    }

    fun format(
        currency: String = "",
        value: BigInteger
    ): String {
        var bigDecimal = value.toBigDecimal().stripTrailingZeros()
        if (bigDecimal.scale() > 0) {
            bigDecimal = bigDecimal.setScale(2, RoundingMode.HALF_UP)
        }
        val decimals = bigDecimal.scale()
        val amount = getFormat(decimals).format(value)
        return format(currency, amount)
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

    private fun format(
        currency: String = "",
        value: String,
    ): String {
        var amount = value.removeSuffix(zeroDecimalsValue)
        if (zeroAmountValue == value) {
            amount = "0"
        }
        val symbol = symbols[currency]
        val stringBuilder = StringBuilder()
        if (symbol != null) {
            if (monetarySymbolFirstPosition && !isCrypto(currency)) {
                stringBuilder.append(symbol)
                stringBuilder.append(SMALL_SPACE)
                stringBuilder.append(amount)
            } else {
                stringBuilder.append(amount)
                stringBuilder.append(SMALL_SPACE)
                stringBuilder.append(symbol)
            }
        } else if (currency == "") {
            stringBuilder.append(amount)
        } else {
            stringBuilder.append(amount)
            stringBuilder.append(SMALL_SPACE)
            stringBuilder.append(currency)
        }
        return stringBuilder.toString()
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