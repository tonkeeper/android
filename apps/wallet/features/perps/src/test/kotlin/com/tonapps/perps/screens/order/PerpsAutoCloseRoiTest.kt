package com.tonapps.perps.screens.order

import com.tonapps.perps.data.PerpsAutoCloseDraft
import com.tonapps.perps.data.PerpsOrderSide
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class PerpsAutoCloseRoiTest {

    @Test
    fun `long take profit below entry flips to minus`() {
        val roi = roiFromPrice(ENTRY, BigDecimal("99"), LEVERAGE, PerpsOrderSide.LONG)

        assertEquals(0, BigDecimal("-10").compareTo(roi))
        assertEquals(-1, roiSign(roi, TAKE_PROFIT_SIGN))
    }

    @Test
    fun `long stop loss above entry flips to plus`() {
        val roi = roiFromPrice(ENTRY, BigDecimal("101"), LEVERAGE, PerpsOrderSide.LONG)

        assertEquals(0, BigDecimal("10").compareTo(roi))
        assertEquals(1, roiSign(roi, STOP_LOSS_SIGN))
    }

    @Test
    fun `short legs flip the other way`() {
        val takeProfitRoi = roiFromPrice(ENTRY, BigDecimal("101"), LEVERAGE, PerpsOrderSide.SHORT)
        val stopLossRoi = roiFromPrice(ENTRY, BigDecimal("99"), LEVERAGE, PerpsOrderSide.SHORT)

        assertEquals(-1, roiSign(takeProfitRoi, TAKE_PROFIT_SIGN))
        assertEquals(1, roiSign(stopLossRoi, STOP_LOSS_SIGN))
    }

    @Test
    fun `price at entry keeps the leg default sign`() {
        val roi = roiFromPrice(ENTRY, ENTRY, LEVERAGE, PerpsOrderSide.LONG)

        assertEquals(TAKE_PROFIT_SIGN, roiSign(roi, TAKE_PROFIT_SIGN))
        assertEquals(STOP_LOSS_SIGN, roiSign(roi, STOP_LOSS_SIGN))
        assertEquals(STOP_LOSS_SIGN, roiSign(null, STOP_LOSS_SIGN))
    }

    @Test
    fun `default signs keep the original preset prices`() {
        assertPrice("101.00", TAKE_PROFIT_SIGN, PerpsOrderSide.LONG)
        assertPrice("99.00", STOP_LOSS_SIGN, PerpsOrderSide.LONG)
        assertPrice("99.00", TAKE_PROFIT_SIGN, PerpsOrderSide.SHORT)
        assertPrice("101.00", STOP_LOSS_SIGN, PerpsOrderSide.SHORT)
    }

    @Test
    fun `flipped sign moves the price to the other side of entry`() {
        assertPrice("99.00", -TAKE_PROFIT_SIGN, PerpsOrderSide.LONG)
        assertPrice("101.00", -STOP_LOSS_SIGN, PerpsOrderSide.LONG)
        assertPrice("101.00", -TAKE_PROFIT_SIGN, PerpsOrderSide.SHORT)
        assertPrice("99.00", -STOP_LOSS_SIGN, PerpsOrderSide.SHORT)
    }

    @Test
    fun `typed percent reprices the leg from the new entry`() {
        val draft = PerpsAutoCloseDraft(
            takeProfit = BigDecimal("101.00"),
            stopLoss = BigDecimal("99.00"),
            takeProfitRoi = BigDecimal("10"),
            stopLossRoi = BigDecimal("-10"),
        )

        val repriced = draft.repriced(BigDecimal("200"), LEVERAGE, 2, PerpsOrderSide.LONG)

        assertEquals(
            PerpsAutoCloseDraft(
                takeProfit = BigDecimal("202.00"),
                stopLoss = BigDecimal("198.00"),
                takeProfitRoi = BigDecimal("10"),
                stopLossRoi = BigDecimal("-10"),
            ),
            repriced,
        )
    }

    @Test
    fun `typed price keeps the leg`() {
        val draft = PerpsAutoCloseDraft(takeProfit = BigDecimal("101.00"), stopLoss = BigDecimal("99.00"))

        assertEquals(draft, draft.repriced(BigDecimal("200"), LEVERAGE, 2, PerpsOrderSide.LONG))
    }

    @Test
    fun `flipped typed percent stays on its side of entry`() {
        val draft = PerpsAutoCloseDraft(
            takeProfit = BigDecimal("101.00"),
            stopLoss = null,
            takeProfitRoi = BigDecimal("-10"),
        )

        val repriced = draft.repriced(BigDecimal("200"), LEVERAGE, 2, PerpsOrderSide.SHORT)

        assertEquals(BigDecimal("202.00"), repriced?.takeProfit)
    }

    @Test
    fun `typed percent past a full loss keeps the leg and is unplaceable`() {
        val draft = PerpsAutoCloseDraft(
            takeProfit = BigDecimal("101.00"),
            stopLoss = BigDecimal("50.00"),
            takeProfitRoi = BigDecimal("10"),
            stopLossRoi = BigDecimal("-100"),
        )

        val repriced = draft.repriced(ENTRY, BigDecimal.ONE, 2, PerpsOrderSide.LONG)

        assertEquals(draft.copy(takeProfit = BigDecimal("110.00")), repriced)
        assertTrue(draft.hasUnplaceableRoi(ENTRY, BigDecimal.ONE, 2, PerpsOrderSide.LONG))
        assertFalse(draft.hasUnplaceableRoi(ENTRY, BigDecimal("2"), 2, PerpsOrderSide.LONG))
    }

    @Test
    fun `typed price is never unplaceable`() {
        val draft = PerpsAutoCloseDraft(takeProfit = BigDecimal("101.00"), stopLoss = BigDecimal("50.00"))

        assertFalse(draft.hasUnplaceableRoi(ENTRY, BigDecimal.ONE, 2, PerpsOrderSide.SHORT))
    }

    @Test
    fun `percent is cut toward zero as the order rows show it`() {
        assertEquals(BigDecimal("4.33"), roiFromPrice(ENTRY, BigDecimal("100.4339"), LEVERAGE, PerpsOrderSide.LONG))
        assertEquals(BigDecimal("-4.33"), roiFromPrice(ENTRY, BigDecimal("99.5661"), LEVERAGE, PerpsOrderSide.LONG))
        assertEquals(BigDecimal("-4.33"), roiFromPrice(ENTRY, BigDecimal("100.4339"), LEVERAGE, PerpsOrderSide.SHORT))
    }

    @Test
    fun `percent landing exactly on a hundredth keeps it`() {
        assertEquals(BigDecimal("4.33"), roiFromPrice(ENTRY, BigDecimal("100.433"), LEVERAGE, PerpsOrderSide.LONG))
        assertEquals(BigDecimal("-4.33"), roiFromPrice(ENTRY, BigDecimal("99.567"), LEVERAGE, PerpsOrderSide.LONG))
    }

    private fun assertPrice(expected: String, roiSign: Int, side: PerpsOrderSide) {
        val price = priceFromPercent(ENTRY, BigDecimal("10"), roiSign, LEVERAGE, 2, side)

        assertEquals(BigDecimal(expected), price)
    }

    private companion object {
        val ENTRY = BigDecimal("100")
        val LEVERAGE = BigDecimal("10")
    }
}
