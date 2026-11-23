package com.tonapps.perps.screens.order

import com.tonapps.perps.data.PerpsAutoCloseDraft
import com.tonapps.perps.data.PerpsMarketRules
import com.tonapps.perps.data.PerpsOrderSide
import com.tonapps.perps.trade.PerpsEntryAutoCloseLimits
import com.tonapps.wallet.localization.Localization
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal

class PerpsAutoCloseEntryTest {

    @Test
    fun liquidationReviewedForAnotherLeverageDoesNotStripTheRepricedLeg() {
        val stopLoss = PerpsAutoCloseDraft(takeProfit = null, stopLoss = BigDecimal("98.67"), stopLossRoi = BigDecimal("-20"))
        val entry = PerpsAutoCloseEntry(draft = stopLoss, warning = null, checkedTerms = HIGH_LEVERAGE)

        val lowered = entry.reduce(SIDE, check(LOW_LEVERAGE, liquidation = PerpsDraftLiquidation(HIGH_LEVERAGE, BigDecimal("96"))))

        assertEquals(BigDecimal("96.00"), lowered.draft?.stopLoss)
        assertNull(lowered.warning)

        val reviewed = lowered.reduce(SIDE, check(LOW_LEVERAGE, liquidation = PerpsDraftLiquidation(LOW_LEVERAGE, BigDecimal("82"))))

        assertEquals(BigDecimal("96.00"), reviewed.draft?.stopLoss)
        assertNull(reviewed.warning)
    }

    @Test
    fun liquidationReloadForTheSameTermsMovesTheStripWarningToAStricterLevel() {
        val entry = PerpsAutoCloseEntry(draft = PRICE_STOP_LOSS, warning = null, checkedTerms = LOW_LEVERAGE)
        val liquidation = BigDecimal("98")
        val warning = PerpsAutoCloseIssue(Localization.perps_auto_close_sl_liquidation_first_above, liquidation)

        val stripped = entry.reduce(SIDE, check(HIGH_LEVERAGE, liquidation = PerpsDraftLiquidation(HIGH_LEVERAGE, liquidation)))

        assertNull(stripped.draft)
        assertEquals(warning, stripped.warning)

        val reloading = stripped.reduce(SIDE, check(HIGH_LEVERAGE, liquidation = null))
        val reloaded = reloading.reduce(
            SIDE,
            check(HIGH_LEVERAGE, liquidation = PerpsDraftLiquidation(HIGH_LEVERAGE, BigDecimal("98.2"))),
        )

        assertEquals(warning, reloading.warning)
        assertEquals(warning.copy(price = BigDecimal("98.2")), reloaded.warning)
    }

    @Test
    fun aSameTermsCheckQuotesTheNewLiquidationInTheLiquidationFirstWarning() {
        val entry = PerpsAutoCloseEntry(
            draft = PRICE_TAKE_PROFIT,
            warning = PerpsAutoCloseIssue(Localization.perps_auto_close_sl_liquidation_first_above, BigDecimal("96")),
            checkedTerms = LOW_LEVERAGE,
        )

        val checked = entry.reduce(SIDE, check(LOW_LEVERAGE, liquidation = PerpsDraftLiquidation(LOW_LEVERAGE, BigDecimal("96.5"))))

        assertEquals(PerpsAutoCloseIssue(Localization.perps_auto_close_sl_liquidation_first_above, BigDecimal("96.5")), checked.warning)
        assertEquals(PRICE_TAKE_PROFIT, checked.draft)
    }

    @Test
    fun aSameTermsCheckWithALessStrictLiquidationKeepsTheQuotedOne() {
        val warning = PerpsAutoCloseIssue(Localization.perps_auto_close_sl_liquidation_first_above, BigDecimal("96.5"))
        val entry = PerpsAutoCloseEntry(draft = PRICE_TAKE_PROFIT, warning = warning, checkedTerms = LOW_LEVERAGE)

        val checked = entry.reduce(SIDE, check(LOW_LEVERAGE, liquidation = PerpsDraftLiquidation(LOW_LEVERAGE, BigDecimal("96"))))

        assertEquals(warning, checked.warning)
        assertEquals(PRICE_TAKE_PROFIT, checked.draft)
    }

