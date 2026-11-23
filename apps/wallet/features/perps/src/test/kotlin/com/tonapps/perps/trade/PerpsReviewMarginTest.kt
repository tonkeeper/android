package com.tonapps.perps.trade

import com.tonapps.chainkit.perpetual.PerpetualReview
import com.tonapps.chainkit.perpetual.PerpsOrderKind
import com.tonapps.chainkit.perpetual.PerpsScale
import com.tonapps.chainkit.perpetual.PerpsSide
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

class PerpsReviewMarginTest {

    @Test
    fun `market open pays the planner collateral plus fee, not the typed budget`() {
        val review = open(
            kind = PerpsOrderKind.Market,
            initialMarginBps = 3334,
            notionalQuote = 14_690_000,
            estimatedCollateralQuote = 4_900_000,
        )

        assertEquals(BigDecimal("4.900000"), review.allocatedMargin())
        assertEquals(BigDecimal("4.913200"), review.youPay())
    }

    @Test
    fun `limit open reserves notional at initial margin, not the collateral`() {
        val review = open(
            kind = PerpsOrderKind.Limit,
            initialMarginBps = 1000,
            notionalQuote = 940_000_000,
            estimatedCollateralQuote = 98_000_000,
        )

        assertEquals(BigDecimal("94.000000"), review.allocatedMargin())
        assertEquals(BigDecimal("94.013200"), review.youPay())
    }

    @Test
    fun `short limit above the mark reserves more than the typed budget until sizing moves to the limit`() {
        val review = open(
            kind = PerpsOrderKind.Limit,
            initialMarginBps = 1000,
            notionalQuote = 1_200_000_000,
            estimatedCollateralQuote = 100_000_000,
            marginBudgetQuote = 100_000_000,
            side = PerpsSide.Short,
        )

        assertEquals(BigDecimal("120.000000"), review.allocatedMargin())
    }

    @Test
    fun `add pays the collateral above the margin allocated before plus fee`() {
        val review = PerpetualReview.Add(
            side = PerpsSide.Long,
            kind = PerpsOrderKind.Market,
            scale = SCALE,
            marginBudgetQuote = 5_000_000,
            initialMarginBps = 1000,
            baseAmount = 1_000,
            notionalQuote = 50_000_000,
            referencePrice = 50_000,
            estimatedEntryPrice = 50_000,
            mergedBaseAmount = 3_000,
            mergedEntryPrice = 50_000,
            estimatedFeeQuote = 13_200,
            estimatedCollateralQuote = 15_000_000,
            priceBoundOrLimit = 52_500,
            maxNotionalQuote = 52_500_000,
            maxFeeQuote = 14_000,
        )

        assertEquals(BigDecimal("5.013200"), review.youPay(allocatedBefore = 10_000_000))
    }

    private fun open(
        kind: PerpsOrderKind,
        initialMarginBps: Int,
        notionalQuote: Long,
        estimatedCollateralQuote: Long,
        marginBudgetQuote: Long = 5_000_000,
        side: PerpsSide = PerpsSide.Long,
    ) = PerpetualReview.Open(
        side = side,
        kind = kind,
        scale = SCALE,
        marginBudgetQuote = marginBudgetQuote,
        initialMarginBps = initialMarginBps,
        baseAmount = 18,
        notionalQuote = notionalQuote,
        referencePrice = 81_611,
        estimatedEntryPrice = 81_611,
        estimatedFeeQuote = 13_200,
        estimatedCollateralQuote = estimatedCollateralQuote,
        priceBoundOrLimit = 85_692,
        maxNotionalQuote = 15_500_000,
        maxFeeQuote = 14_000,
    )

    private companion object {
        const val QUOTE_DECIMALS = 6
        val SCALE = PerpsScale(priceDecimals = 1, baseDecimals = 5, quoteDecimals = QUOTE_DECIMALS)
    }
}
