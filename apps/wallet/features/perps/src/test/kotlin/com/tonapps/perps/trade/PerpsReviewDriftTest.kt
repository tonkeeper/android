package com.tonapps.perps.trade

import com.tonapps.chainkit.perpetual.PerpetualReview
import com.tonapps.chainkit.perpetual.PerpsOrderKind
import com.tonapps.chainkit.perpetual.PerpsScale
import com.tonapps.chainkit.perpetual.PerpsSide
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PerpsReviewDriftTest {

    @Test
    fun `an identical re-plan keeps within the review`() {
        assertTrue(open().keepsWithin(open()))
        assertTrue(close().keepsWithin(close()))
    }

    @Test
    fun `the worst price may move up to two percent`() {
        assertTrue(open(priceBoundOrLimit = 87_320).keepsWithin(open()))
        assertFalse(open(priceBoundOrLimit = 87_492).keepsWithin(open()))
    }

    @Test
    fun `side, leverage and triggers must match exactly`() {
        assertFalse(open(side = PerpsSide.Short).keepsWithin(open()))
        assertFalse(open(initialMarginBps = 1_001).keepsWithin(open()))
        assertFalse(open(takeProfitTrigger = TAKE_PROFIT + 1).keepsWithin(open()))
    }

    @Test
    fun `a fee change inside a cent is not drift`() {
        assertTrue(open(estimatedFeeQuote = 13_000).keepsWithin(open(estimatedFeeQuote = 4_000)))
    }

    @Test
    fun `a liquidation price that appears or vanishes is drift`() {
        assertFalse(open(liquidationPrice = null).keepsWithin(open()))
        assertFalse(open().keepsWithin(open(liquidationPrice = null)))
    }

    @Test
    fun `a close that turns into a whole exit is drift`() {
        assertFalse(close(fullExit = true).keepsWithin(close()))
    }

    @Test
    fun `a different operation is drift`() {
        assertFalse(close().keepsWithin(open()))
    }

    @Test
    fun `a different scale is drift`() {
        val rescaled = PerpsScale(priceDecimals = 2, baseDecimals = 5, quoteDecimals = 6)

        assertFalse(open(scale = rescaled).keepsWithin(open()))
    }

    @Test
    fun `an open that becomes an immediate risk is drift`() {
        assertFalse(open(isImmediateRisk = true).keepsWithin(open()))
    }

    @Test
    fun `a margin change that becomes an immediate risk is drift`() {
        assertFalse(margin(isImmediateRisk = true).keepsWithin(margin()))
    }

    @Test
    fun `a margin change that is no longer an immediate risk keeps within the review`() {
        assertTrue(margin().keepsWithin(margin(isImmediateRisk = true)))
    }

    private fun open(
        side: PerpsSide = PerpsSide.Long,
        scale: PerpsScale = SCALE,
        initialMarginBps: Int = 1_000,
        estimatedFeeQuote: Long = 13_200,
        priceBoundOrLimit: Long = 85_692,
        takeProfitTrigger: Long? = TAKE_PROFIT,
        liquidationPrice: Long? = 75_000,
        isImmediateRisk: Boolean = false,
    ) = PerpetualReview.Open(
        side = side,
        kind = PerpsOrderKind.Market,
        scale = scale,
        marginBudgetQuote = 5_000_000,
        initialMarginBps = initialMarginBps,
        baseAmount = 600,
        notionalQuote = 49_000_000,
        referencePrice = 81_611,
        estimatedEntryPrice = 81_611,
        estimatedFeeQuote = estimatedFeeQuote,
        estimatedCollateralQuote = 4_900_000,
        priceBoundOrLimit = priceBoundOrLimit,
        maxNotionalQuote = 51_500_000,
        maxFeeQuote = 14_000,
        takeProfitTrigger = takeProfitTrigger,
        stopLossTrigger = 78_000,
        liquidationPrice = liquidationPrice,
        isImmediateRisk = isImmediateRisk,
    )

    private fun margin(isImmediateRisk: Boolean = false) = PerpetualReview.Margin(
        scale = SCALE,
        addToPosition = false,
        amountQuote = 1_000_000,
        allocatedMarginAfterQuote = 4_000_000,
        liquidationPrice = 75_000,
        isImmediateRisk = isImmediateRisk,
    )

    private fun close(fullExit: Boolean = false) = PerpetualReview.Close(
        side = PerpsSide.Long,
        scale = SCALE,
        fullExit = fullExit,
        closingBaseAmount = 300,
        estimatedFillBaseAmount = 300,
        remainingBaseAmount = 300,
        notionalQuote = 24_500_000,
        referencePrice = 81_611,
        estimatedExitPrice = 81_611,
        estimatedFeeQuote = 6_600,
        estimatedRealizedPnlQuote = 0,
        estimatedReceiveQuote = 2_450_000,
        estimatedReturnedMarginQuote = 2_450_000,
        priceBound = 77_530,
        maxNotionalQuote = 24_500_000,
        maxFeeQuote = 7_000,
        liquidationPrice = 75_000,
    )

    private companion object {
        const val TAKE_PROFIT = 90_000L
        val SCALE = PerpsScale(priceDecimals = 1, baseDecimals = 5, quoteDecimals = 6)
    }
}
