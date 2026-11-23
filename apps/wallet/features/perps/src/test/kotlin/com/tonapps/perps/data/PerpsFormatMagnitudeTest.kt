package com.tonapps.perps.data

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
import com.tonapps.chainkit.core.chain.model.num.Formatter as ChainKitFormatter

class PerpsFormatMagnitudeTest {

    private val installedOptions = ChainKitFormatter.defaultOptions

    @After
    fun restoreLocale() {
        ChainKitFormatter.configureDefaults(installedOptions)
        configurePerpsMagnitudes(PerpsMagnitudes.English)
    }

    @Test
    fun `whole amounts drop the cents and fractional ones keep two digits`() {
        assertEquals("$100", formatUsdAmount(BigDecimal("100.00")))
        assertEquals("$1.50", formatUsdAmount(BigDecimal("1.5")))
        assertEquals("$0.50", formatUsdAmount(BigDecimal("0.5")))
        assertEquals("$0.05", formatUsdAmount(BigDecimal("0.05")))
        assertEquals("−$12.30", formatFiatAmount(BigDecimal("-12.3"), USD_CURRENCY))
        assertEquals("+$2.10", formatSignedUsdAmount(BigDecimal("2.1")))
        assertEquals("$712.50", formatCompactUsd(BigDecimal("712.5")))
        assertEquals("$712", formatCompactUsd(BigDecimal("712")))
    }

    @Test
    fun `shortened amounts keep their trimmed digits`() {
        assertEquals("$1.5K", formatCompactUsd(BigDecimal("1500")))
        assertEquals("$301.69K", formatCompactUsd(BigDecimal("301690")))
        assertEquals("$1.5M", formatUsdAmount(BigDecimal("1500000")))
        assertEquals("$2B", formatUsdAmount(BigDecimal("2000000000")))
    }

    @Test
    fun `market figures shorten from a thousand up to trillions`() {
        assertEquals("$999.99", formatCompactUsd(BigDecimal("999.99")))
        assertEquals("$20.64K", formatCompactUsd(BigDecimal("20640")))
        assertEquals("$20.6K", formatCompactUsd(BigDecimal("20600")))
        assertEquals("$20K", formatCompactUsd(BigDecimal("20000")))
        assertEquals("$4.61M", formatCompactUsd(BigDecimal("4610000")))
        assertEquals("$1.2B", formatCompactUsd(BigDecimal("1200000000")))
        assertEquals("$1.05T", formatCompactUsd(BigDecimal("1050000000000")))
        assertEquals("$1 200T", formatCompactUsd(BigDecimal("1200000000000000")))
    }

    @Test
    fun `market figures round half up to two decimals`() {
        assertEquals("$20.65K", formatCompactUsd(BigDecimal("20645")))
        assertEquals("$20.64K", formatCompactUsd(BigDecimal("20644.99")))
    }

    @Test
    fun `market figures that round to a thousand carry to the next magnitude`() {
        assertEquals("$999.99", formatCompactUsd(BigDecimal("999.994")))
        assertEquals("$1K", formatCompactUsd(BigDecimal("999.996")))
        assertEquals("$1M", formatCompactUsd(BigDecimal("999999")))
        assertEquals("$999.99K", formatCompactUsd(BigDecimal("999994")))
        assertEquals("$1B", formatCompactUsd(BigDecimal("999999999")))
        assertEquals("$1T", formatCompactUsd(BigDecimal("999999999999")))
    }

    @Test
    fun `fees round half up to cents and keep their digits below a cent`() {
        assertEquals("$0.95", formatUsdAmountRoundedToCents(BigDecimal("0.949")))
        assertEquals("$0.94", formatUsdAmountRoundedToCents(BigDecimal("0.944")))
        assertEquals("$0.01", formatUsdAmountRoundedToCents(BigDecimal("0.005")))
        assertEquals("$1.20", formatUsdAmountRoundedToCents(BigDecimal("1.199")))
        assertEquals(formatUsdAmount(BigDecimal("0.004")), formatUsdAmountRoundedToCents(BigDecimal("0.004")))
    }

    @Test
    fun `token amounts are not padded with cents`() {
        assertEquals("1.5 TON", formatTokenCompact(BigDecimal("1.5"), "TON"))
        assertEquals("1.5M USDT", formatTokenCompact(BigDecimal("1500000"), "USDT"))
    }

    @Test
    fun `installed magnitudes replace the english suffixes`() {
        ChainKitFormatter.configureDefaults(installedOptions.copy(decimalSeparator = ","))
        configurePerpsMagnitudes(
            PerpsMagnitudes(
                thousands = "%1\$s тыс.",
                millions = "%1\$s млн",
                billions = "%1\$s млрд",
                trillions = "%1\$s трлн",
            )
        )

        assertEquals("$1,5 тыс.", formatCompactUsd(BigDecimal("1500")))
        assertEquals("$51,5 млн", formatCompactUsd(BigDecimal("51500000")))
        assertEquals("$1 млн", formatCompactUsd(BigDecimal("999999")))
        assertEquals("$1,05 трлн", formatCompactUsd(BigDecimal("1050000000000")))
        assertEquals("$51,5 млн", formatUsdAmount(BigDecimal("51500000")))
        assertEquals("+$2,3 млрд", formatSignedUsdAmount(BigDecimal("2300000000")))
        assertEquals("$4 трлн", formatUsdAmount(BigDecimal("4000000000000")))
        assertEquals("1,5 млн USDT", formatTokenCompact(BigDecimal("1500000"), "USDT"))
        assertEquals("$1,50", formatUsdAmount(BigDecimal("1.5")))
    }
}
