package com.tonapps.icu.format

import android.icu.text.DecimalFormat
import android.icu.text.DecimalFormatSymbols
import android.icu.text.NumberFormat
import android.util.ArrayMap
import com.tonapps.log.L
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

internal class CurrencyFormat(val locale: Locale) {

    companion object {
        private const val CURRENCY_SIGN = "¤"
        private const val SMALL_SPACE = " "
        private const val DEFAULT_SPACE = " "
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
            put("USDC", "₵")
            put("DOGE", "Ð")
            put("TON", TON_SYMBOL)
            put("USDT", "USD₮")
        }

        private fun fixSymbol(value: String, cutLongSymbol: Boolean): String {
            if (cutLongSymbol && value.length > 5) {
                return value.substring(0, 5) + "…"
            }
            if (value.equals("USDT", ignoreCase = true)) {
                return "USD₮"
            }
            if (value.equals("USDC", ignoreCase = true)) {
                return "USD₵"
            }
            return value
        }

        private val symbols = fiatSymbols + tokenSymbols

        private fun isFiat(currency: String): Boolean {
            return fiatSymbols.containsKey(currency)
        }

        private fun createFormat(
            decimals: Int,
            pattern: String,
            locale: Locale
        ): DecimalFormat {
            val symbols = DecimalFormatSymbols.getInstance(locale)
            val decimalFormat = DecimalFormat(pattern, symbols)
            decimalFormat.maximumFractionDigits = decimals
            decimalFormat.minimumFractionDigits = decimals
            decimalFormat.groupingSize = 3
            decimalFormat.isGroupingUsed = true
            return decimalFormat
        }

        fun getScaleFull(value: BigDecimal): Int {
            if (value == BigDecimal.ZERO) {
                return 0
            }
            return when {
                value >= BigDecimal.ONE -> 2
                value >= BigDecimal("0.1") -> 2
                value >= BigDecimal("0.01") -> 3
                else -> {
                    val plainString = value.stripTrailingZeros().toPlainString()
                    val dotIndex = plainString.indexOf('.')
                    if (dotIndex == -1) {
                        return 2
                    }
                    var zerosAfterDot = 0
                    for (i in (dotIndex + 1) until plainString.length) {
                        if (plainString[i] == '0') {
                            zerosAfterDot++
                        } else {
                            break
                        }
                    }
                    maxOf(4, minOf(zerosAfterDot + 4, 12))
                }
            }
        }

        fun getScale(value: BigDecimal): Int {
            if (value == BigDecimal.ZERO) {
                return 0
            }
            return when {
                value >= BigDecimal("1000") -> 0
                value >= BigDecimal.ONE -> 2
                BigDecimal("0.0000001") >= value -> 0
                else -> {
                    val plainString = value.stripTrailingZeros().toPlainString()
                    val dotIndex = plainString.indexOf('.')
                    if (dotIndex == -1) {
                        return 0
                    }
                    var leadingZeros = 0
                    for (i in (dotIndex + 1) until plainString.length) {
                        if (plainString[i] == '0') {
                            leadingZeros++
                        } else {
                            break
                        }
                    }
                    leadingZeros + 3
                }
            }
        }
    }

    private var format = NumberFormat.getCurrencyInstance(locale) as DecimalFormat
    private val pattern = format.toPattern().replace(CURRENCY_SIGN, "").trim()
    private val cache = ConcurrentHashMap<String, DecimalFormat>(symbols.size, 1.0f, 2)
    private val monetarySymbolFirstPosition = format.toPattern().startsWith(CURRENCY_SIGN)

    internal val monetaryDecimalSeparator = format.decimalFormatSymbols.monetaryDecimalSeparator.toString()

    fun formatFull(
        currency: String,
        value: BigDecimal,
        customScale: Int,
    ): CharSequence {
        val targetScale = getScaleFull(value.abs())
        val scale = if (targetScale > customScale) targetScale else customScale
        val bigDecimal = value.stripTrailingZeros().setScale(scale, RoundingMode.HALF_EVEN).stripTrailingZeros()
        val decimals = bigDecimal.scale()
        val amount = getFormat(decimals).format(bigDecimal)
        return format(
            currency = currency,
            value = amount,
            replaceSymbol = true,
            cutLongSymbol = false
        )
    }

    fun format(
        currency: String = "",
        value: BigDecimal,
        roundingMode: RoundingMode = RoundingMode.DOWN,
        replaceSymbol: Boolean = true,
        stripTrailingZeros: Boolean,
        cutLongSymbol: Boolean,
    ): CharSequence {
        val scale = getScale(value.abs())
        val bigDecimal = if (stripTrailingZeros) {
            value.setScale(scale, roundingMode).stripTrailingZeros()
        } else {
            value.setScale(scale, roundingMode)
        }
        val decimals = bigDecimal.scale()
        val amount = getFormat(decimals).format(bigDecimal)
        return format(
            currency = currency,
            value = amount,
            replaceSymbol = replaceSymbol,
            cutLongSymbol = cutLongSymbol
        )
    }

    private fun format(
        currency: String = "",
        value: String,
        replaceSymbol: Boolean,
        cutLongSymbol: Boolean,
    ): CharSequence {
        val symbol = if (replaceSymbol) symbols[currency] else fixSymbol(currency, cutLongSymbol)
        val builder = StringBuilder()
        if (symbol != null) {
            if (replaceSymbol && monetarySymbolFirstPosition && isFiat(currency)) {
                builder.append(symbol)
                builder.append(SMALL_SPACE)
                builder.append(value)
            } else {
                builder.append(value)
                builder.append(DEFAULT_SPACE)
                builder.append(symbol)
            }
        } else if (currency == "") {
            builder.append(value)
        } else {
            builder.append(value)
            builder.append(DEFAULT_SPACE)
            builder.append(fixSymbol(currency, cutLongSymbol))
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
            format = createFormat(decimals, pattern, locale)
            cache[key] = format
        }
        return format
    }

}