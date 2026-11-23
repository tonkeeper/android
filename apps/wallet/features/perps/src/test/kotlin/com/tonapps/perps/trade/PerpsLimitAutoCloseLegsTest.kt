package com.tonapps.perps.trade

import com.tonapps.chainkit.perpetual.PerpsOrderCategory
import com.tonapps.chainkit.perpetual.PerpsRestingOrderStatus
import com.tonapps.chainkit.perpetual.PerpsRestingOrderType
import com.tonapps.chainkit.perpetual.PerpsScale
import com.tonapps.chainkit.perpetual.PerpsSide
import io.perpsapi.models.Order
import io.perpsapi.models.OrderAutoClose
import io.perpsapi.models.OrderAutoCloseLeg
import io.perpsapi.models.OrderCategory
import io.perpsapi.models.OrderSide
import io.perpsapi.models.OrderStatus
import io.perpsapi.models.OrderType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class PerpsLimitAutoCloseLegsTest {

    @Test
    fun `a waiting leg rests as a pending order that is not the position's`() {
        val orders = listOf(limit(OrderSide.long, takeProfit = leg(TAKE_PROFIT_INDEX, "110.5"), stopLoss = leg(STOP_LOSS_INDEX, "90")))

        val resting = orders.toRestingOrders(SCALE)

        assertEquals(listOf(LIMIT_INDEX, TAKE_PROFIT_INDEX, STOP_LOSS_INDEX), resting.map { it.orderIndex })
        val takeProfit = resting.first { it.orderIndex == TAKE_PROFIT_INDEX }
        assertEquals(PerpsOrderCategory.TakeProfit, takeProfit.category)
        assertEquals(PerpsRestingOrderType.TakeProfit, takeProfit.orderType)
        assertEquals(PerpsSide.Short, takeProfit.side)
        assertEquals(PerpsRestingOrderStatus.Pending, takeProfit.status)
        assertEquals(11_050L, takeProfit.triggerPrice)
        assertEquals(VALID_TO * 1_000L, takeProfit.expiryUnixMs)
        assertFalse(takeProfit.positionTied)
        assertEquals(PerpsOrderCategory.StopLoss, resting.first { it.orderIndex == STOP_LOSS_INDEX }.category)
        assertNull(resting.restingLeg(PerpsOrderCategory.TakeProfit))
        assertNull(resting.restingLeg(PerpsOrderCategory.StopLoss))
    }

    @Test
    fun `a short limit's legs close long`() {
        val resting = listOf(limit(OrderSide.short, takeProfit = leg(TAKE_PROFIT_INDEX, "90"))).toRestingOrders(SCALE)

        assertEquals(PerpsSide.Long, resting.first { it.orderIndex == TAKE_PROFIT_INDEX }.side)
    }

    @Test
    fun `a waiting leg also listed as an order is kept once and not tied to the position`() {
        val listedLeg = Order(
            orderIndex = TAKE_PROFIT_INDEX,
            side = OrderSide.short,
            type = OrderType.market,
            status = OrderStatus.new,
            category = OrderCategory.take_profit,
            baseSize = "0",
            triggerPrice = "110.5",
        )
        val orders = listOf(limit(OrderSide.long, takeProfit = leg(TAKE_PROFIT_INDEX, "110.5")), listedLeg)

        val resting = orders.toRestingOrders(SCALE)

        assertEquals(listOf(LIMIT_INDEX, TAKE_PROFIT_INDEX), resting.map { it.orderIndex })
        assertFalse(resting.first { it.orderIndex == TAKE_PROFIT_INDEX }.positionTied)
    }

    @Test
    fun `a position's own leg stays tied to it`() {
        val positionLeg = Order(
            orderIndex = STOP_LOSS_INDEX,
            side = OrderSide.short,
            type = OrderType.market,
            status = OrderStatus.new,
            category = OrderCategory.stop_loss,
            baseSize = "1",
            triggerPrice = "90",
        )

        val resting = listOf(positionLeg).toRestingOrders(SCALE)

        assertEquals(STOP_LOSS_INDEX, resting.restingLeg(PerpsOrderCategory.StopLoss)?.orderIndex)
    }

    private fun limit(
        side: OrderSide,
        takeProfit: OrderAutoCloseLeg? = null,
        stopLoss: OrderAutoCloseLeg? = null,
    ) = Order(
        orderIndex = LIMIT_INDEX,
        side = side,
        type = OrderType.limit,
        status = OrderStatus.new,
        category = OrderCategory.`open`,
        baseSize = "1",
        price = "100",
        autoClose = OrderAutoClose(takeProfit = takeProfit, stopLoss = stopLoss),
    )

    private fun leg(index: Long, trigger: String) = OrderAutoCloseLeg(
        orderIndex = index,
        clientOrderIndex = 0,
        triggerPrice = trigger,
        price = trigger,
        validTo = VALID_TO,
    )

    private companion object {
        const val LIMIT_INDEX = 10L
        const val TAKE_PROFIT_INDEX = 11L
        const val STOP_LOSS_INDEX = 12L
        const val VALID_TO = 1_800_000_000L
        val SCALE = PerpsScale(priceDecimals = 2, baseDecimals = 4, quoteDecimals = 6)
    }
}