    @Test
    fun aShortSameTermsCheckQuotesALowerLiquidation() {
        val entry = PerpsAutoCloseEntry(
            draft = null,
            warning = PerpsAutoCloseIssue(Localization.perps_auto_close_sl_liquidation_first_below, BigDecimal("104")),
            checkedTerms = LOW_LEVERAGE,
        )

        val checked = entry.reduce(
            PerpsOrderSide.SHORT,
            check(LOW_LEVERAGE, liquidation = PerpsDraftLiquidation(LOW_LEVERAGE, BigDecimal("103.5"))),
        )

        assertEquals(PerpsAutoCloseIssue(Localization.perps_auto_close_sl_liquidation_first_below, BigDecimal("103.5")), checked.warning)
    }

    @Test
    fun aSameTermsCheckKeepsAWarningWithoutALiquidation() {
        val warning = PerpsAutoCloseIssue(Localization.perps_auto_close_sl_above_current)
        val entry = PerpsAutoCloseEntry(draft = PRICE_TAKE_PROFIT, warning = warning, checkedTerms = LOW_LEVERAGE)

        val checked = entry.reduce(SIDE, check(LOW_LEVERAGE, liquidation = PerpsDraftLiquidation(LOW_LEVERAGE, BigDecimal("96.5"))))

        assertEquals(warning, checked.warning)
        assertEquals(PRICE_TAKE_PROFIT, checked.draft)
    }

    @Test
    fun aSameTermsCheckWithoutALiquidationKeepsTheLiquidationFirstWarning() {
        val warning = PerpsAutoCloseIssue(Localization.perps_auto_close_sl_liquidation_first_above, BigDecimal("96"))
        val entry = PerpsAutoCloseEntry(draft = PRICE_TAKE_PROFIT, warning = warning, checkedTerms = LOW_LEVERAGE)
        val liquidation = resolveAutoCloseLiquidation(LOW_LEVERAGE, reviewed = PerpsDraftLiquidation(LOW_LEVERAGE, null), estimate = null)

        val checked = entry.reduce(SIDE, check(LOW_LEVERAGE, liquidation = liquidation))

        assertEquals(warning, checked.warning)
        assertEquals(PRICE_TAKE_PROFIT, checked.draft)
    }

    @Test
    fun termsChangeThatStripsNothingClearsTheWarning() {
        val entry = PerpsAutoCloseEntry(draft = PRICE_STOP_LOSS, warning = null, checkedTerms = LOW_LEVERAGE)
        val stripped = entry.reduce(SIDE, check(HIGH_LEVERAGE, liquidation = PerpsDraftLiquidation(HIGH_LEVERAGE, BigDecimal("98"))))

        val lowered = stripped.reduce(SIDE, check(LOW_LEVERAGE, liquidation = PerpsDraftLiquidation(HIGH_LEVERAGE, BigDecimal("98"))))

        assertNull(lowered.draft)
        assertNull(lowered.warning)
        assertEquals(LOW_LEVERAGE, lowered.checkedTerms)
    }

    @Test
    fun legsAreCheckedAgainstTheLimitPriceOfTheTerms() {
        val entry = PerpsAutoCloseEntry(draft = PRICE_STOP_LOSS, warning = null, checkedTerms = LOW_LEVERAGE)
        val limit = PerpsAutoCloseTerms(limitPrice = BigDecimal("96"), leverage = 5)

        val limited = entry.reduce(SIDE, check(limit, liquidation = null))

        assertNull(limited.draft)
        assertEquals(PerpsAutoCloseIssue(Localization.perps_auto_close_sl_below_current), limited.warning)
    }

    @Test
    fun settingLegsClearsTheWarningAndKeepsTheCheckedTerms() {
        val stripped = PerpsAutoCloseEntry(
            draft = null,
            warning = PerpsAutoCloseIssue(Localization.perps_auto_close_sl_liquidation_first_above, BigDecimal("98")),
            checkedTerms = HIGH_LEVERAGE,
        )

        val set = stripped.reduce(SIDE, PerpsAutoCloseCmd.Set(PRICE_STOP_LOSS))

        assertEquals(PRICE_STOP_LOSS, set.draft)
        assertNull(set.warning)
        assertEquals(HIGH_LEVERAGE, set.checkedTerms)
    }

