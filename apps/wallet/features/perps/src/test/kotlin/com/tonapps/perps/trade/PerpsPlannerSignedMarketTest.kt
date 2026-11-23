package com.tonapps.perps.trade

import com.tonapps.chainkit.perpetual.PerpsMarketRules
import com.tonapps.chainkit.perpetual.PerpsTradeError
import com.tonapps.mvi.graph.KResult
import com.tonapps.perps.data.PerpsError
import com.tonapps.perps.data.PerpsOrderSide
import com.tonapps.perps.data.PerpsRepository
import com.tonapps.perps.security.PerpsSecurityBlocks
import com.tonapps.perps.security.PerpsSecurityConfigRepository
import com.tonapps.perps.security.PerpsSecurityConfigStore
import com.tonapps.wallet.api.API
import com.tonapps.wallet.testing.BaseUnitTest
import io.infrastructure.Success
import io.mockk.every
import io.mockk.mockk
import io.perpsapi.models.Integrator
import io.perpsapi.models.Market
import io.perpsapi.models.TradingScreen
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

class PerpsPlannerSignedMarketTest : BaseUnitTest() {

    private val api = mockk<API>()

    @Test
    fun `a market served as signed is planned on the signed scale and limits`() = runTest {
        val rules = rules(PerpsSignedMarkets.listing(market))

        assertEquals(5, rules.scale.priceDecimals)
        assertEquals(1, rules.scale.baseDecimals)
        assertEquals(6, rules.scale.quoteDecimals)
        assertEquals(1L, rules.minBaseAmount)
        assertEquals(1_000_000L, rules.minNotionalQuote)
        assertEquals(1_000, rules.minInitialMarginBps)
    }

    @Test
    fun `the quote scale is the signed one, not the price and size scales added up`() = runTest {
        val rules = rules(signed(quoteDecimals = 7))

        assertEquals(7, rules.scale.quoteDecimals)
        assertEquals(10_000_000L, rules.minNotionalQuote)
    }

    @Test
    fun `the leverage ceiling is the signed one`() = runTest {
        val rules = rules(signed(maxLeverage = 25), market.copy(maxLeverage = 25, initialMarginFraction = 666))

        assertEquals(400, rules.minInitialMarginBps)
    }

    @Test
    fun `minimums written with other trailing zeros are the same minimums`() = runTest {
        val rules = rules(signed(minBaseAmount = "0.10", minQuoteAmount = "1.000000"))

        assertEquals(1L, rules.minBaseAmount)
    }

    @Test
    fun `a price scale one digit larger than signed is refused`() = runTest {
        assertRefused(PerpsTradeError.InvalidRules, market.copy(priceDecimals = 6, tickSize = "0.00001"))
    }

    @Test
    fun `a size scale one digit larger than signed is refused`() = runTest {
        assertRefused(PerpsTradeError.InvalidRules, market.copy(sizeDecimals = 2, stepSize = "0.01"))
    }

    @Test
    fun `a price scale one less and a size scale one more with the same sum are refused`() = runTest {
        assertRefused(PerpsTradeError.InvalidRules, market.copy(priceDecimals = 4, sizeDecimals = 2))
    }

    @Test
    fun `a minimum size other than the signed one is refused`() = runTest {
        assertRefused(PerpsTradeError.InvalidRules, market.copy(minSizeBase = "0.01"))
        assertRefused(PerpsTradeError.InvalidRules, market.copy(minSizeBase = null))
    }

    @Test
    fun `a minimum notional other than the signed one is refused`() = runTest {
        assertRefused(PerpsTradeError.InvalidRules, market.copy(minSizeQuote = "0.1"))
        assertRefused(PerpsTradeError.InvalidRules, market.copy(minSizeQuote = ""))
    }

