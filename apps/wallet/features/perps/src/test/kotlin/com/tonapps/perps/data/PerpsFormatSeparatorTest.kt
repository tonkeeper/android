package com.tonapps.perps.data

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import com.tonapps.chainkit.core.chain.model.num.Formatter as ChainKitFormatter

class PerpsFormatSeparatorTest {

    private val installedOptions = ChainKitFormatter.defaultOptions

    @Before
    fun installCommaLocale() {
        ChainKitFormatter.configureDefaults(installedOptions.copy(decimalSeparator = ","))
    }

    @After
    fun restoreLocale() {
        ChainKitFormatter.configureDefaults(installedOptions)
    }

    @Test
    fun `prices amounts and percents use the installed decimal separator`() {
        assertEquals("$3,63", formatUsd(BigDecimal("3.63")))
        assertEquals("$64 141,75", formatUsdPrice(64141.75))
        assertEquals("$0,1235", formatUsdPrice(0.12345, 4))
        assertEquals("$4,28", formatUsdAmount(BigDecimal("4.28")))
        assertEquals("−4,08%", formatSignedPercent(BigDecimal("-4.08")))
        assertEquals("+1,5 %", formatOffsetPercent(BigDecimal("1.5")))
        assertEquals("0,0058 BNB", formatTokenAmount(BigDecimal("0.0058"), "BNB", 4))
    }

    @Test
    fun `compact amounts use the installed decimal separator`() {
        assertEquals("$51,5M", formatCompactUsd(BigDecimal("51500000")))
        assertEquals("$51,5M", formatUsdAmount(BigDecimal("51500000")))
        assertEquals("+$1 234,56", formatSignedUsdAmount(BigDecimal("1234.567")))
    }

    @Test
    fun `funding slippage and base amounts use the installed decimal separator`() {
        assertEquals("0,0013%", formatFundingPercent(BigDecimal("0.000013")))
        assertEquals("0,5%", formatPpmPercent(5_000))
        assertEquals("0,0058", formatLocalizedDecimal(BigDecimal("0.00580")))
    }

    @Test
    fun `editable values keep the dot whatever the locale`() {
        assertEquals("0.0058", formatPlainDecimal(BigDecimal("0.00580")))
        assertEquals("1.5", sanitizeAmount("1,5"))
    }

    @Test
    fun `the dot comes back when the dot locale is installed`() {
        ChainKitFormatter.configureDefaults(installedOptions.copy(decimalSeparator = "."))

        assertEquals("$3.63", formatUsd(BigDecimal("3.63")))
        assertEquals("$51.5M", formatCompactUsd(BigDecimal("51500000")))
        assertEquals("0.0058", formatLocalizedDecimal(BigDecimal("0.0058")))
    }
}
