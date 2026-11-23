package com.tonapps.wallet.data.battery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.math.BigInteger

class BatteryRechargeMathTest {

    @Test
    fun theChargesRateIsTakenAsTheBatterySendsIt() {
        assertEquals(BigDecimal("335.7457"), BatteryRechargeMath.chargesRate("335.7457", "0.87293889427740057", "0.0026"))
    }

    @Test
    fun withoutAChargesRateItComesFromTheRateAndTheChargeCost() {
        // 0.0052 TON a unit at 0.0026 TON a charge: two charges a unit.
        val rate = BatteryRechargeMath.chargesRate(null, "0.0052", "0.0026")
        assertEquals(0, BigDecimal("2").compareTo(rate))
        assertEquals(0, BigDecimal("2").compareTo(BatteryRechargeMath.chargesRate("0", "0.0052", "0.0026")))
        assertEquals(0, BigDecimal("2").compareTo(BatteryRechargeMath.chargesRate("not a number", "0.0052", "0.0026")))
    }

    @Test
    fun noChargesRateFromABrokenMethod() {
        assertNull(BatteryRechargeMath.chargesRate(null, "0", "0.0026"))
        assertNull(BatteryRechargeMath.chargesRate(null, "0.8", ""))
        assertNull(BatteryRechargeMath.chargesRate("-1", "not a number", "0.0026"))
    }

    @Test
    fun chargesFollowTheChargesRate() {
        // 10 USDT at 335.7457 charges each.
        assertEquals(3357L, BatteryRechargeMath.charges(BigDecimal("10"), BigDecimal("335.7457")))
    }

    @Test
    fun chargesRoundDown() {
        assertEquals(2L, BatteryRechargeMath.charges(BigDecimal("1"), BigDecimal("2")))
        assertEquals(1L, BatteryRechargeMath.charges(BigDecimal("0.999999"), BigDecimal("2")))
    }

    @Test
    fun noChargesForNothingOrABrokenRate() {
        assertEquals(0L, BatteryRechargeMath.charges(BigDecimal.ZERO, BigDecimal("335")))
        assertEquals(0L, BatteryRechargeMath.charges(BigDecimal("10"), BigDecimal.ZERO))
        assertEquals(0L, BatteryRechargeMath.charges(BigDecimal("10"), null))
        assertEquals(0L, BatteryRechargeMath.charges(BigDecimal("-1"), BigDecimal("335")))
    }

    @Test
    fun chargesAreCappedRatherThanWrapped() {
        // Beyond Int.MAX_VALUE charges: an Int would wrap negative.
        assertEquals(3_357_457_000_000L, BatteryRechargeMath.charges(BigDecimal("10000000000"), BigDecimal("335.7457")))
        assertEquals(Long.MAX_VALUE, BatteryRechargeMath.charges(BigDecimal("1E30"), BigDecimal("335.7457")))
    }

    @Test
    fun packAmountRoundsUpToTheAssetsDecimals() {
        // 750 / 335.7457 = 2.2338335… USDT, up to six decimals.
        assertEquals(BigDecimal("2.233834"), BatteryRechargeMath.amount(750, BigDecimal("335.7457"), decimals = 6))
    }

    @Test
    fun packAmountBuysAtLeastThePacksCharges() {
        listOf("335.7457", "1.7435", "0.0009", "712091.4331").map(::BigDecimal).forEach { rate ->
            listOf(750, 1500, 2500).forEach { charges ->
                listOf(6, 9, 18).forEach { decimals ->
                    val amount = BatteryRechargeMath.amount(charges, rate, decimals)
                    assertTrue("$charges at $rate, $decimals", BatteryRechargeMath.charges(amount, rate) >= charges)
                }
            }
        }
    }

    @Test
    fun packAmountOfACoinWithFewDecimals() {
        // 1500 charges at 500000 a coin: 0.003 exactly, with nothing to round.
        assertEquals(BigDecimal("0.003"), BatteryRechargeMath.amount(1500, BigDecimal("500000"), decimals = 3))
        // With two decimals 0.003 rounds up to 0.01, never down to nothing.
        assertEquals(BigDecimal("0.01"), BatteryRechargeMath.amount(1500, BigDecimal("500000"), decimals = 2))
    }

    @Test
    fun packAmountIsZeroForABrokenRate() {
        assertEquals(BigDecimal.ZERO, BatteryRechargeMath.amount(750, BigDecimal.ZERO, decimals = 6))
        assertEquals(BigDecimal.ZERO, BatteryRechargeMath.amount(750, null, decimals = 6))
        assertEquals(BigDecimal.ZERO, BatteryRechargeMath.amount(0, BigDecimal("335"), decimals = 6))
    }

    @Test
    fun theLeastPurchaseIsInclusive() {
        assertTrue(BatteryRechargeMath.isEnough(BigDecimal("5"), "5"))
        assertTrue(BatteryRechargeMath.isEnough(BigDecimal("5.000001"), "5"))
        assertFalse(BatteryRechargeMath.isEnough(BigDecimal("4.999999"), "5"))
    }

    @Test
    fun withoutALeastPurchaseAnyPositiveAmountIsEnough() {
        assertTrue(BatteryRechargeMath.isEnough(BigDecimal("0.000001"), null))
        assertFalse(BatteryRechargeMath.isEnough(BigDecimal.ZERO, null))
        assertFalse(BatteryRechargeMath.isEnough(BigDecimal.ZERO, "0"))
    }

    @Test
    fun baseUnitsFollowTheDecimals() {
        assertEquals(BigInteger("10500000"), BatteryRechargeMath.toBaseUnits(BigDecimal("10.5"), 6))
        assertEquals(BigInteger("1000000000000000000"), BatteryRechargeMath.toBaseUnits(BigDecimal("1"), 18))
        assertEquals(BigInteger("1"), BatteryRechargeMath.toBaseUnits(BigDecimal("0.000001"), 6))
    }

    @Test
    fun baseUnitsDropWhatIsBelowOne() {
        assertEquals(BigInteger("1"), BatteryRechargeMath.toBaseUnits(BigDecimal("0.0000019"), 6))
        assertEquals(BigInteger.ZERO, BatteryRechargeMath.toBaseUnits(BigDecimal("0.0000001"), 6))
    }
}
