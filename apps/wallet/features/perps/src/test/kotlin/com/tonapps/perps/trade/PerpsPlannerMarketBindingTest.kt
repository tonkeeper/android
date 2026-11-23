package com.tonapps.perps.trade

import com.tonapps.chainkit.perpetual.PerpsPositionState
import com.tonapps.chainkit.perpetual.PerpsTradeError
import com.tonapps.mvi.graph.KResult
import com.tonapps.perps.data.PerpsAutoCloseDraft
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
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.OffsetDateTime

class PerpsPlannerMarketBindingTest : BaseUnitTest() {

    private val api = mockk<API>()
    private val adapter = PerpsPlannerAdapter(
        repository = PerpsRepository(api, mockk(), mockk(), mockk(relaxed = true)),
        accountService = mockk(),
        clientOrderIndex = mockk(),
        mediator = mockk(),
        securityConfig = PerpsSignedMarkets.listing(market),
    )

    @Test
    fun `a position and orders of the requested market are taken`() = runTest {
        screen(position = position(), orders = listOf(order(ORDER_INDEX), takeProfit(TAKE_PROFIT_INDEX)))

        val state = (adapter.reviewMarketState(WALLET_ID, MARKET_INDEX) as KResult.Ok).value

        assertTrue(state.context.position is PerpsPositionState.Present)
        assertEquals(listOf(ORDER_INDEX, TAKE_PROFIT_INDEX), state.context.restingOrders.map { it.orderIndex })
    }

    @Test
    fun `a position served for another market is refused`() = runTest {
        screen(position = position(marketIndex = OTHER_MARKET_INDEX))

        assertRefused()
    }

    @Test
    fun `a position without a market is refused`() = runTest {
        screen(position = position(marketIndex = null))

        assertRefused()
    }

    @Test
    fun `an order served for another market is refused`() = runTest {
        screen(orders = listOf(order(ORDER_INDEX), order(OTHER_ORDER_INDEX, marketIndex = OTHER_MARKET_INDEX)))

        assertRefused()
    }

    @Test
    fun `an order without a market is refused`() = runTest {
        screen(orders = listOf(order(ORDER_INDEX, marketIndex = null)))

        assertRefused()
    }

    @Test
    fun `closing a position served for another market is refused before anything is signed`() = runTest {
        screen(position = position(marketIndex = OTHER_MARKET_INDEX))

        assertPlanRefused(PerpsTradeRequest.Close(WALLET_ID, MARKET_INDEX))
    }

    @Test
    fun `cancelling while an order of another market is served is refused before anything is signed`() = runTest {
        screen(orders = listOf(order(OTHER_ORDER_INDEX, marketIndex = OTHER_MARKET_INDEX)))

        assertPlanRefused(PerpsTradeRequest.OrderCancel(WALLET_ID, MARKET_INDEX, OTHER_ORDER_INDEX))
    }

    @Test
    fun `a background auto-close renewal with a leg of another market is refused before anything is signed`() = runTest {
        screen(position = position(), orders = listOf(takeProfit(TAKE_PROFIT_INDEX, marketIndex = OTHER_MARKET_INDEX)))

        assertPlanRefused(PerpsTradeRequest.AutoCloseRenewal(WALLET_ID, MARKET_INDEX, mapOf(TAKE_PROFIT_INDEX to 1L)))
    }

    @Test
    fun `re-pricing the auto-close from the entry of a position of another market is refused before anything is signed`() = runTest {
        screen(position = position(marketIndex = OTHER_MARKET_INDEX), orders = listOf(takeProfit(TAKE_PROFIT_INDEX)))

        assertPlanRefused(
            PerpsTradeRequest.AutoCloseFromEntry(
                walletId = WALLET_ID,
                marketIndex = MARKET_INDEX,
                leverage = 5,
                placed = PerpsAutoCloseDraft(takeProfit = BigDecimal("4"), stopLoss = null),
            ),
        )
    }

    @Test
    fun `a position of another market is not due for an auto-close renewal`() = runTest {
        screen(position = position(marketIndex = OTHER_MARKET_INDEX), orders = listOf(takeProfit(TAKE_PROFIT_INDEX)))

        assertEquals(false, renewalDue())
    }

