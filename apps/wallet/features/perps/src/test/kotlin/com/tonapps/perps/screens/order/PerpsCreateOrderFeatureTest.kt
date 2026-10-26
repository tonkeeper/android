package com.tonapps.perps.screens.order

import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.blockchain.model.legacy.WalletType
import com.tonapps.perps.data.PerpsAutoCloseDraft
import com.tonapps.perps.data.PerpsLivePrices
import com.tonapps.perps.data.PerpsOrderSide
import com.tonapps.perps.data.PerpsRepository
import com.tonapps.perps.data.perpsTicker
import com.tonapps.perps.trade.PerpsPlannerAdapter
import com.tonapps.wallet.api.API
import com.tonapps.wallet.data.multichain.account.UnifiedAccountRepository
import com.tonapps.wallet.data.multichain.lighter.PerpetualRepository
import com.tonapps.wallet.data.settings.SettingsRepository
import com.tonapps.wallet.localization.Localization
import com.tonapps.wallet.testing.BaseUnitTest
import com.tonapps.wallet.testing.TestWallet
import io.infrastructure.Success
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.perpsapi.models.Balance
import io.perpsapi.models.Integrator
import io.perpsapi.models.Market
import io.perpsapi.models.OrderBookLevel
import io.perpsapi.models.TradingFlags
import io.perpsapi.models.TradingScreen
import io.perpsapi.models.TruncatedOrderBook
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.OffsetDateTime

class PerpsCreateOrderFeatureTest : BaseUnitTest() {

    private val api = mockk<API>()
    private val adapter = PerpsPlannerAdapter(
        repository = PerpsRepository(api, mockk(), mockk()),
        accountService = mockk(),
        clientOrderIndex = mockk { every { next(any()) } answers { firstArg() } },
        mediator = mockk(),
    )
    private val perpetualRepository = mockk<PerpetualRepository>()
    private val livePrices = mockk<PerpsLivePrices>()
    private val unifiedAccountRepository = mockk<UnifiedAccountRepository>()
    private val settingsRepository = mockk<SettingsRepository> {
        every { hiddenBalances } returns false
        every { hiddenBalancesFlow } returns MutableStateFlow(false)
    }
    private val prices = MutableStateFlow(mapOf(perpsTicker(SYMBOL) to BigDecimal("3.62")))

    private val scheduler get() = dispatchers.scheduler

    @Before
    fun setUp() {
        val wallet = mockk<WalletEntity>()
        every { wallet.type } returns WalletType.Multichain
        every { wallet.id } returns TestWallet.ID
        every { unifiedAccountRepository.selectedTonWalletFlow } returns flowOf(wallet)
        every { livePrices.observe(any()) } returns prices
        coEvery { perpetualRepository.signingCredentials(TestWallet.ID) } returns null
        every { api.perps.integrator.getIntegrator(xWalletId = any()) } returns Integrator(
            integratorAccountIndex = 0,
            integratorTakerFee = 0,
            integratorMakerFee = 0,
            maxPerpsTakerFee = 0,
            maxPerpsMakerFee = 0,
            maxSpotTakerFee = 0,
            maxSpotMakerFee = 0,
            approvalTtlMs = 0,
        )
        book("3.62")
        market(litMarket)
    }

    @Test
    fun toggleTurnsTheDollarMarginIntoTheAssetSize() {
        val feature = open()
        enter(feature, "5")

        toggle(feature)

        assertTrue(feature.inputInSize.value)
        assertEquals("6.9", feature.amount.value)
        assertEquals(1, feature.amountFractionDigits.value)
        assertEquals(BigDecimal("4.995600"), feature.marginQuote.value)
    }

    @Test
    fun assetSizeSetsTheMarginAtTheLivePrice() {
        val feature = open()
        toggle(feature)

        enter(feature, "100")

        assertEquals(BigDecimal("72.400000"), feature.marginQuote.value)
        assertEquals(BigDecimal("100.0"), feature.sizeBase.value)

        tick("3.70")

        assertEquals(BigDecimal("74.000000"), feature.marginQuote.value)
        assertEquals(BigDecimal("100.0"), feature.sizeBase.value)
    }

