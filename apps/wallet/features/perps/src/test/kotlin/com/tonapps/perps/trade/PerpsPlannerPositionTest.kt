package com.tonapps.perps.trade

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
import io.perpsapi.models.TradingFlags
import io.perpsapi.models.TradingScreen
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PerpsPlannerPositionTest : BaseUnitTest() {

    private val api = mockk<API>()
    private val adapter = PerpsPlannerAdapter(
        repository = PerpsRepository(api, mockk(), mockk(), mockk(relaxed = true)),
        accountService = mockk(),
        clientOrderIndex = mockk(),
        mediator = mockk(),
        securityConfig = PerpsSignedMarkets.listing(market),
    )

    @Test
    fun `a position with a size is present with that base amount`() = runTest {
        screen(position = position(size = "12.5"))

        val state = (adapter.reviewMarketState(WALLET_ID, MARKET_INDEX) as KResult.Ok).value

        assertEquals(SIZE_12_5_BASE_UNITS, (state.context.position as PerpsPositionState.Present).baseAmount)
    }

    @Test
    fun `a position of size zero is flat`() = runTest {
        screen(position = position(size = "0"))

        val state = (adapter.reviewMarketState(WALLET_ID, MARKET_INDEX) as KResult.Ok).value

        assertEquals(PerpsPositionState.KnownFlat, state.context.position)
    }

    @Test
    fun `no position is flat`() = runTest {
        screen(position = null)

        val state = (adapter.reviewMarketState(WALLET_ID, MARKET_INDEX) as KResult.Ok).value

        assertEquals(PerpsPositionState.KnownFlat, state.context.position)
    }

    @Test
    fun `a position without a size is refused`() = runTest {
        screen(position = position(size = null))

        assertRefused()
    }

    @Test
    fun `a position with a blank size is refused`() = runTest {
        screen(position = position(size = ""))

        assertRefused()
    }

    @Test
    fun `a position with an unparseable size is refused`() = runTest {
        screen(position = position(size = "abc"))

        assertRefused()
    }

    @Test
    fun `a position with a negative size is refused`() = runTest {
        screen(position = position(size = "-1"))

        assertRefused()
    }

    @Test
    fun `a positive size below one size step is refused`() = runTest {
        screen(position = position(size = "0.01"))

        assertRefused()
    }

    @Test
    fun `an unknown side with a blank size is refused`() = runTest {
        screen(position = position(size = "", side = PositionSide.unknown))

        assertRefused()
    }

    @Test
    fun `missing flags deny open`() = runTest {
        screen(flags = null)

        val state = (adapter.reviewMarketState(WALLET_ID, MARKET_INDEX) as KResult.Ok).value

        assertFalse(state.context.openEnabled)
    }

    @Test
    fun `flags without open_enabled deny open`() = runTest {
        screen(flags = TradingFlags(openEnabled = null))

        val state = (adapter.reviewMarketState(WALLET_ID, MARKET_INDEX) as KResult.Ok).value

        assertFalse(state.context.openEnabled)
    }

    @Test
    fun `open_enabled true allows open`() = runTest {
        screen(flags = TradingFlags(openEnabled = true))

        val state = (adapter.reviewMarketState(WALLET_ID, MARKET_INDEX) as KResult.Ok).value

        assertTrue(state.context.openEnabled)
    }

    @Test
    fun `open_enabled false denies open`() = runTest {
        screen(flags = TradingFlags(openEnabled = false))

        val state = (adapter.reviewMarketState(WALLET_ID, MARKET_INDEX) as KResult.Ok).value

        assertFalse(state.context.openEnabled)
    }

    private suspend fun assertRefused() {
        val failure = (adapter.reviewMarketState(WALLET_ID, MARKET_INDEX) as KResult.Err).value

        assertEquals(PerpsTradeError.InvalidRules, (failure as PerpsPlanFailure.Planner).kind)
    }

    private fun screen(position: Position? = null, flags: TradingFlags? = null) {
        val screen = TradingScreen(market = market, position = position, openOrders = emptyList(), flags = flags)
        every { api.perps.screens.getTradingScreenWithHttpInfo(MARKET_INDEX, WALLET_ID) } returns Success(screen)
        every { api.perps.integrator.getIntegrator(xWalletId = WALLET_ID) } returns integrator
        every { api.perps.positions.getOpenPosition(any(), WALLET_ID) } throws
            ClientException("Client error : 404", HTTP_NOT_FOUND)
    }

    private fun position(size: String?, side: PositionSide = PositionSide.long): Position {
        return Position(
            marketIndex = MARKET_INDEX,
            side = side,
            propertySize = size,
            avgEntryPrice = "3.62",
            allocatedMargin = "9.05",
            marginMode = MarginMode.isolated,
            leverage = 5,
        )
    }

    private companion object {
        const val WALLET_ID = "wallet"
        const val MARKET_INDEX = 120
        const val HTTP_NOT_FOUND = 404
        const val SIZE_12_5_BASE_UNITS = 125L

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
