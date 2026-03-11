package com.tonapps.icu

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.text.SpannableString
import com.tonapps.log.L
import com.tonapps.icu.format.CurrencyFormat
import com.tonapps.icu.format.TONSymbolSpan
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale

object CurrencyFormatter {

    const val MINUS = "-"
    const val PREFIX_PLUS = "+ "
    const val PREFIX_MINUS = "− "

    private val customDigitLocales = listOf(
        Locale("ar"),
        Locale("fa"),
        Locale("ur"),
        Locale("hi"),
        Locale("bn"),
        Locale("ta"),
        Locale("th"),
        Locale("lo"),
        Locale("my"),
        Locale("si"),
    )

    fun getScale(value: BigDecimal): Int {
        return CurrencyFormat.getScale(value)
    }

    private fun getFixedLocale(locale: Locale): Locale {
        return try {
            if (customDigitLocales.any { it.language.equals(locale.language) } || locale.isO3Country.equals("IRN", ignoreCase = true)) {
                Locale.US
            } else {
                locale
            }
        } catch (e: Throwable) {
            Locale.US
        }
    }

    private var format = CurrencyFormat(getFixedLocale(Locale.getDefault(Locale.Category.FORMAT)))

    val monetaryDecimalSeparator: String
        get() = format.monetaryDecimalSeparator

    val locale: Locale
        get() = format.locale

    fun onConfigurationChanged(newConfig: Configuration) {
        val newLocale = newConfig.locales[0]
        onLocaleChanged(newLocale)
    }

    private fun onLocaleChanged(newLocale: Locale) {
        format = CurrencyFormat(getFixedLocale(newLocale))
    }

    fun formatPercent(
        value: BigDecimal,
        roundingMode: RoundingMode = RoundingMode.DOWN,
        stripTrailingZeros: Boolean = true,
    ): CharSequence {
        val format = format(value = value, roundingMode = roundingMode, stripTrailingZeros = stripTrailingZeros, cutLongSymbol = false)
        return "$format%"
    }

    fun formatPercent(
        value: Int
    ) = formatPercent(value.toBigDecimal())

    fun format(
        currency: String = "",
        value: BigDecimal,
        roundingMode: RoundingMode = RoundingMode.DOWN,
        replaceSymbol: Boolean = true,
        stripTrailingZeros: Boolean = true,
        cutLongSymbol: Boolean = false,
    ): CharSequence {
       return format.format(currency, value, roundingMode, replaceSymbol, stripTrailingZeros, cutLongSymbol)
    }

    fun formatFull(
        currency: String,
        value: Coins,
        customScale: Int
    ): CharSequence {
        return format.formatFull(currency, value.value, customScale)
    }

    fun format(
        currency: String = "",
        value: Coins,
        roundingMode: RoundingMode = RoundingMode.DOWN,
        replaceSymbol: Boolean = true,
        stripTrailingZeros: Boolean = true,
        cutLongSymbol: Boolean = false,
        scale: Int? = null
    ): CharSequence {
        val value = when (scale) {
            null -> value.value
            else -> value.value.setScale(scale, RoundingMode.CEILING)
        }

        return format(currency, value, roundingMode, replaceSymbol, stripTrailingZeros, cutLongSymbol)
    }

    fun formatFiat(
        currency: String,
        value: BigDecimal,
        roundingMode: RoundingMode = RoundingMode.HALF_EVEN,
        replaceSymbol: Boolean = true,
        stripTrailingZeros: Boolean = false,
    ): CharSequence {
        return format(currency, value, roundingMode, replaceSymbol, stripTrailingZeros, false)
    }

    fun formatFiat(
        currency: String,
        value: Coins,
        roundingMode: RoundingMode = RoundingMode.HALF_EVEN,
        replaceSymbol: Boolean = true,
    ) = formatFiat(currency, value.value, roundingMode, replaceSymbol)

    fun CharSequence.withCustomSymbol(context: Context): CharSequence {
        if (true) { // Not now... maybe in future
            return this
        }
        val startIndex = indexOf(CurrencyFormat.TON_SYMBOL)
        val endIndex = startIndex + CurrencyFormat.TON_SYMBOL.length
        if (startIndex == -1) {
            return this
        }
        val previewChar = getOrNull(startIndex - 1) ?: ' '
        val nextChar = getOrNull(endIndex) ?: ' '
        if (previewChar.isLetter() || nextChar.isLetter()) {
            return this
        }

        val span = TONSymbolSpan(context)
        val spannableString = SpannableString(this)
        spannableString.setSpan(span, startIndex, endIndex, SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE)
        return spannableString
    }

}