    @Test
    fun `a leverage ceiling other than the signed one is refused`() = runTest {
        assertRefused(PerpsTradeError.InvalidRules, market.copy(maxLeverage = 20))
        assertRefused(PerpsTradeError.InvalidRules, market.copy(maxLeverage = 5))
        assertRefused(PerpsTradeError.InvalidRules, market.copy(maxLeverage = null, initialMarginFraction = 1000))
    }

    @Test
    fun `a market served for another index than the requested one is refused`() = runTest {
        val other = market.copy(marketIndex = MARKET_INDEX + 1)

        assertRefused(PerpsTradeError.InvalidRules, other, PerpsSignedMarkets.listing(market, other))
    }

    @Test
    fun `a market served under another symbol than the signed one is refused`() = runTest {
        assertRefused(PerpsTradeError.InvalidRules, market.copy(symbol = "SOL"))
        assertRefused(PerpsTradeError.InvalidRules, market.copy(symbol = "lit"))
        assertRefused(PerpsTradeError.InvalidRules, market.copy(symbol = null))
    }

    @Test
    fun `a market number a signed order cannot carry is refused even when served and listed`() = runTest {
        val outOfRange = Short.MAX_VALUE + 1
        val served = market.copy(marketIndex = outOfRange)
        every { api.perps.screens.getTradingScreenWithHttpInfo(outOfRange, WALLET_ID) } returns
            Success(TradingScreen(market = served, openOrders = emptyList()))
        every { api.perps.integrator.getIntegrator(xWalletId = WALLET_ID) } returns integrator

        val failure = (adapter(PerpsSignedMarkets.listing(served)).reviewMarketState(WALLET_ID, outOfRange) as KResult.Err).value

        assertEquals(PerpsTradeError.UnsupportedMarket, (failure as PerpsPlanFailure.Planner).kind)
    }

    @Test
    fun `a refused market is not due for an auto-close renewal`() = runTest {
        screen(market.copy(sizeDecimals = 2, stepSize = "0.01"))

        val due = adapter(PerpsSignedMarkets.listing(market)).autoCloseRenewalDue(
            walletId = WALLET_ID,
            marketIndex = MARKET_INDEX,
            credentials = mockk(relaxed = true),
            nowMs = 0L,
            confirmed = emptyMap(),
        )

        assertEquals(false, (due as KResult.Ok).value)
    }

    @Test
    fun `no verified config yet leaves the auto-close renewal to retry`() = runTest {
        screen(market)

        val due = adapter(unverified()).autoCloseRenewalDue(
            walletId = WALLET_ID,
            marketIndex = MARKET_INDEX,
            credentials = mockk(relaxed = true),
            nowMs = 0L,
            confirmed = emptyMap(),
        )

        assertEquals(PerpsError.Unknown, (due as KResult.Err).value)
    }

    @Test
    fun `a market the signed config does not list is refused`() = runTest {
        assertRefused(
            PerpsTradeError.UnsupportedMarket,
            market,
            PerpsSignedMarkets.repository(listOf(PerpsSecurityBlocks.market(marketId = MARKET_INDEX + 1))),
        )
    }

    @Test
    fun `a signed config without markets refuses every market`() = runTest {
        assertRefused(PerpsTradeError.UnsupportedMarket, market, PerpsSignedMarkets.repository(markets = null))
    }

    @Test
    fun `no verified config refuses every market`() = runTest {
        assertRefused(PerpsTradeError.UnsupportedMarket, market, unverified())
    }

    @Test
    fun `a market the signed config allows no leverage on is refused`() = runTest {
        assertRefused(PerpsTradeError.UnsupportedMarket, market.copy(maxLeverage = 0), signed(maxLeverage = 0))
    }

