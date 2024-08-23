package com.tonapps.icu.format

import android.util.ArrayMap
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.NumberFormat
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

internal class CurrencyFormat(val locale: Locale) {

    companion object {
        private const val CURRENCY_SIGN = "¤"
        private const val SMALL_SPACE = " "
        private const val APOSTROPHE = "'"
        const val TON_SYMBOL = "TON"

        private val fiatSymbols = ArrayMap<String, String>().apply {
            put("USD", "$")
            put("EUR", "€")
            put("RUB", "₽")
            put("AED", "د.إ")
            put("UAH", "₴")
            put("KZT", "₸")
            put("UZS", "UZS")
            put("GBP", "£")
            put("CHF", "₣")
            put("CNY", "¥")
            put("KRW", "₩")
            put("IDR", "Rp")
            put("INR", "₹")
            put("JPY", "¥")
            put("CAD", "C$")
            put("ARS", "ARS$")
            put("BYN", "Br")
            put("COP", "COL$")
            put("ETB", "ብር")
            put("ILS", "₪")
            put("KES", "KSh")
            put("NGN", "₦")
            put("UGX", "USh")
            put("VES", "Bs.\u200E")
            put("ZAR", "R")
            put("TRY", "₺")
            put("THB", "฿")
            put("VND", "₫")
            put("BRL", "R$")
            put("GEL", "₾")
            put("BDT", "৳")
        }

        private val tokenSymbols = ArrayMap<String, String>().apply {
            put("BTC", "₿")
            put("ETH", "Ξ")
            put("USDT", "₮")
            put("USDC", "₵")
            put("DOGE", "Ð")
            put("TON", TON_SYMBOL)
        }

        private val symbols = fiatSymbols + tokenSymbols

        private fun isFiat(currency: String): Boolean {
            return fiatSymbols.containsKey(currency)
        }

        private fun createFormat(decimals: Int, pattern: String): DecimalFormat {
            val decimalFormat = DecimalFormat(pattern)
            decimalFormat.maximumFractionDigits = decimals
            decimalFormat.minimumFractionDigits = decimals
            decimalFormat.groupingSize = 3
            decimalFormat.isGroupingUsed = true
            return decimalFormat
        }

        private fun getScale(value: BigDecimal): Int {
            if (value == BigDecimal.ZERO) {
                return 0
            }
            return when {
                value >= BigDecimal.ONE -> 2
                value >= BigDecimal("0.1") -> 2
                value >= BigDecimal("0.01") -> 3
                else -> 4
            }
        }
    }

    private var format = NumberFormat.getCurrencyInstance(locale) as DecimalFormat
    private val pattern = format.toPattern().replace(CURRENCY_SIGN, "").trim()
    private val cache = ConcurrentHashMap<String, DecimalFormat>(symbols.size, 1.0f, 2)
    private val monetarySymbolFirstPosition = format.toPattern().startsWith(CURRENCY_SIGN)

    internal val monetaryDecimalSeparator = format.decimalFormatSymbols.monetaryDecimalSeparator.toString()

    fun format(
        currency: String = "",
        value: BigDecimal,
        customScale: Int = 0,
        roundingMode: RoundingMode = RoundingMode.DOWN,
        replaceSymbol: Boolean = true,
    ): CharSequence {
        val targetScale = getScale(value.abs())
        val scale = if (targetScale > customScale) targetScale else customScale
        val bigDecimal = value.stripTrailingZeros().setScale(scale, roundingMode).stripTrailingZeros()
        val decimals = bigDecimal.scale()
        val amount = getFormat(decimals).format(bigDecimal)
        return format(currency, amount, replaceSymbol)
    }

    private fun format(
        currency: String = "",
        value: String,
        replaceSymbol: Boolean,
    ): CharSequence {
        val symbol = if (replaceSymbol) symbols[currency] else currency
        val builder = StringBuilder()
        if (symbol != null) {
            if (monetarySymbolFirstPosition && isFiat(currency)) {
                builder.append(symbol)
                builder.append(SMALL_SPACE)
                builder.append(value)
            } else {
                builder.append(value)
                builder.append(SMALL_SPACE)
                builder.append(symbol)
            }
        } else if (currency == "") {
            builder.append(value)
        } else {
            builder.append(value)
            builder.append(SMALL_SPACE)
            builder.append(currency)
        }
        return builder.toString()
    }

    private fun cacheKey(decimals: Int): String {
        return decimals.toString()
    }

    private fun getFormat(decimals: Int): DecimalFormat {
        val key = cacheKey(decimals)
        var format = cache[key]
        if (format == null) {
            format = createFormat(decimals, pattern)
            cache[key] = format
        }
        return format
    }

}