    @Test
    fun `a leg of another market is not due for an auto-close renewal`() = runTest {
        screen(position = position(), orders = listOf(takeProfit(TAKE_PROFIT_INDEX, marketIndex = OTHER_MARKET_INDEX)))

        assertEquals(false, renewalDue())
    }

    @Test
    fun `an expiring leg of the requested market is due for an auto-close renewal`() = runTest {
        screen(position = position(), orders = listOf(takeProfit(TAKE_PROFIT_INDEX)))

        assertEquals(true, renewalDue())
    }

    private suspend fun renewalDue(): Boolean {
        val due = adapter.autoCloseRenewalDue(
            walletId = WALLET_ID,
            marketIndex = MARKET_INDEX,
            credentials = mockk(relaxed = true),
            nowMs = NOW_MS,
            confirmed = mapOf(TAKE_PROFIT_CLIENT_INDEX to TAKE_PROFIT_TRIGGER),
        )
        return (due as KResult.Ok).value
    }

    private suspend fun assertRefused() {
        val failure = (adapter.reviewMarketState(WALLET_ID, MARKET_INDEX) as KResult.Err).value as PerpsPlanFailure.Planner

        assertEquals(PerpsTradeError.InvalidRules, failure.kind)
        assertEquals(MARKET_BINDING_FIELD, failure.field)
    }

    private suspend fun assertPlanRefused(request: PerpsTradeRequest) {
        val planned = adapter.plan(
            request = request,
            credentials = mockk(relaxed = true),
            mark = null,
            operationId = OPERATION_ID,
        )

        val failure = (planned as KResult.Err).value as PerpsPlanFailure.Planner
        assertEquals(PerpsTradeError.InvalidRules, failure.kind)
        assertEquals(MARKET_BINDING_FIELD, failure.field)
    }

    private fun screen(position: Position? = null, orders: List<Order> = emptyList()) {
        val screen = TradingScreen(market = market, position = position, openOrders = orders)
        every { api.perps.screens.getTradingScreenWithHttpInfo(MARKET_INDEX, WALLET_ID) } returns Success(screen)
        every { api.perps.integrator.getIntegrator(xWalletId = WALLET_ID) } returns integrator
        every { api.perps.positions.getOpenPosition(any(), WALLET_ID) } throws
            ClientException("Client error : 404", HTTP_NOT_FOUND)
    }

    private fun position(marketIndex: Int? = MARKET_INDEX): Position {
        return Position(
            marketIndex = marketIndex,
            side = PositionSide.long,
            propertySize = "12.5",
            avgEntryPrice = "3.62",
            allocatedMargin = "9.05",
            marginMode = MarginMode.isolated,
            leverage = 5,
        )
    }

    private fun order(orderIndex: Long, marketIndex: Int? = MARKET_INDEX): Order {
        return Order(
            orderIndex = orderIndex,
            marketIndex = marketIndex,
            side = OrderSide.long,
            type = OrderType.limit,
            status = OrderStatus.`open`,
            category = OrderCategory.`open`,
            baseSize = "1.0",
            price = "3.5",
        )
    }

    private fun takeProfit(orderIndex: Long, marketIndex: Int? = MARKET_INDEX): Order {
        return Order(
            orderIndex = orderIndex,
            clientOrderIndex = TAKE_PROFIT_CLIENT_INDEX,
            marketIndex = marketIndex,
            side = OrderSide.short,
            type = OrderType.limit,
            status = OrderStatus.`open`,
            category = OrderCategory.take_profit,
            baseSize = "12.5",
            triggerPrice = "4.00000",
            expiresAt = OffsetDateTime.parse("2026-11-12T20:00:00Z"),
        )
    }

    private companion object {
        const val WALLET_ID = "wallet"
        const val OPERATION_ID = "operation"
        const val MARKET_INDEX = 120
        const val OTHER_MARKET_INDEX = 2
        const val ORDER_INDEX = 1_001L
        const val OTHER_ORDER_INDEX = 2_002L
        const val TAKE_PROFIT_INDEX = 3_003L
        const val TAKE_PROFIT_CLIENT_INDEX = 77L
        const val TAKE_PROFIT_TRIGGER = 400_000L
        const val HTTP_NOT_FOUND = 404
        val NOW_MS: Long = OffsetDateTime.parse("2026-11-12T19:00:00Z").toInstant().toEpochMilli()

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
