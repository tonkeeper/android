package com.tonapps.icu

import android.util.ArrayMap
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.NumberFormat
import java.util.concurrent.ConcurrentHashMap

object CurrencyFormatter {

    private const val CURRENCY_SIGN = "¤"
    private const val SMALL_SPACE = " "
    private const val APOSTROPHE = "'"
    private const val TON_SYMBOL = "TON"

    private val symbols = ArrayMap<String, String>().apply {
        put("USD", "$")
        put("EUR", "€")
        put("RUB", "₽")
        put("AED", "د.إ")
        put("UAH", "₴")
        put("KZT", "₸")
        put("UZS", "лв")
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

        put("TON", TON_SYMBOL)
        put("BTC", "₿")
    }

    private val thresholds = listOf(
        0.0000000001 to 18,
        0.00000001 to 16,
        0.0001 to 8,
        0.01 to 2
    )

    private fun isTON(currency: String): Boolean {
        return currency == "TON"
    }

    private fun isCrypto(currency: String): Boolean {
        return isTON(currency) || currency == "BTC"
    }

    private val format = NumberFormat.getCurrencyInstance() as DecimalFormat
    private val pattern = format.toPattern().replace(CURRENCY_SIGN, "").trim()
    private val cache = ConcurrentHashMap<String, DecimalFormat>(symbols.size, 1.0f, 2)

    val monetarySymbolFirstPosition = format.toPattern().startsWith(CURRENCY_SIGN)
    val monetaryDecimalSeparator = format.decimalFormatSymbols.monetaryDecimalSeparator.toString()

    fun format(
        currency: String = "",
        value: Double,
        scale: Int = 0,
        roundingMode: RoundingMode = RoundingMode.DOWN
    ): CharSequence {
        var bigDecimal = Coins.safeBigDecimal(value).stripTrailingZeros()
        if (scale > 0) {
            bigDecimal = bigDecimal.setScale(scale, roundingMode)
        } else if (bigDecimal.scale() > 0) {
            bigDecimal = bigDecimal.setScale(getScale(value), roundingMode)
        }
        bigDecimal = bigDecimal.stripTrailingZeros()
        val decimals = bigDecimal.scale()
        val amount = getFormat(decimals).format(bigDecimal)
        return format(currency, amount)
    }

    fun format(
        currency: String = "",
        value: Coins,
        scale: Int = 0,
        roundingMode: RoundingMode = RoundingMode.DOWN
    ): CharSequence {
        return format(currency, value.value, scale, roundingMode)
    }

    fun formatFiat(
        currency: String,
        value: Coins,
        scale: Int = 2,
        roundingMode: RoundingMode = RoundingMode.DOWN
    ): CharSequence {
        return format(currency, value, scale, roundingMode)
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
    ): CharSequence {
        val symbol = symbols[currency]
        val builder = StringBuilder()
        if (symbol != null) {
            if (monetarySymbolFirstPosition && !isCrypto(currency)) {
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