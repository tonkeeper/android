package com.tonapps.perps.screens.order

import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.mvi.graph.KResult
import com.tonapps.perps.data.PerpsLivePrices
import com.tonapps.perps.data.PerpsMarketRules
import com.tonapps.perps.data.PerpsOrderSide
import com.tonapps.perps.data.PerpsTradingFlags
import com.tonapps.perps.data.PerpsTradingState
import com.tonapps.perps.data.perpsTicker
import com.tonapps.perps.trade.PerpsPlannerAdapter
import com.tonapps.wallet.data.multichain.account.UnifiedAccountRepository
import com.tonapps.wallet.localization.Localization
import com.tonapps.wallet.testing.BaseUnitTest
import com.tonapps.wallet.testing.TestWallet
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal

class PerpsLimitPriceFeatureTest : BaseUnitTest() {

    private val adapter = mockk<PerpsPlannerAdapter>()
    private val livePrices = mockk<PerpsLivePrices>()
    private val unifiedAccountRepository = mockk<UnifiedAccountRepository>()

    private val scheduler get() = dispatchers.scheduler

    @Before
    fun setUp() {
        val wallet = mockk<WalletEntity> { every { id } returns TestWallet.ID }
        every { unifiedAccountRepository.selectedTonWalletFlow } returns flowOf(wallet)
        every { livePrices.observe(any()) } returns MutableStateFlow(mapOf(perpsTicker(SYMBOL) to BigDecimal("100")))
        coEvery { adapter.tradingState(MARKET_INDEX, TestWallet.ID, any()) } returns KResult.Ok(
            PerpsTradingState(
                flags = PerpsTradingFlags.ALL_ENABLED,
                limitOrders = emptyList(),
                availableBalance = null,
                rules = RULES,
            )
        )
    }

    @Test
    fun `a new long below its stop loss cannot be set`() {
        val feature = open(PerpsOrderSide.LONG, autoClose(takeProfit = "98.5", stopLoss = "90", removes = false))

        enter(feature, "90")

        assertEquals(
            PerpsLimitPriceWarning(text = Localization.perps_limit_price_below_stop_loss, price = "$90"),
            feature.warning.value,
        )
        assertFalse(feature.canSet.value)
    }

    @Test
    fun `a new long at or above its take profit cannot be set`() {
        val feature = open(PerpsOrderSide.LONG, autoClose(takeProfit = "98.5", stopLoss = "90", removes = false))

        enter(feature, "98.5")

        assertEquals(
            PerpsLimitPriceWarning(text = Localization.perps_limit_price_above_take_profit, price = "$98.5"),
            feature.warning.value,
        )
        assertFalse(feature.canSet.value)
    }

    @Test
    fun `a new long between its legs can be set`() {
        val feature = open(PerpsOrderSide.LONG, autoClose(takeProfit = "98.5", stopLoss = "90", removes = false))

        enter(feature, "95")

        assertNull(feature.warning.value)
        assertTrue(feature.canSet.value)
    }

    @Test
    fun `a new short above its stop loss cannot be set`() {
        val feature = open(PerpsOrderSide.SHORT, autoClose(takeProfit = "101", stopLoss = "110", removes = false))

        enter(feature, "111")

        assertEquals(
            PerpsLimitPriceWarning(text = Localization.perps_limit_price_above_stop_loss, price = "$110"),
            feature.warning.value,
        )
        assertFalse(feature.canSet.value)
    }

    @Test
    fun `a new short at its take profit cannot be set`() {
        val feature = open(PerpsOrderSide.SHORT, autoClose(takeProfit = "101", stopLoss = "110", removes = false))

        enter(feature, "101")

        assertEquals(
            PerpsLimitPriceWarning(text = Localization.perps_limit_price_below_take_profit, price = "$101"),
            feature.warning.value,
        )
        assertFalse(feature.canSet.value)
    }

    @Test
    fun `an edit past the stop loss warns that it is removed and can be set`() {
        val feature = open(PerpsOrderSide.LONG, autoClose(takeProfit = null, stopLoss = "90", removes = true))

        enter(feature, "89")

        val warning = feature.warning.value
        assertEquals(
            PerpsLimitPriceWarning(
                text = Localization.perps_limit_price_below_stop_loss,
                price = "$90",
                removal = Localization.perps_limit_price_stop_loss_removed,
            ),
            warning,
        )
        assertFalse(warning?.blocking ?: true)
        assertTrue(feature.canSet.value)
    }

    @Test
    fun `an edit past the take profit warns that it is removed`() {
        val feature = open(PerpsOrderSide.LONG, autoClose(takeProfit = "98.5", stopLoss = null, removes = true))

        enter(feature, "99")

        assertEquals(Localization.perps_limit_price_take_profit_removed, feature.warning.value?.removal)
        assertTrue(feature.canSet.value)
    }

    @Test
    fun `the current price check comes before the legs`() {
        val feature = open(PerpsOrderSide.LONG, autoClose(takeProfit = "98.5", stopLoss = null, removes = true))

        enter(feature, "101")

        assertEquals(PerpsLimitPriceWarning(text = Localization.perps_limit_price_warning_below), feature.warning.value)
        assertFalse(feature.canSet.value)
    }

    @Test
    fun `without legs only the current price is checked`() {
        val feature = open(PerpsOrderSide.LONG, autoClose = null)

        enter(feature, "50")

        assertNull(feature.warning.value)
        assertTrue(feature.canSet.value)
    }

    private fun open(side: PerpsOrderSide, autoClose: PerpsLimitAutoClose?): PerpsLimitPriceFeature {
        val feature = PerpsLimitPriceFeature(
            marketIndex = MARKET_INDEX,
            symbol = SYMBOL,
            side = side,
            initialPrice = null,
            autoClose = autoClose,
            adapter = adapter,
            livePrices = livePrices,
            unifiedAccountRepository = unifiedAccountRepository,
        )
        scheduler.runCurrent()
        return feature
    }

    private fun enter(feature: PerpsLimitPriceFeature, price: String) {
        feature.sendAction(PerpsLimitPriceAction.SetInput(price))
        scheduler.runCurrent()
    }

    private fun autoClose(takeProfit: String?, stopLoss: String?, removes: Boolean) = PerpsLimitAutoClose(
        takeProfit = takeProfit?.toBigDecimal(),
        stopLoss = stopLoss?.toBigDecimal(),
        removesPassedLeg = removes,
    )

    private companion object {
        const val MARKET_INDEX = 2
        const val SYMBOL = "SOL"
        val RULES = PerpsMarketRules(
            maxLeverage = 20,
            priceDecimals = 2,
            sizeDecimals = 3,
            tickSize = BigDecimal("0.01"),
            stepSize = BigDecimal("0.001"),
            minSizeBase = null,
            minSizeQuote = null,
            takerFeePct = null,
            initialMarginBps = null,
            maintenanceMarginBps = null,
            markPrice = BigDecimal("100"),
            tradable = true,
        )
    }
}
