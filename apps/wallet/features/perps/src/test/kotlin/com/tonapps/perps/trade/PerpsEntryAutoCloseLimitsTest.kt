package com.tonapps.perps.trade

import com.tonapps.chainkit.perpetual.PerpetualReview
import com.tonapps.chainkit.perpetual.PerpsFreshness
import com.tonapps.chainkit.perpetual.PerpsIntegratorFees
import com.tonapps.chainkit.perpetual.PerpsMarketRules
import com.tonapps.chainkit.perpetual.PerpsOrderKind
import com.tonapps.chainkit.perpetual.PerpsScale
import com.tonapps.chainkit.perpetual.PerpsSide
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

class PerpsEntryAutoCloseLimitsTest {

    @Test
    fun `long market open allows the first tick past the estimated entry each way`() {
        val limits = entryAutoCloseLimits(
            review = open(PerpsSide.Long, estimatedEntry = 4_236, liquidation = 4_063),
            rules = RULES,
            markPrice = BigDecimal("0.004236"),
        )

        assertEquals(
            PerpsEntryAutoCloseLimits(
                closestTakeProfit = BigDecimal("0.004237"),
                closestStopLoss = BigDecimal("0.004235"),
            ),
            limits,
        )
    }

    @Test
    fun `short market open mirrors the long one`() {
        val limits = entryAutoCloseLimits(
            review = open(PerpsSide.Short, estimatedEntry = 4_236, liquidation = 4_409),
            rules = RULES,
            markPrice = BigDecimal("0.004236"),
        )

        assertEquals(
            PerpsEntryAutoCloseLimits(
                closestTakeProfit = BigDecimal("0.004235"),
                closestStopLoss = BigDecimal("0.004237"),
            ),
            limits,
        )
    }

    @Test
    fun `book entry above the mark moves the take profit minimum with it`() {
        val limits = entryAutoCloseLimits(
            review = open(PerpsSide.Long, estimatedEntry = 4_238, liquidation = 4_063),
            rules = RULES,
            markPrice = BigDecimal("0.004236"),
        )

        assertEquals(BigDecimal("0.004238"), limits?.closestTakeProfit)
    }

    @Test
    fun `resting long limit is measured from the limit, not the mark above it`() {
        val limits = entryAutoCloseLimits(
            review = open(PerpsSide.Long, estimatedEntry = 4_200, liquidation = 4_030, kind = PerpsOrderKind.Limit),
            rules = RULES,
            markPrice = BigDecimal("0.004236"),
        )

        assertEquals(
            PerpsEntryAutoCloseLimits(
                closestTakeProfit = BigDecimal("0.004201"),
                closestStopLoss = BigDecimal("0.004199"),
            ),
            limits,
        )
    }

    @Test
    fun `no liquidation leaves the stop loss unbounded`() {
        val limits = entryAutoCloseLimits(
            review = open(PerpsSide.Long, estimatedEntry = 4_236, liquidation = null),
            rules = RULES,
            markPrice = BigDecimal("0.004236"),
        )

        assertEquals(
            PerpsEntryAutoCloseLimits(closestTakeProfit = BigDecimal("0.004237"), closestStopLoss = null),
            limits,
        )
    }

    private fun open(
        side: PerpsSide,
        estimatedEntry: Long,
        liquidation: Long?,
        kind: PerpsOrderKind = PerpsOrderKind.Market,
    ) = PerpetualReview.Open(
        side = side,
        kind = kind,
        scale = SCALE,
        marginBudgetQuote = 2_000_000,
        initialMarginBps = 1_000,
        baseAmount = 4_721,
        notionalQuote = 20_000_000,
        referencePrice = estimatedEntry,
        estimatedEntryPrice = estimatedEntry,
        estimatedFeeQuote = 0,
        estimatedCollateralQuote = 2_000_000,
        priceBoundOrLimit = estimatedEntry,
        maxNotionalQuote = 20_000_000,
        maxFeeQuote = 0,
        liquidationPrice = liquidation,
    )

    private companion object {
        val SCALE = PerpsScale(priceDecimals = 6, baseDecimals = 0, quoteDecimals = 6)
        val RULES = PerpsMarketRules(
            scopeEnvironment = PerpsProductRules.ENVIRONMENT,
            marketId = 1,
            quoteAsset = PerpsProductRules.QUOTE_ASSET,
            tradingStatus = "active",
            scale = SCALE,
            priceTick = 1,
            baseStep = 1,
            minBaseAmount = 1,
            makerFeePpm = 0,
            takerFeePpm = 0,
            minInitialMarginBps = 500,
            maxInitialMarginBps = 10_000,
            maintenanceMarginBps = 300,
            priceSlippagePpm = PerpsProductRules.PRICE_SLIPPAGE_PPM,
            tpslPriceSlippagePpm = PerpsProductRules.TPSL_PRICE_SLIPPAGE_PPM,
            tpslBufferPpm = PerpsProductRules.TPSL_BUFFER_PPM,
            minAdjustMarginQuote = 1_000_000,
            removeMarginBufferPpm = PerpsProductRules.REMOVE_MARGIN_BUFFER_PPM,
            minRemoveMarginBufferQuote = 10_000,
            integrator = PerpsIntegratorFees.NONE,
            freshness = PerpsFreshness(receivedAtUnixMs = 0),
        )
    }
}
