package com.tonapps.perps.screens.order

import com.tonapps.perps.data.PerpsOrderSide
import com.tonapps.wallet.localization.Localization
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class PerpsAutoCloseRulesTest {

    @Test
    fun longTakeProfitAboveTheMarketButInsideTheBufferNamesTheMinimum() {
        val check = checkAutoClose(PerpsOrderSide.LONG, BigDecimal("0.004243"), null, LONG_BOUNDS)

        assertEquals(PerpsAutoCloseIssue(Localization.perps_auto_close_tp_at_least, BigDecimal("0.004245")), check.takeProfit)
    }

    @Test
    fun longTakeProfitAtTheMinimumPasses() {
        val check = checkAutoClose(PerpsOrderSide.LONG, BigDecimal("0.004245"), null, LONG_BOUNDS)

        assertTrue(check.isValid)
    }

    @Test
    fun longTakeProfitBelowTheMarketKeepsTheCurrentPriceText() {
        val check = checkAutoClose(PerpsOrderSide.LONG, BigDecimal("0.004230"), null, LONG_BOUNDS)

        assertEquals(PerpsAutoCloseIssue(Localization.perps_review_tp_update_text_long), check.takeProfit)
    }

    @Test
    fun longStopLossInsideTheBufferNamesTheMaximum() {
        val check = checkAutoClose(PerpsOrderSide.LONG, null, BigDecimal("0.004230"), LONG_BOUNDS)

        assertEquals(PerpsAutoCloseIssue(Localization.perps_auto_close_sl_at_most, BigDecimal("0.004227")), check.stopLoss)
        assertNull(checkAutoClose(PerpsOrderSide.LONG, null, BigDecimal("0.004227"), LONG_BOUNDS).stopLoss)
    }

    @Test
    fun shortLegsInsideTheBufferNameTheirLimits() {
        val bounds = LONG_BOUNDS.copy(
            liquidationPrice = BigDecimal("0.004409"),
            closestTakeProfit = BigDecimal("0.004227"),
            closestStopLoss = BigDecimal("0.004245"),
        )

        val check = checkAutoClose(PerpsOrderSide.SHORT, BigDecimal("0.004230"), BigDecimal("0.004240"), bounds)

        assertEquals(PerpsAutoCloseIssue(Localization.perps_auto_close_tp_at_most, BigDecimal("0.004227")), check.takeProfit)
        assertEquals(PerpsAutoCloseIssue(Localization.perps_auto_close_sl_at_least, BigDecimal("0.004245")), check.stopLoss)
        assertTrue(checkAutoClose(PerpsOrderSide.SHORT, BigDecimal("0.004227"), BigDecimal("0.004245"), bounds).isValid)
    }

    @Test
    fun withoutLimitsOnlyTheMarketIsChecked() {
        val bounds = LONG_BOUNDS.copy(closestTakeProfit = null, closestStopLoss = null)

        assertTrue(checkAutoClose(PerpsOrderSide.LONG, BigDecimal("0.004237"), BigDecimal("0.004235"), bounds).isValid)
    }

    @Test
    fun longStopLossAtOrBelowTheLiquidationAsksToSetItAbove() {
        val bounds = LONG_BOUNDS.copy(closestStopLoss = null)

        val check = checkAutoClose(PerpsOrderSide.LONG, null, BigDecimal("0.004063"), bounds)

        assertEquals(
            PerpsAutoCloseIssue(Localization.perps_auto_close_sl_liquidation_first_above, BigDecimal("0.004063")),
            check.stopLoss,
        )
        assertTrue(checkAutoClose(PerpsOrderSide.LONG, null, BigDecimal("0.004064"), bounds).isValid)
    }

    @Test
    fun shortStopLossAtOrAboveTheLiquidationAsksToSetItBelow() {
        val bounds = LONG_BOUNDS.copy(liquidationPrice = BigDecimal("0.004409"), closestTakeProfit = null, closestStopLoss = null)

        val check = checkAutoClose(PerpsOrderSide.SHORT, null, BigDecimal("0.004409"), bounds)

        assertEquals(
            PerpsAutoCloseIssue(Localization.perps_auto_close_sl_liquidation_first_below, BigDecimal("0.004409")),
            check.stopLoss,
        )
        assertTrue(checkAutoClose(PerpsOrderSide.SHORT, null, BigDecimal("0.004408"), bounds).isValid)
    }

    private companion object {
        val LONG_BOUNDS = PerpsAutoCloseBounds(
            entryPrice = BigDecimal("0.004236"),
            leverage = BigDecimal(10),
            currentPrice = BigDecimal("0.004236"),
            liquidationPrice = BigDecimal("0.004063"),
            closestTakeProfit = BigDecimal("0.004245"),
            closestStopLoss = BigDecimal("0.004227"),
        )
    }
}