    @Test
    fun assetSizeKeepsTheMarketPrecision() {
        val feature = open()
        toggle(feature)

        enter(feature, "12.345")

        assertEquals("12.3", feature.amount.value)
    }

    @Test
    fun wholeLotMarketsTakeWholeNumbers() {
        market(litMarket.copy(priceDecimals = 6, sizeDecimals = 0, stepSize = "1"))
        val feature = open()
        toggle(feature)

        enter(feature, "12.5")

        assertEquals("12", feature.amount.value)
        assertEquals(0, feature.amountFractionDigits.value)
    }

    @Test
    fun assetMarginRoundsUpSoTheSizeSurvivesTheWayBack() {
        market(litMarket.copy(priceDecimals = 6, sizeDecimals = 0, stepSize = "1", markPrice = "0.12345679"))
        prices.value = emptyMap()
        val feature = open()
        toggle(feature)

        enter(feature, "100")

        val margin = feature.marginQuote.value
        assertEquals(BigDecimal("2.469136"), margin)
        assertEquals(
            BigDecimal("100"),
            margin.multiply(BigDecimal(LEVERAGE)).divide(BigDecimal("0.12345679"), 0, RoundingMode.DOWN),
        )
    }

    @Test
    fun assetMarginUsesTheSizeFlooredToTheStep() {
        market(litMarket.copy(stepSize = "0.5"))
        val feature = open()
        toggle(feature)

        enter(feature, "1.3")

        assertEquals(BigDecimal("1.0"), feature.sizeBase.value)
        assertEquals(BigDecimal("0.724000"), feature.marginQuote.value)
    }

    @Test
    fun assetMarginRoundsUpOnTheMarketQuoteScale() {
        market(litMarket.copy(priceDecimals = 4, markPrice = "3.6213"))
        prices.value = emptyMap()
        val feature = open()
        toggle(feature)

        enter(feature, "6.9")

        assertEquals(BigDecimal("4.99740"), feature.marginQuote.value)
    }

    @Test
    fun assetSizeBelowOneStepAsksForTheMinimum() {
        market(litMarket.copy(stepSize = "0.5"))
        val feature = open()
        toggle(feature)

        enter(feature, "0.3")

        assertEquals(PerpsOrderIssue.BelowMinMargin(BigDecimal("1.00"), canDeposit = false), feature.issue.value)
        assertFalse(feature.canReview.value)
    }

    @Test
    fun toggleBackWithoutEditsRestoresTheTypedSize() {
        market(litMarket.copy(markPrice = "0.8237"))
        prices.value = emptyMap()
        val feature = open()
        toggle(feature)
        enter(feature, "100")

        toggle(feature)
        assertEquals("16.47", feature.amount.value)
        toggle(feature)

        assertTrue(feature.inputInSize.value)
        assertEquals("100", feature.amount.value)
    }

    @Test
    fun leverageChangeAfterToggleConvertsAgain() {
        market(litMarket.copy(markPrice = "0.8237"))
        prices.value = emptyMap()
        val feature = open()
        toggle(feature)
        enter(feature, "100")
        toggle(feature)

        feature.sendAction(PerpsCreateOrderAction.SetLeverage(1))
        scheduler.runCurrent()
        toggle(feature)

        assertEquals("19.9", feature.amount.value)
    }

    @Test
    fun dollarsBelowOneStepShowZeroWithTheMinimum() {
        market(litMarket.copy(priceDecimals = 6, sizeDecimals = 0, stepSize = "1", markPrice = "50"))
        book("50")
        prices.value = emptyMap()
        val feature = open()
        enter(feature, "1")

        toggle(feature)

        assertEquals("0", feature.amount.value)
        assertEquals(PerpsOrderIssue.BelowMinMargin(BigDecimal("10.03"), canDeposit = false), feature.issue.value)
        assertFalse(feature.canReview.value)

        toggle(feature)

        assertFalse(feature.inputInSize.value)
        assertEquals("1", feature.amount.value)
    }

