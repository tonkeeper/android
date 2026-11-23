package com.tonapps.tonkeeper.ui.screen.battery

import com.tonapps.chainkit.core.chain.model.num.Formatter
import com.tonapps.tonkeeper.ui.screen.battery.recharge.asset.formatMinAmount
import com.tonapps.wallet.data.battery.BatteryRechargeMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class BatteryMinAmountTextTest {

    private val point = Formatter.defaultOptions.decimalSeparator

    @Test
    fun theMinimumIsRoundedUpNeverDown() {
        assertEquals("0${point}0304 SOL", formatMinAmount("SOL", BigDecimal("0.030314279"), 9))
        assertEquals("0${point}0312 SOL", formatMinAmount("SOL", BigDecimal("0.031102851"), 9))
        assertEquals("0${point}00385 BNB", formatMinAmount("BNB", BigDecimal("0.003841"), 18))
        assertEquals("0${point}00126 ETH", formatMinAmount("ETH", BigDecimal("0.001258"), 18))
        assertEquals("28${point}0433 POL", formatMinAmount("POL", BigDecimal("28.043267"), 18))
    }

    @Test
    fun aMinimumAboveOneIsRoundedUpToo() {
        assertEquals("1${point}235 USDT", formatMinAmount("USDT", BigDecimal("1.2345"), 6))
        assertEquals("123${point}457 NOT", formatMinAmount("NOT", BigDecimal("123.456789012"), 9))
    }

    @Test
    fun aMinimumWithFewDigitsIsShownAsItIs() {
        assertEquals("5 USDC", formatMinAmount("USDC", BigDecimal("5"), 6))
        assertEquals("0${point}03 SOL", formatMinAmount("SOL", BigDecimal("0.03"), 9))
        assertEquals("2${point}5 USDT", formatMinAmount("USDT", BigDecimal("2.50"), 6))
    }

    @Test
    fun theMinimumNeverHasMoreDecimalsThanTheAsset() {
        assertEquals("0${point}01 XYZ", formatMinAmount("XYZ", BigDecimal("0.001234"), 2))
        assertEquals("1 XYZ", formatMinAmount("XYZ", BigDecimal("0.3"), 0))
    }

    @Test
    fun theSeparatorsAreTheOnesItIsGiven() {
        val german = Formatter.defaultOptions.copy(decimalSeparator = ",", groupingSeparator = ".")
        assertEquals("1.234,568 XYZ", formatMinAmount("XYZ", BigDecimal("1234.5678"), 6, german))
    }

    @Test
    fun aMethodWithoutASymbolShowsTheBareAmount() {
        assertEquals("0${point}0304", formatMinAmount("", BigDecimal("0.030314279"), 9))
    }

    @Test
    fun theShownMinimumPassesTheCheckAgainstTheExactOne() {
        val minimums = listOf("0.030314279", "0.031102851", "28.043267", "0.003841", "0.001258", "0.002568", "5", "1.2345", "1234.5678")
        for (min in minimums) {
            val shown = formatMinAmount("", BigDecimal(min), 18).replace(Formatter.defaultOptions.groupingSeparator.orEmpty(), "").replace(point, ".")
            assertTrue("$shown < $min", BatteryRechargeMath.isEnough(BigDecimal(shown), min))
        }
    }
}