    @Test
    fun `an order on a shifted scale is refused before anything is signed`() = runTest {
        screen(market.copy(priceDecimals = 4, sizeDecimals = 2))
        val request = PerpsTradeRequest.Open(
            walletId = WALLET_ID,
            marketIndex = MARKET_INDEX,
            side = PerpsOrderSide.LONG,
            marginQuote = BigDecimal.TEN,
            leverage = 5,
            autoClose = null,
            limitPrice = null,
        )

        val planned = adapter(PerpsSignedMarkets.listing(market)).plan(
            request = request,
            credentials = mockk(relaxed = true),
            mark = null,
            operationId = OPERATION_ID,
        )

        val failure = (planned as KResult.Err).value

        assertEquals(PerpsTradeError.InvalidRules, (failure as PerpsPlanFailure.Planner).kind)
    }

    private suspend fun rules(
        config: PerpsSecurityConfigRepository,
        served: Market = market,
    ): PerpsMarketRules {
        screen(served)
        return (adapter(config).reviewMarketState(WALLET_ID, MARKET_INDEX) as KResult.Ok).value.rules
    }

    private suspend fun assertRefused(
        kind: PerpsTradeError,
        served: Market,
        config: PerpsSecurityConfigRepository = PerpsSignedMarkets.listing(market),
    ) {
        screen(served)

        val failure = (adapter(config).reviewMarketState(WALLET_ID, MARKET_INDEX) as KResult.Err).value

        assertEquals(kind, (failure as PerpsPlanFailure.Planner).kind)
    }

    private fun signed(
        quoteDecimals: Int = 6,
        minBaseAmount: String = "0.1",
        minQuoteAmount: String = "1",
        maxLeverage: Int = 10,
    ): PerpsSecurityConfigRepository {
        val entry: JsonObject = PerpsSecurityBlocks.market(
            marketId = MARKET_INDEX,
            quoteDecimals = quoteDecimals,
            minBaseAmount = minBaseAmount,
            minQuoteAmount = minQuoteAmount,
            maxLeverage = maxLeverage,
        )
        return PerpsSignedMarkets.repository(listOf(entry))
    }

    private fun unverified(): PerpsSecurityConfigRepository {
        return PerpsSecurityConfigRepository(
            servedBlock = { PerpsSecurityBlocks.signed(PerpsSecurityBlocks.newKey()) },
            store = object : PerpsSecurityConfigStore {
                override fun keptBlock(): String? = null
                override fun keep(block: String) = Unit
            },
        )
    }

    private fun adapter(config: PerpsSecurityConfigRepository): PerpsPlannerAdapter {
        return PerpsPlannerAdapter(
            repository = PerpsRepository(api, mockk(), mockk(), mockk(relaxed = true)),
            accountService = mockk(),
            clientOrderIndex = mockk(),
            mediator = mockk(),
            securityConfig = config,
        )
    }

    private fun screen(served: Market) {
        val screen = TradingScreen(market = served, openOrders = emptyList())
        every { api.perps.screens.getTradingScreenWithHttpInfo(MARKET_INDEX, WALLET_ID) } returns Success(screen)
        every { api.perps.integrator.getIntegrator(xWalletId = WALLET_ID) } returns integrator
    }

    private companion object {
        const val WALLET_ID = "wallet"
        const val MARKET_INDEX = 120
        const val OPERATION_ID = "operation"

        val market = Market(
            marketIndex = MARKET_INDEX,
            symbol = "LIT",
            status = "active",
            priceDecimals = 5,
            sizeDecimals = 1,
            tickSize = "0.0001",
            stepSize = "0.1",
            minSizeBase = "0.1",
            minSizeQuote = "1",
            maxLeverage = 10,
            makerFeePct = "0",
            takerFeePct = "0.05",
            initialMarginFraction = 1000,
            maintenanceMarginFraction = 500,
            markPrice = "3.62",
        )

        val integrator = Integrator(
            integratorAccountIndex = 0,
            integratorTakerFee = 0,
            integratorMakerFee = 0,
            maxPerpsTakerFee = 0,
            maxPerpsMakerFee = 0,
            maxSpotTakerFee = 0,
            maxSpotMakerFee = 0,
            approvalTtlMs = 0,
        )
    }
}
