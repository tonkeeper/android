package com.tonapps.deposit.multicoin

import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

class QuickAmountsTest {

    @Test
    fun `without a minimum the amounts are the defaults`() {
        assertEquals(listOf(50L, 100L, 500L), quickAmounts(null))
        assertEquals(listOf(50L, 100L, 500L), quickAmounts(BigDecimal.ZERO))
    }

    @Test
    fun `a dollar-like minimum keeps the defaults`() {
        assertEquals(listOf(50L, 100L, 500L), quickAmounts(BigDecimal("25")))
        assertEquals(listOf(50L, 100L, 500L), quickAmounts(BigDecimal("20")))
    }

    @Test
    fun `a minimum in a weaker currency scales the amounts and keeps them round`() {
        assertEquals(listOf(20_000L, 50_000L, 200_000L), quickAmounts(BigDecimal("10000")))
    }

    @Test
    fun `a small minimum gives small round amounts that still grow`() {
        assertEquals(listOf(2L, 5L, 20L), quickAmounts(BigDecimal("1")))
        assertEquals(listOf(1L, 2L, 10L), quickAmounts(BigDecimal("0.5")))
    }
}
