package com.tonapps.perps.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class PerpsLiquidationPriceTest {

    @Test
    fun `limit short above mark liquidates above entry`() {
        val liquidation = liquidation(PerpsOrderSide.SHORT, ENTRY, MARK)!!

        assertTrue(liquidation > ENTRY)
        assertEquals(BigDecimal("3007.28"), liquidation)
    }

    @Test
    fun `long liquidates below entry`() {
        val liquidation = liquidation(PerpsOrderSide.LONG, ENTRY, MARK)!!

        assertTrue(liquidation < ENTRY)
        assertEquals(BigDecimal("2891.76"), liquidation)
    }

    @Test
    fun `missing or zero prices give no estimate`() {
        assertNull(liquidation(PerpsOrderSide.SHORT, null, MARK))
        assertNull(liquidation(PerpsOrderSide.SHORT, ENTRY, null))
        assertNull(liquidation(PerpsOrderSide.SHORT, BigDecimal.ZERO, MARK))
        assertNull(liquidation(PerpsOrderSide.SHORT, ENTRY, BigDecimal.ZERO))
    }

    private fun liquidation(side: PerpsOrderSide, entry: BigDecimal?, mark: BigDecimal?) = perpsLiquidationPrice(
        side = side,
        entryPrice = entry,
        markPrice = mark,
        leverage = 20,
        maintenanceMarginBps = 300,
        priceDecimals = 2,
    )

    private companion object {
        val ENTRY = BigDecimal("2950")
        val MARK = BigDecimal("2900")
    }
}