    @Test
    fun theMinimumMarginIsThePlannersButAtLeastOneDollar() {
        market(litMarket.copy(minSizeBase = "2", minSizeQuote = "10"))
        val feature = open()
        enter(feature, "0.99")

        assertEquals(PerpsOrderIssue.BelowMinMargin(BigDecimal("1.00"), canDeposit = false), feature.issue.value)
        assertFalse(feature.canReview.value)

        enter(feature, "1")

        assertNull(feature.issue.value)
        assertTrue(feature.canReview.value)

        feature.sendAction(PerpsCreateOrderAction.SetLimitPrice(BigDecimal("3.62")))
        scheduler.runCurrent()

        assertEquals(PerpsOrderIssue.BelowMinMargin(BigDecimal("2.04"), canDeposit = false), feature.issue.value)
        assertFalse(feature.canReview.value)

        enter(feature, "2.04")

        assertNull(feature.issue.value)
        assertTrue(feature.canReview.value)
    }

    @Test
    fun aRefusedAmountReloadsTheMinimumAndTheIssueRowNamesIt() {
        market(litMarket.copy(minSizeBase = "2", minSizeQuote = "10"))
        val feature = open()
        feature.sendAction(PerpsCreateOrderAction.SetLimitPrice(BigDecimal("3.62")))
        scheduler.runCurrent()
        enter(feature, "2.04")

        assertNull(feature.issue.value)

        market(litMarket.copy(minSizeBase = "2", minSizeQuote = "20"))
        refuse(feature, Localization.perps_review_invalid_amount)

        assertNull(feature.refusal.value)
        assertEquals(PerpsOrderIssue.BelowMinMargin(BigDecimal("4.07"), canDeposit = false), feature.issue.value)
        assertFalse(feature.canReview.value)

        refuse(feature, Localization.perps_review_failed)

        assertEquals(Localization.perps_review_failed, feature.refusal.value)

        enter(feature, "20")
        refuse(feature, Localization.perps_review_invalid_amount)

        assertEquals(Localization.perps_review_invalid_amount, feature.refusal.value)
    }

    @Test
    fun toggleBackRoundsTheMarginDownToCents() {
        val feature = open()
        toggle(feature)
        enter(feature, "6.9")

        toggle(feature)

        assertFalse(feature.inputInSize.value)
        assertEquals("4.99", feature.amount.value)
        assertEquals(2, feature.amountFractionDigits.value)
    }

    @Test
    fun maxInAssetModeFillsTheLargestSizeTheBalanceCovers() {
        val feature = open()
        toggle(feature)

        max(feature)

        assertEquals("32.1", feature.amount.value)
        assertEquals(BigDecimal("23.260000"), feature.marginQuote.value)
        assertNull(feature.issue.value)
        assertTrue(feature.canReview.value)
    }

    @Test
    fun maxInAssetModeKeepsTheBudgetAndResizesOnPriceTicks() {
        val feature = open()
        toggle(feature)
        max(feature)

        tick("3.70")

        assertEquals("31.4", feature.amount.value)
        assertEquals(BigDecimal("23.260000"), feature.marginQuote.value)
        assertNull(feature.issue.value)
        assertTrue(feature.canReview.value)
    }

    @Test
    fun editingAfterMaxDropsTheBudget() {
        val feature = open()
        toggle(feature)
        max(feature)

        enter(feature, "10")
        tick("3.70")

        assertEquals("10", feature.amount.value)
        assertEquals(BigDecimal("7.400000"), feature.marginQuote.value)
    }