    @Test
    fun takeProfitInsideTheEntryBufferIsStrippedWithTheMinimum() {
        val terms = PerpsAutoCloseTerms(limitPrice = null, leverage = 10)
        val takeProfit = PerpsAutoCloseDraft(takeProfit = BigDecimal("100.10"), stopLoss = null, takeProfitRoi = BigDecimal("1"))
        val entry = PerpsAutoCloseEntry(draft = takeProfit, warning = null, checkedTerms = terms)
        val limits = PerpsEntryAutoCloseLimits(closestTakeProfit = BigDecimal("100.20"), closestStopLoss = BigDecimal("99.80"))

        val checked = entry.reduce(SIDE, check(terms, liquidation = PerpsDraftLiquidation(terms, BigDecimal("90.50"), limits)))

        assertNull(checked.draft)
        assertEquals(PerpsAutoCloseIssue(Localization.perps_auto_close_tp_at_least, BigDecimal("100.20")), checked.warning)
    }

    @Test
    fun entryLimitsReviewedForOtherTermsAreIgnored() {
        val terms = PerpsAutoCloseTerms(limitPrice = null, leverage = 10)
        val takeProfit = PerpsAutoCloseDraft(takeProfit = BigDecimal("100.10"), stopLoss = null, takeProfitRoi = BigDecimal("1"))
        val entry = PerpsAutoCloseEntry(draft = takeProfit, warning = null, checkedTerms = terms)
        val limits = PerpsEntryAutoCloseLimits(closestTakeProfit = BigDecimal("100.20"), closestStopLoss = null)

        val checked = entry.reduce(SIDE, check(terms, liquidation = PerpsDraftLiquidation(HIGH_LEVERAGE, null, limits)))

        assertEquals(BigDecimal("100.10"), checked.draft?.takeProfit)
        assertNull(checked.warning)
    }

    @Test
    fun withoutAPlanTheEstimateIsTheLiquidation() {
        val resolved = resolveAutoCloseLiquidation(LOW_LEVERAGE, reviewed = null, estimate = ESTIMATE)

        assertEquals(PerpsDraftLiquidation(LOW_LEVERAGE, ESTIMATE), resolved)
    }

    @Test
    fun aPlanForOtherTermsIsIgnoredForTheEstimate() {
        val reviewed = PerpsDraftLiquidation(HIGH_LEVERAGE, BigDecimal("96"))

        val resolved = resolveAutoCloseLiquidation(LOW_LEVERAGE, reviewed = reviewed, estimate = ESTIMATE)

        assertEquals(PerpsDraftLiquidation(LOW_LEVERAGE, ESTIMATE), resolved)
    }

    @Test
    fun aPlanSettledWithALiquidationWinsOverTheEstimate() {
        val reviewed = PerpsDraftLiquidation(LOW_LEVERAGE, BigDecimal("96"))

        val resolved = resolveAutoCloseLiquidation(LOW_LEVERAGE, reviewed = reviewed, estimate = ESTIMATE)

        assertEquals(reviewed, resolved)
    }

    @Test
    fun aPlanSettledWithoutALiquidationFallsBackToTheEstimateAndKeepsItsEntryLimits() {
        val limits = PerpsEntryAutoCloseLimits(closestTakeProfit = BigDecimal("100.20"), closestStopLoss = BigDecimal("99.80"))
        val reviewed = PerpsDraftLiquidation(LOW_LEVERAGE, price = null, entryLimits = limits)

        val resolved = resolveAutoCloseLiquidation(LOW_LEVERAGE, reviewed = reviewed, estimate = ESTIMATE)

        assertEquals(PerpsDraftLiquidation(LOW_LEVERAGE, ESTIMATE, limits), resolved)
    }

