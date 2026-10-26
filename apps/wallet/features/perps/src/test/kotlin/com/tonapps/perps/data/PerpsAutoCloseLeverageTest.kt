package com.tonapps.perps.data

import com.tonapps.perps.screens.order.roiFromPrice
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

class PerpsAutoCloseLeverageTest {

    @Test
    fun `percent on a live position matches the projected roi of its legs`() {
        val position = position(leverage = 15, margin = BigDecimal("1.001086"))
        val leverage = position.autoCloseLeverage()

        val takeProfitRoi = roiFromPrice(ENTRY, BigDecimal("0.081181"), leverage, PerpsOrderSide.LONG)
        val stopLossRoi = roiFromPrice(ENTRY, BigDecimal("0.079045"), leverage, PerpsOrderSide.LONG)

        assertEquals(BigDecimal("19.57"), takeProfitRoi)
        assertEquals(BigDecimal("-20.40"), stopLossRoi)
    }

    @Test
    fun `added margin keeps the nominal leverage`() {
        val leverage = position(leverage = 15, margin = BigDecimal("2.88")).autoCloseLeverage()

        assertEquals(BigDecimal(15), leverage)
    }

    @Test
    fun `no nominal leverage falls back to the value over the margin`() {
        val leverage = position(
            leverage = null,
            margin = BigDecimal("2"),
            positionValue = BigDecimal("30"),
        ).autoCloseLeverage()

        assertEquals(0, BigDecimal(15).compareTo(leverage))
    }

    @Test
    fun `nothing to derive the leverage from counts the percent at 1x`() {
        assertEquals(BigDecimal.ONE, position(leverage = null, margin = null).autoCloseLeverage())
    }

    private fun position(
        leverage: Int?,
        margin: BigDecimal?,
        positionValue: BigDecimal? = null,
    ) = PerpsOpenPosition(
        id = "lighter:3",
        marketIndex = 3,
        symbol = "DOGE",
        side = PerpsPositionSide.LONG,
        size = SIZE,
        positionValue = positionValue,
        avgEntryPrice = ENTRY,
        markPrice = null,
        liquidationPrice = null,
        liquidationDistancePct = null,
        margin = margin,
        equity = null,
        unrealizedPnl = null,
        roiPct = null,
        leverage = leverage,
        fundingPaid = null,
    )

    private companion object {
        val SIZE = BigDecimal("187")
        val ENTRY = BigDecimal("0.080135")
    }
}