    @Test
    fun toggleCarriesTheMaxBudgetBothWays() {
        val feature = open()
        toggle(feature)
        max(feature)

        toggle(feature)
        assertFalse(feature.inputInSize.value)
        assertEquals("23.26", feature.amount.value)
        assertEquals(BigDecimal("23.26"), feature.marginQuote.value)

        tick("3.70")
        toggle(feature)
        assertTrue(feature.inputInSize.value)
        assertEquals("31.4", feature.amount.value)
        assertEquals(BigDecimal("23.260000"), feature.marginQuote.value)
    }

    @Test
    fun maxInAssetModeWithoutBalanceShowsNoIssue() {
        market(litMarket, collateral = "0")
        val feature = open()
        toggle(feature)

        max(feature)

        assertNull(feature.issue.value)
        assertFalse(feature.canReview.value)
    }

    @Test
    fun toggleWithoutPriceKeepsTheDollars() {
        market(litMarket.copy(markPrice = null))
        prices.value = emptyMap()
        val feature = open()
        enter(feature, "5")

        toggle(feature)

        assertFalse(feature.inputInSize.value)
        assertEquals("5", feature.amount.value)
    }

    @Test
    fun priceTicksDoNotReplanTheLiquidationDraft() {
        val feature = open()
        val stopLoss = PerpsAutoCloseDraft(takeProfit = null, stopLoss = BigDecimal("4"))
        feature.sendAction(PerpsCreateOrderAction.SetAutoClose(stopLoss))
        toggle(feature)
        enter(feature, "5")
        scheduler.advanceTimeBy(DRAFT_DEBOUNCE_MS + 1)
        scheduler.runCurrent()

        repeat(5) { index ->
            tick("3.6$index")
            scheduler.advanceTimeBy(TICK_MS)
            scheduler.runCurrent()
        }

        coVerify(exactly = 1) { perpetualRepository.signingCredentials(TestWallet.ID) }
        assertTrue(feature.canReview.value)
    }

    @Test
    fun takeProfitSetByPercentKeepsThePercentAsThePriceMoves() {
        val feature = open()
        val takeProfit = PerpsAutoCloseDraft(
            takeProfit = BigDecimal("3.54760"),
            stopLoss = null,
            takeProfitRoi = BigDecimal("10"),
        )
        feature.sendAction(PerpsCreateOrderAction.SetAutoClose(takeProfit))
        scheduler.runCurrent()

        tick("3.70")

        assertEquals(BigDecimal("3.62600"), feature.autoClose.value?.takeProfit)
        assertEquals(BigDecimal("10"), feature.autoClose.value?.takeProfitRoi)
    }

    @Test
    fun stopLossSetByPriceKeepsThePriceAsThePriceMoves() {
        val feature = open()
        val stopLoss = PerpsAutoCloseDraft(takeProfit = null, stopLoss = BigDecimal("4"))
        feature.sendAction(PerpsCreateOrderAction.SetAutoClose(stopLoss))
        scheduler.runCurrent()

        tick("3.70")

        assertEquals(stopLoss, feature.autoClose.value)
    }

    @Test
    fun leverageChangeKeepsTheTypedPercent() {
        val feature = open()
        val takeProfit = PerpsAutoCloseDraft(
            takeProfit = BigDecimal("3.54760"),
            stopLoss = null,
            takeProfitRoi = BigDecimal("10"),
        )
        feature.sendAction(PerpsCreateOrderAction.SetAutoClose(takeProfit))
        scheduler.runCurrent()

        feature.sendAction(PerpsCreateOrderAction.SetLeverage(10))
        scheduler.runCurrent()

        assertEquals(BigDecimal("3.58380"), feature.autoClose.value?.takeProfit)
        assertEquals(BigDecimal("10"), feature.autoClose.value?.takeProfitRoi)
        assertNull(feature.autoCloseWarning.value)
    }