    @Test
    fun theEstimateFallbackStripsAStopLossPastItWithTheLiquidationFirstWarning() {
        val entry = PerpsAutoCloseEntry(draft = PRICE_STOP_LOSS, warning = null, checkedTerms = LOW_LEVERAGE)
        val reviewed = PerpsDraftLiquidation(HIGH_LEVERAGE, price = null)
        val liquidation = resolveAutoCloseLiquidation(HIGH_LEVERAGE, reviewed = reviewed, estimate = ESTIMATE)

        val stripped = entry.reduce(SIDE, check(HIGH_LEVERAGE, liquidation = liquidation))

        assertNull(stripped.draft)
        assertEquals(PerpsAutoCloseIssue(Localization.perps_auto_close_sl_liquidation_first_above, ESTIMATE), stripped.warning)
    }

    @Test
    fun longEstimateRoundsUpToThePriceDecimals() {
        val estimate = autoCloseLiquidationEstimate(PerpsOrderSide.LONG, TEN_X, MARK, RULES)

        assertEquals(BigDecimal("94.74"), estimate)
    }

    @Test
    fun shortEstimateRoundsDownToThePriceDecimals() {
        val estimate = autoCloseLiquidationEstimate(PerpsOrderSide.SHORT, TEN_X, MARK, RULES)

        assertEquals(BigDecimal("104.76"), estimate)
    }

    @Test
    fun longWithoutLeverageHasNoEstimate() {
        val estimate = autoCloseLiquidationEstimate(PerpsOrderSide.LONG, PerpsAutoCloseTerms(limitPrice = null, leverage = 1), MARK, RULES)

        assertNull(estimate)
    }

    @Test
    fun noEstimateWithoutRulesOrMark() {
        assertNull(autoCloseLiquidationEstimate(PerpsOrderSide.LONG, TEN_X, MARK, rules = null))
        assertNull(autoCloseLiquidationEstimate(PerpsOrderSide.LONG, TEN_X, mark = null, RULES))
    }

    @Test
    fun limitTermsEstimateFromTheLimitPrice() {
        val terms = PerpsAutoCloseTerms(limitPrice = BigDecimal("90"), leverage = 10)

        val estimate = autoCloseLiquidationEstimate(PerpsOrderSide.LONG, terms, MARK, RULES)

        assertEquals(BigDecimal("85.27"), estimate)
    }

    @Test
    fun shortLimitAboveTheMarkEstimatesFromTheLimitPrice() {
        val terms = PerpsAutoCloseTerms(limitPrice = BigDecimal("110"), leverage = 10)

        val estimate = autoCloseLiquidationEstimate(PerpsOrderSide.SHORT, terms, MARK, RULES)

        assertEquals(BigDecimal("115.23"), estimate)
    }

    private fun check(terms: PerpsAutoCloseTerms, liquidation: PerpsDraftLiquidation?): PerpsAutoCloseCmd.Check {
        return PerpsAutoCloseCmd.Check(terms = terms, mark = MARK, liquidation = liquidation, priceDecimals = PRICE_DECIMALS)
    }

    private companion object {
        val SIDE = PerpsOrderSide.LONG
        val MARK = BigDecimal("100")
        const val PRICE_DECIMALS = 2
        val LOW_LEVERAGE = PerpsAutoCloseTerms(limitPrice = null, leverage = 5)
        val HIGH_LEVERAGE = PerpsAutoCloseTerms(limitPrice = null, leverage = 15)
        val PRICE_STOP_LOSS = PerpsAutoCloseDraft(takeProfit = null, stopLoss = BigDecimal("97"))
        val PRICE_TAKE_PROFIT = PerpsAutoCloseDraft(takeProfit = BigDecimal("105"), stopLoss = null)
        val ESTIMATE = BigDecimal("98")
        val TEN_X = PerpsAutoCloseTerms(limitPrice = null, leverage = 10)
        val RULES = PerpsMarketRules(
            maxLeverage = 20,
            priceDecimals = PRICE_DECIMALS,
            sizeDecimals = 2,
            tickSize = null,
            stepSize = null,
            minSizeBase = null,
            minSizeQuote = null,
            takerFeePct = null,
            initialMarginBps = null,
            maintenanceMarginBps = 500,
            markPrice = MARK,
            tradable = true,
        )
    }
}
