package com.tonapps.icu

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.text.SpannableString
import android.util.Log
import com.tonapps.icu.format.CurrencyFormat
import com.tonapps.icu.format.TONSymbolSpan
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale

object CurrencyFormatter {

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
        customScale: Int = 2,
        roundingMode: RoundingMode = RoundingMode.DOWN,
        stripTrailingZeros: Boolean = true,
    ): CharSequence {
        val format = format(value = value, customScale = customScale, roundingMode = roundingMode, stripTrailingZeros = stripTrailingZeros)
        return "$format%"
    }

    fun format(
        currency: String = "",
        value: BigDecimal,
        customScale: Int = 0,
        roundingMode: RoundingMode = RoundingMode.DOWN,
        replaceSymbol: Boolean = true,
        stripTrailingZeros: Boolean = true,
    ): CharSequence {
       return format.format(currency, value, customScale, roundingMode, replaceSymbol, stripTrailingZeros)
    }

    fun format(
        currency: String = "",
        value: Coins,
        customScale: Int = 0,
        roundingMode: RoundingMode = RoundingMode.DOWN,
        replaceSymbol: Boolean = true,
        stripTrailingZeros: Boolean = true,
    ): CharSequence {
        return format(currency, value.value, customScale, roundingMode, replaceSymbol, stripTrailingZeros)
    }

    fun formatFiat(
        currency: String,
        value: BigDecimal,
        customScale: Int = 2,
        roundingMode: RoundingMode = RoundingMode.DOWN,
        replaceSymbol: Boolean = true,
        stripTrailingZeros: Boolean = false,
    ): CharSequence {
        return format(currency, value, customScale, roundingMode, replaceSymbol, stripTrailingZeros)
    }

    fun formatFiat(
        currency: String,
        value: Coins,
        customScale: Int = 2,
        roundingMode: RoundingMode = RoundingMode.DOWN,
        replaceSymbol: Boolean = true,
    ) = formatFiat(currency, value.value, customScale, roundingMode, replaceSymbol)

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