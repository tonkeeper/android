package com.tonapps.perps.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal

class PerpsLimitMarginTest {

    @Test
    fun `reserve is the remaining notional at the initial margin of the leverage`() {
        val order = order(remainingBase = "0.01", limitPrice = "100000")

        assertEquals(BigDecimal("333.40"), order.limitMargin(3))
    }

    @Test
    fun `reserve follows the current limit price after a reprice`() {
        val placed = order(remainingBase = "0.01", limitPrice = "100000")
        val repriced = placed.copy(limitPrice = BigDecimal("90000"))

        assertEquals(BigDecimal("100.00"), placed.limitMargin(10))
        assertEquals(BigDecimal("90.00"), repriced.limitMargin(10))
    }

    @Test
    fun `no leverage leaves the reserve unknown`() {
        assertNull(order(remainingBase = "0.01", limitPrice = "100000").limitMargin(null))
    }

    private fun order(remainingBase: String, limitPrice: String) = PerpsPortfolioOrder(
        marketIndex = 1,
        orderIndex = 1,
        clientOrderIndex = 1,
        symbol = "BTC",
        iconUrl = null,
        side = PerpsPositionSide.LONG,
        remainingBase = BigDecimal(remainingBase),
        limitPrice = BigDecimal(limitPrice),
        leverage = null,
        margin = null,
    )
}