    @Test
    fun leverageThatLeavesNoPriceForTheTypedPercentBlocksReviewUntilChangedBack() {
        val feature = open()
        enter(feature, "5")
        val takeProfit = PerpsAutoCloseDraft(
            takeProfit = BigDecimal("2.89600"),
            stopLoss = null,
            takeProfitRoi = BigDecimal("100"),
        )
        feature.sendAction(PerpsCreateOrderAction.SetAutoClose(takeProfit))
        scheduler.runCurrent()
        assertTrue(feature.canReview.value)

        feature.sendAction(PerpsCreateOrderAction.SetLeverage(1))
        scheduler.runCurrent()

        assertEquals(takeProfit, feature.autoClose.value)
        assertEquals(PerpsAutoCloseIssue(Localization.perps_review_auto_close_invalid), feature.autoCloseWarning.value)
        assertFalse(feature.canReview.value)

        feature.sendAction(PerpsCreateOrderAction.SetLeverage(LEVERAGE))
        scheduler.runCurrent()

        assertEquals(takeProfit, feature.autoClose.value)
        assertNull(feature.autoCloseWarning.value)
        assertTrue(feature.canReview.value)
    }

    private fun market(market: Market, collateral: String = "23.26") {
        val screen = TradingScreen(
            market = market,
            balance = Balance(collateral = collateral),
            flags = TradingFlags(openEnabled = true, autoCloseEnabled = true),
        )
        every { api.perps.screens.getTradingScreen(MARKET_INDEX, TestWallet.ID) } returns screen
        every { api.perps.screens.getTradingScreenWithHttpInfo(MARKET_INDEX, TestWallet.ID) } returns Success(screen)
    }

    private fun book(price: String) {
        val level = OrderBookLevel(price = price, propertySize = "1000")
        every { api.perps.orderbook.getTruncatedOrderBook(any(), any(), any(), any(), any()) } returns TruncatedOrderBook(
            updatedAt = OffsetDateTime.now(),
            truncated = false,
            bids = listOf(level),
            asks = listOf(level),
        )
    }

    private fun open(): PerpsCreateOrderFeature {
        val feature = PerpsCreateOrderFeature(
            marketIndex = MARKET_INDEX,
            symbol = SYMBOL,
            side = PerpsOrderSide.SHORT,
            adapter = adapter,
            perpetualRepository = perpetualRepository,
            livePrices = livePrices,
            unifiedAccountRepository = unifiedAccountRepository,
            settingsRepository = settingsRepository,
        )
        scheduler.runCurrent()
        feature.sendAction(PerpsCreateOrderAction.SetLeverage(LEVERAGE))
        scheduler.runCurrent()
        return feature
    }

    private fun enter(feature: PerpsCreateOrderFeature, amount: String) {
        feature.sendAction(PerpsCreateOrderAction.SetAmount(amount))
        scheduler.runCurrent()
    }

    private fun toggle(feature: PerpsCreateOrderFeature) {
        feature.sendAction(PerpsCreateOrderAction.ToggleInputMode)
        scheduler.runCurrent()
    }

    private fun max(feature: PerpsCreateOrderFeature) {
        feature.sendAction(PerpsCreateOrderAction.Max)
        scheduler.runCurrent()
    }

    private fun refuse(feature: PerpsCreateOrderFeature, text: Int) {
        feature.sendAction(PerpsCreateOrderAction.ShowRefusal(text))
        scheduler.runCurrent()
    }

    private fun tick(price: String) {
        prices.value = mapOf(perpsTicker(SYMBOL) to BigDecimal(price))
        scheduler.runCurrent()
    }

    private companion object {
        const val MARKET_INDEX = 120
        const val SYMBOL = "LIT"
        const val LEVERAGE = 5
        const val DRAFT_DEBOUNCE_MS = 500L
        const val TICK_MS = 400L

        val litMarket = Market(
            marketIndex = MARKET_INDEX,
            symbol = SYMBOL,
            status = "active",
            priceDecimals = 5,
            sizeDecimals = 1,
            tickSize = "0.0001",
            stepSize = "0.1",
            minSizeBase = "0.1",
            minSizeQuote = "1",
            maxLeverage = 10,
            takerFeePct = "0.05",
            initialMarginFraction = 1000,
            maintenanceMarginFraction = 500,
            markPrice = "3.62",
        )
    }
}
