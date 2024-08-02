package com.tonapps.icu

import android.util.ArrayMap
import android.util.Log
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.NumberFormat
import java.util.concurrent.ConcurrentHashMap

object CurrencyFormatter {

    private const val CURRENCY_SIGN = "¤"
    private const val SMALL_SPACE = " "
    private const val APOSTROPHE = "'"

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
        put("TON", "TON")
    }

    private val symbols = fiatSymbols + tokenSymbols

    private val thresholds = listOf(
        0.0000000001 to 18,
        0.00000001 to 16,
        0.000001 to 8,
        0.0001 to 4,
        0.01 to 2
    )

    private val bigDecimalThresholds = listOf(
        BigDecimal("0.0000000001") to 18,
        BigDecimal("0.00000001") to 16,
        BigDecimal("0.000001") to 8,
        BigDecimal("0.0001") to 4,
        BigDecimal("0.01") to 2
    )

    private fun isFiat(currency: String): Boolean {
        return fiatSymbols.containsKey(currency)
    }

    private val format = NumberFormat.getCurrencyInstance() as DecimalFormat
    private val pattern = format.toPattern().replace(CURRENCY_SIGN, "").trim()
    private val cache = ConcurrentHashMap<String, DecimalFormat>(symbols.size, 1.0f, 2)

    val monetarySymbolFirstPosition = format.toPattern().startsWith(CURRENCY_SIGN)
    val monetaryDecimalSeparator = format.decimalFormatSymbols.monetaryDecimalSeparator.toString()

    fun format(
        currency: String = "",
        value: BigDecimal,
        scale: Int = 0,
        roundingMode: RoundingMode = RoundingMode.DOWN,
        replaceSymbol: Boolean = true
    ): CharSequence {
        var bigDecimal = value.stripTrailingZeros()
        if (scale > 0) {
            bigDecimal = bigDecimal.setScale(scale, roundingMode)
        } else if (bigDecimal.scale() > 0) {
            bigDecimal = bigDecimal.setScale(getScale(value.abs()), roundingMode)
        }
        bigDecimal = bigDecimal.stripTrailingZeros()
        val decimals = bigDecimal.scale()
        val amount = getFormat(decimals).format(bigDecimal)
        return format(currency, amount, replaceSymbol)
    }

    fun format(
        currency: String = "",
        value: Coins,
        scale: Int = 0,
        roundingMode: RoundingMode = RoundingMode.DOWN,
        replaceSymbol: Boolean = true
    ): CharSequence {
        return format(currency, value.value, scale, roundingMode, replaceSymbol)
    }

    fun formatFiat(
        currency: String,
        value: Coins,
        scale: Int = 2,
        roundingMode: RoundingMode = RoundingMode.DOWN,
        replaceSymbol: Boolean = true
    ): CharSequence {
        return format(currency, value, scale, roundingMode, replaceSymbol)
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

    private fun getScale(value: Double): Int {
        if (value == 0.0) {
            return 0
        } else if (value <= 0.0) {
            return 2
        }

        for ((threshold, scale) in thresholds) {
            if (value < threshold) {
                return scale
            }
        }
        return 2
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