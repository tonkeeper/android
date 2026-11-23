package com.tonapps.icu

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.util.Locale

/**
 * Pins the separators [CurrencyFormatter] exposes, because the app layer hands exactly these to
 * ChainKit and the amount input builds its mask from them.
 */
@RunWith(AndroidJUnit4::class)
class CurrencyFormatterTest {

    @After
    fun restoreDefaultLocale() {
        CurrencyFormatter.setLocale(Locale.getDefault(Locale.Category.FORMAT))
    }

    @Test
    fun separatorsFollowTheLocale() {
        CurrencyFormatter.setLocale(Locale.US)
        assertEquals(".", CurrencyFormatter.decimalSeparator)
        assertEquals(",", CurrencyFormatter.groupingSeparator)

        CurrencyFormatter.setLocale(Locale.GERMANY)
        assertEquals(",", CurrencyFormatter.decimalSeparator)
        assertEquals(".", CurrencyFormatter.groupingSeparator)
    }

    @Test
    fun repeatedSetLocaleKeepsTheSeparators() {
        CurrencyFormatter.setLocale(Locale.GERMANY)
        CurrencyFormatter.setLocale(Locale.GERMANY)

        assertEquals(Locale.GERMANY, CurrencyFormatter.locale)
        assertEquals(",", CurrencyFormatter.decimalSeparator)
        assertEquals(".", CurrencyFormatter.groupingSeparator)
    }

    @Test
    fun customDigitLocalesFallBackToUs() {
        CurrencyFormatter.setLocale(Locale("ar", "EG"))

        assertEquals(Locale.US, CurrencyFormatter.locale)
        assertEquals(".", CurrencyFormatter.decimalSeparator)
        assertEquals(",", CurrencyFormatter.groupingSeparator)
    }

    @Test
    fun formattedOutputCarriesTheExposedDecimalSeparator() {
        CurrencyFormatter.setLocale(Locale.GERMANY)

        assertEquals("12,34", CurrencyFormatter.format(value = BigDecimal("12.34")).toString())
    }

    @Test
    fun compactOutputCarriesTheExposedDecimalSeparator() {
        CurrencyFormatter.setLocale(Locale.GERMANY)
        assertEquals("1,5K", CurrencyFormatter.format(value = BigDecimal("1500"), compact = true).toString())

        CurrencyFormatter.setLocale(Locale.US)
        assertEquals("1.5K", CurrencyFormatter.format(value = BigDecimal("1500"), compact = true).toString())
    }

    @Test
    fun exactFiatKeepsEveryDigitAndTheCurrencyDecimals() {
        CurrencyFormatter.setLocale(Locale.US)

        assertEquals("$\u20091,234.50", CurrencyFormatter.formatFiatExact("USD", BigDecimal("1234.5")).toString())
        assertEquals("$\u20092.955", CurrencyFormatter.formatFiatExact("USD", BigDecimal("2.955")).toString())
        assertEquals("¥\u20091,234", CurrencyFormatter.formatFiatExact("JPY", BigDecimal("1234")).toString())
    }

    @Test
    fun percentFollowsTheFormatterLocale() {
        CurrencyFormatter.setLocale(Locale.GERMANY)
        assertEquals("-\u20091,25%", Formatter.percent(-1.25f))

        CurrencyFormatter.setLocale(Locale("ar", "EG"))
        assertEquals("+\u20091.25%", Formatter.percent(1.25f))
    }
}
