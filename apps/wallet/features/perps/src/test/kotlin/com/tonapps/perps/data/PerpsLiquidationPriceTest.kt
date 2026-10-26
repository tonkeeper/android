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
        assertEquals((2950.0 + 2900.0 * 0.05) / 1.03, liquidation.toDouble(), DELTA)
    }

    @Test
    fun `long liquidates below entry`() {
        val liquidation = liquidation(PerpsOrderSide.LONG, ENTRY, MARK)!!

        assertTrue(liquidation < ENTRY)
        assertEquals((2950.0 - 2900.0 * 0.05) / 0.97, liquidation.toDouble(), DELTA)
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
    )

    private companion object {
        val ENTRY = BigDecimal("2950")
        val MARK = BigDecimal("2900")
        const val DELTA = 1e-6
    }
}
