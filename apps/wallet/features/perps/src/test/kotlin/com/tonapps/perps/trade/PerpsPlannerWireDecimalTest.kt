package com.tonapps.perps.trade

import com.tonapps.chainkit.perpetual.PerpsFeeKind
import com.tonapps.chainkit.perpetual.PerpsPositionState
import com.tonapps.chainkit.perpetual.PerpsTradeError
import com.tonapps.mvi.graph.KResult
import com.tonapps.perps.data.PerpsRepository
import com.tonapps.wallet.api.API
import com.tonapps.wallet.testing.BaseUnitTest
import io.infrastructure.ClientException
import io.infrastructure.Success
import io.mockk.every
import io.mockk.mockk
import io.perpsapi.models.Integrator
import io.perpsapi.models.MarginMode
import io.perpsapi.models.Market
import io.perpsapi.models.Position
import io.perpsapi.models.PositionSide
import io.perpsapi.models.TradingScreen
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PerpsPlannerWireDecimalTest : BaseUnitTest() {

    private val api = mockk<API>()
    private val adapter = PerpsPlannerAdapter(
        repository = PerpsRepository(api, mockk(), mockk(), mockk(relaxed = true)),
        accountService = mockk(),
        clientOrderIndex = mockk(),
        mediator = mockk(),
        securityConfig = PerpsSignedMarkets.listing(market),
    )

    @Test(timeout = TIMEOUT_MS)
    fun `a tick size with a huge exponent is refused`() = runTest {
        screen(market.copy(tickSize = HUGE))

        assertRefused()
    }

    @Test(timeout = TIMEOUT_MS)
    fun `a step size with a tiny exponent is refused`() = runTest {
        screen(market.copy(stepSize = TINY))

        assertRefused()
    }

    @Test(timeout = TIMEOUT_MS)
    fun `a fee with a huge exponent is refused`() = runTest {
        screen(market.copy(takerFeePct = HUGE))

        assertRefused()
    }

    @Test(timeout = TIMEOUT_MS)
    fun `an over-long fee is refused`() = runTest {
        screen(market.copy(makerFeePct = "0." + "0".repeat(70) + "1"))

        assertRefused()
    }

    @Test(timeout = TIMEOUT_MS)
    fun `a size bound with a huge exponent is dropped`() = runTest {
        screen(market.copy(maxSizeBase = HUGE, maxSizeQuote = HUGE))

        val rules = (adapter.reviewMarketState(WALLET_ID, MARKET_INDEX) as KResult.Ok).value.rules

        assertNull(rules.maxBaseAmount)
        assertNull(rules.maxNotionalQuote)
    }

    @Test(timeout = TIMEOUT_MS)
    fun `a position margin with a huge exponent is refused`() = runTest {
        screen(market, position.copy(allocatedMargin = HUGE))

        assertRefused()
    }

    @Test(timeout = TIMEOUT_MS)
    fun `a liquidation price with a huge exponent is dropped`() = runTest {
        screen(market, position.copy(liquidationPrice = HUGE))

        val state = (adapter.reviewMarketState(WALLET_ID, MARKET_INDEX) as KResult.Ok).value

        assertNull((state.context.position as PerpsPositionState.Present).liquidationPrice)
    }

    @Test(timeout = TIMEOUT_MS)
    fun `the market fee stands while the account tier is unknown`() = runTest {
        screen(market)

        val rules = (adapter.reviewMarketState(WALLET_ID, MARKET_INDEX) as KResult.Ok).value.rules

        assertEquals(500L, rules.takerFeePpm)
        assertEquals(0L, rules.makerFeePpm)
    }

    @Test(timeout = TIMEOUT_MS)
    fun `the account tier fee is only shown, never priced`() = runTest {
        val plus = integrator.copy(venueTakerFee = 50, venueMakerFee = 20)
        screen(market, integrator = plus)

        val rules = (adapter.reviewMarketState(WALLET_ID, MARKET_INDEX) as KResult.Ok).value.rules

        assertEquals(500L, rules.takerFeePpm)
        assertEquals(0L, rules.makerFeePpm)
        assertEquals(50L, displayedTakerFeePpm(PerpsFeeKind.Open, rules, plus))
        assertEquals(500L, displayedTakerFeePpm(PerpsFeeKind.Close, rules, integrator))
    }

    private suspend fun assertRefused() {
        val failure = (adapter.reviewMarketState(WALLET_ID, MARKET_INDEX) as KResult.Err).value

        assertEquals(PerpsTradeError.InvalidRules, (failure as PerpsPlanFailure.Planner).kind)
    }

    private fun screen(market: Market, position: Position? = null, integrator: Integrator = Companion.integrator) {
        val screen = TradingScreen(market = market, position = position, openOrders = emptyList())
        every { api.perps.screens.getTradingScreenWithHttpInfo(MARKET_INDEX, WALLET_ID) } returns Success(screen)
        every { api.perps.integrator.getIntegrator(xWalletId = WALLET_ID) } returns integrator
        every { api.perps.positions.getOpenPosition(any(), WALLET_ID) } throws
            ClientException("Client error : 404", HTTP_NOT_FOUND)
    }

    private companion object {
        const val WALLET_ID = "wallet"
        const val MARKET_INDEX = 120
        const val HTTP_NOT_FOUND = 404
        const val TIMEOUT_MS = 10_000L
        const val HUGE = "1e999999999"
        const val TINY = "1e-999999999"

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

        val position = Position(
            marketIndex = MARKET_INDEX,
            side = PositionSide.long,
            propertySize = "12.5",
            avgEntryPrice = "3.62",
            allocatedMargin = "9.05",
            marginMode = MarginMode.isolated,
            leverage = 5,
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
