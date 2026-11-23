package com.tonapps.deposit.multicoin

import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

class RampRateTest {

    @Test
    fun `a rate below 0·1 keeps four significant digits`() {
        assertRounded("0.0000094312", "0.000009431")
        assertRounded("0.0123456", "0.01235")
        assertRounded("0.0999999", "0.1")
    }

    @Test
    fun `a rate of 0·1 or more keeps three decimals`() {
        assertRounded("0.931849", "0.932")
        assertRounded("0.45678", "0.457")
        assertRounded("0.1", "0.1")
        assertRounded("1.00237", "1.002")
        assertRounded("106031.48", "106031.48")
        assertRounded("3638.9174", "3638.917")
    }

    @Test
    fun `trailing zeros are dropped`() {
        assertRounded("2.9300", "2.93")
        assertRounded("100", "100")
        assertRounded("0.5000", "0.5")
    }

    private fun assertRounded(value: String, expected: String) {
        assertEquals(expected, roundRate(BigDecimal(value)).toPlainString())
    }
}