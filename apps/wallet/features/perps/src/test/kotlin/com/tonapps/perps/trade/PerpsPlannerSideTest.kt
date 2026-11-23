package com.tonapps.perps.trade

import com.tonapps.chainkit.perpetual.PerpsPositionState
import com.tonapps.chainkit.perpetual.PerpsSide
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
import io.perpsapi.models.Order
import io.perpsapi.models.OrderCategory
import io.perpsapi.models.OrderSide
import io.perpsapi.models.OrderStatus
import io.perpsapi.models.OrderType
import io.perpsapi.models.Position
import io.perpsapi.models.PositionSide
import io.perpsapi.models.TradingScreen
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class PerpsPlannerSideTest : BaseUnitTest() {

    private val api = mockk<API>()
    private val adapter = PerpsPlannerAdapter(
        repository = PerpsRepository(api, mockk(), mockk(), mockk(relaxed = true)),
        accountService = mockk(),
        clientOrderIndex = mockk(),
        mediator = mockk(),
        securityConfig = PerpsSignedMarkets.listing(market),
    )

    @Test
    fun `a short position stays short`() = runTest {
        screen(position = position(PositionSide.short))

        val state = (adapter.reviewMarketState(WALLET_ID, MARKET_INDEX) as KResult.Ok).value

        assertEquals(PerpsSide.Short, (state.context.position as PerpsPositionState.Present).side)
    }

    @Test
    fun `a long position stays long`() = runTest {
        screen(position = position(PositionSide.long))

        val state = (adapter.reviewMarketState(WALLET_ID, MARKET_INDEX) as KResult.Ok).value

        assertEquals(PerpsSide.Long, (state.context.position as PerpsPositionState.Present).side)
    }

    @Test
    fun `an unknown position side is refused`() = runTest {
        screen(position = position(PositionSide.unknown))

        assertRefused()
    }

    @Test
    fun `a position without a side is refused`() = runTest {
        screen(position = position(null))

        assertRefused()
    }

    @Test
    fun `an order side is kept as sent`() = runTest {
        screen(orders = listOf(order(OrderSide.short)))

        val state = (adapter.reviewMarketState(WALLET_ID, MARKET_INDEX) as KResult.Ok).value

        assertEquals(PerpsSide.Short, state.context.restingOrders.single().side)
    }

    @Test
    fun `an unknown order side is refused`() = runTest {
        screen(orders = listOf(order(OrderSide.short), order(OrderSide.unknown)))

        assertRefused()
    }

    @Test
    fun `an order without a side is refused`() = runTest {
        screen(orders = listOf(order(null)))

        assertRefused()
    }

    private suspend fun assertRefused() {
        val failure = (adapter.reviewMarketState(WALLET_ID, MARKET_INDEX) as KResult.Err).value

        assertEquals(PerpsTradeError.InvalidRules, (failure as PerpsPlanFailure.Planner).kind)
    }

    private fun screen(position: Position? = null, orders: List<Order> = emptyList()) {
        val screen = TradingScreen(market = market, position = position, openOrders = orders)
        every { api.perps.screens.getTradingScreenWithHttpInfo(MARKET_INDEX, WALLET_ID) } returns Success(screen)
        every { api.perps.integrator.getIntegrator(xWalletId = WALLET_ID) } returns integrator
        every { api.perps.positions.getOpenPosition(any(), WALLET_ID) } throws
            ClientException("Client error : 404", HTTP_NOT_FOUND)
    }

    private fun position(side: PositionSide?): Position {
        return Position(
            marketIndex = MARKET_INDEX,
            side = side,
            propertySize = "12.5",
            avgEntryPrice = "3.62",
            allocatedMargin = "9.05",
            marginMode = MarginMode.isolated,
            leverage = 5,
        )
    }

    private fun order(side: OrderSide?): Order {
        return Order(
            orderIndex = ORDER_INDEX + (side?.ordinal ?: -1),
            marketIndex = MARKET_INDEX,
            side = side,
            type = OrderType.limit,
            status = OrderStatus.`open`,
            category = OrderCategory.`open`,
            baseSize = "3.0",
            price = "3.5",
        )
    }

    private companion object {
        const val WALLET_ID = "wallet"
        const val MARKET_INDEX = 120
        const val ORDER_INDEX = 7_000L
        const val HTTP_NOT_FOUND = 404

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
