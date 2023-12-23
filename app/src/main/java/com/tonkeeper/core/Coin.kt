package com.tonkeeper.core

import android.icu.math.MathContext
import android.util.Log
import androidx.collection.arrayMapOf
import org.ton.bigint.BigInt
import ton.SupportedCurrency
import java.math.BigDecimal
import java.math.BigInteger
import java.text.DecimalFormat
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale
import kotlin.math.abs
import kotlin.math.pow

object Coin {

    private const val DEFAULT_DECIMALS = 18

    const val MIN_DECIMALS = 2
    const val MAX_DECIMALS = 9

    const val BASE = 1000000000L
    private const val SMALL_SPACE = " "

    private val symbols = arrayMapOf<String, String>().apply {
        put("USD", "$")
        put("EUR", "€")
        put("RUB", "₽")
        put("AED", "د.إ")
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
        // put("TON", "\uD83D\uDC8E")
        put("TON", "TON")
    }

    private val currencyFormat = (NumberFormat.getCurrencyInstance() as DecimalFormat)

    private val simpleFormat = NumberFormat.getNumberInstance().apply {
        minimumFractionDigits = MIN_DECIMALS
        maximumFractionDigits = MAX_DECIMALS
    }

    fun parseFloat(
        value: String,
        decimals: Any = DEFAULT_DECIMALS
    ): Float {
        val actualDecimals = when (decimals) {
            is String -> decimals.toInt()
            else -> decimals as Int
        }
        val floatValue = value.toFloatOrNull() ?: return 0f
        return floatValue * 10.0.pow(-actualDecimals.toDouble()).toFloat()
    }

    fun toNano(value: Float): Long {
        return (value * BASE).toLong()
    }

    fun toCoins(value: Long): Float {
        return value / BASE.toFloat()
    }

    fun format(
        currency: SupportedCurrency,
        value: Long,
        useCurrencyCode: Boolean = false,
        decimals: Int = MIN_DECIMALS
    ): String {
        return format(currency.code, toCoins(value), useCurrencyCode, decimals)
    }

    fun format(
        currency: SupportedCurrency,
        value: Float,
        useCurrencyCode: Boolean = false,
        decimals: Int = MIN_DECIMALS
    ): String {
        return format(currency.code, value, useCurrencyCode, decimals)
    }

    fun format(
        currency: String = "",
        value: Long,
        useCurrencyCode: Boolean = false,
        decimals: Int = 2
    ): String {
        return format(currency, toCoins(value), useCurrencyCode, decimals)
    }

    fun format(
        currency: String = "",
        value: Float,
        useCurrencyCode: Boolean = false,
        decimals: Int = MIN_DECIMALS
    ): String {
        var format: String
        if (currency.isNotEmpty()) {
            val customSymbol = getSymbols(currency, useCurrencyCode)
            if (customSymbol != null) {
                simpleFormat.maximumFractionDigits = decimals
                simpleFormat.minimumFractionDigits = decimals
                format = customFormat(customSymbol, value)
            } else {
                currencyFormat.maximumFractionDigits = decimals
                simpleFormat.minimumFractionDigits = decimals
                currencyFormat.currency = Currency.getInstance(currency)
                format = currencyFormat.format(value)
            }
        } else {
            simpleFormat.maximumFractionDigits = decimals
            simpleFormat.minimumFractionDigits = decimals
            format = simpleFormat.format(value)
        }
        return format
    }

    private fun getSymbols(
        currency: String,
        useCurrencyCode: Boolean = false
    ): String? {
        if (useCurrencyCode) {
            return currency
        }
        return symbols[currency]
    }

    private fun customFormat(
        customSymbol: String,
        value: Float
    ): String {
        val symbols = currencyFormat.decimalFormatSymbols
        val pattern = currencyFormat.toPattern()
        symbols.currencySymbol = customSymbol
        if (pattern.indexOf('¤') < pattern.indexOf('#')) {
            symbols.currencySymbol = "$customSymbol$SMALL_SPACE"
        } else {
            symbols.currencySymbol = "$SMALL_SPACE$customSymbol"
        }
        currencyFormat.decimalFormatSymbols = symbols
        return currencyFormat.format(value)
    }
}