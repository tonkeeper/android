package com.tonapps.perps.screens.details

import com.tonapps.chainkit.perpetual.PerpetualAccountSnapshot
import com.tonapps.chainkit.perpetual.PerpsMarketRules
import com.tonapps.chainkit.perpetual.PerpsOrderCategory
import com.tonapps.chainkit.perpetual.PerpsRestingOrder
import com.tonapps.chainkit.perpetual.PerpsRestingOrderStatus
import com.tonapps.chainkit.perpetual.PerpsRestingOrderType
import com.tonapps.chainkit.perpetual.PerpsScale
import com.tonapps.chainkit.perpetual.PerpsScope
import com.tonapps.chainkit.perpetual.PerpsSide
import com.tonapps.chainkit.perpetual.PerpsTradeIntent
import com.tonapps.perps.data.PerpsLimitOrder
import com.tonapps.perps.data.PerpsLimitOrderLeg
import com.tonapps.perps.data.PerpsPositionSide
import com.tonapps.perps.trade.PerpsPlannedTrade
import com.tonapps.perps.trade.PerpsProductRules
import com.tonapps.perps.trade.PerpsTradeRequest
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class PerpsLimitEditReviewTest {

    @Test
    fun `the size and new price shown are the ones the plan signs, not the row's`() {
        val review = limitEditReview(planned(baseAmount = 50_000L, price = 211_820L), row(remainingBase = "0.4"))!!

        assertEquals(0, BigDecimal("0.5").compareTo(review.base))
        assertEquals(0, BigDecimal("2118.20").compareTo(review.newPrice))
        assertEquals(0, BigDecimal("1800.00").compareTo(review.oldPrice))
        assertEquals(SCALE.baseDecimals, review.baseDecimals)
        assertEquals(SCALE.priceDecimals, review.priceDecimals)
    }

    @Test
    fun `the side comes from the resting order`() {
        val review = limitEditReview(planned(restingSide = PerpsSide.Short), row(side = PerpsPositionSide.LONG))!!

        assertEquals(PerpsPositionSide.SHORT, review.side)
    }

    @Test
    fun `legs the new price passes are flagged for removal`() {
        val takeProfit = PerpsLimitOrderLeg(orderIndex = 7L, triggerPrice = BigDecimal("2000"))
        val stopLoss = PerpsLimitOrderLeg(orderIndex = 8L, triggerPrice = BigDecimal("1500"))

        val review = limitEditReview(planned(), row(takeProfit = takeProfit, stopLoss = stopLoss))!!

        assertEquals(listOf(takeProfit), review.passedLegs)
        assertTrue(review.removesTakeProfit)
        assertFalse(review.removesStopLoss)
    }

    @Test
    fun `legs are checked against the tick-floored price that is signed, not the entered one`() {
        val stopLoss = PerpsLimitOrderLeg(orderIndex = 8L, triggerPrice = BigDecimal("2000.0"))

        val planned = planned(price = 200_000L, requestPrice = BigDecimal("2000.04"))

        val review = limitEditReview(planned, row(stopLoss = stopLoss))!!

        assertEquals(listOf(stopLoss), review.passedLegs)
        assertTrue(review.removesStopLoss)
    }

    @Test
    fun `a resting order without a limit price gives no review`() {
        assertNull(limitEditReview(planned(restingLimitPrice = null), row()))
    }

    @Test
    fun `a plan without the resting order gives no review`() {
        assertNull(limitEditReview(planned(restingIndex = ORDER_INDEX + 1), row()))
    }

    @Test
    fun `the confirmed request carries the confirmed size and old price`() {
        val review = limitEditReview(planned(baseAmount = 50_000L), row(remainingBase = "0.4"))!!

        val request = review.confirmedRequest()

        assertEquals(0, BigDecimal("0.5").compareTo(request.maxBaseAmount))
        assertEquals(0, BigDecimal("1800.00").compareTo(request.expectedLimitPrice))
        assertEquals(ORDER_INDEX, request.orderIndex)
        assertEquals(NEW_PRICE, request.price)
    }

    private fun planned(
        baseAmount: Long = 50_000L,
        price: Long = 211_820L,
        restingSide: PerpsSide = PerpsSide.Long,
        restingIndex: Long = ORDER_INDEX,
        restingLimitPrice: Long? = 180_000L,
        requestPrice: BigDecimal = NEW_PRICE,
    ): PerpsPlannedTrade {
        val resting = PerpsRestingOrder(
            orderIndex = restingIndex,
            side = restingSide,
            category = PerpsOrderCategory.Open,
            orderType = PerpsRestingOrderType.Limit,
            status = PerpsRestingOrderStatus.Open,
            reduceOnly = false,
            positionTied = false,
            remainingBaseAmount = baseAmount,
            limitPrice = restingLimitPrice,
            triggerPrice = null,
            expiryUnixMs = null,
        )
        val context = mockk<PerpetualAccountSnapshot>()
        every { context.restingOrders } returns listOf(resting)
        val rules = mockk<PerpsMarketRules>()
        every { rules.scale } returns SCALE
        val planned = mockk<PerpsPlannedTrade>()
        every { planned.intent } returns PerpsTradeIntent.OrderModify(
            operationId = "operation",
            scope = PerpsScope.review(
                walletId = WALLET_ID,
                venue = PerpsProductRules.VENUE,
                chainId = PerpsProductRules.CHAIN_ID,
                environment = PerpsProductRules.ENVIRONMENT,
            ),
            marketId = MARKET_INDEX.toLong(),
            orderIndex = ORDER_INDEX,
            baseAmount = baseAmount,
            price = price,
            triggerPrice = null,
        )
        every { planned.context } returns context
        every { planned.rules } returns rules
        every { planned.request } returns PerpsTradeRequest.OrderModify(
            walletId = WALLET_ID,
            marketIndex = MARKET_INDEX,
            orderIndex = ORDER_INDEX,
            price = requestPrice,
            maxBaseAmount = BigDecimal("0.4"),
        )
        return planned
    }

    private fun row(
        remainingBase: String = "0.5",
        side: PerpsPositionSide = PerpsPositionSide.LONG,
        takeProfit: PerpsLimitOrderLeg? = null,
        stopLoss: PerpsLimitOrderLeg? = null,
    ) = PerpsLimitOrder(
        orderIndex = ORDER_INDEX,
        clientOrderIndex = null,
        side = side,
        remainingBase = BigDecimal(remainingBase),
        limitPrice = BigDecimal("1800"),
        takeProfit = takeProfit,
        stopLoss = stopLoss,
    )

    private companion object {
        const val WALLET_ID = "wallet"
        const val MARKET_INDEX = 1
        const val ORDER_INDEX = 42L
        val NEW_PRICE = BigDecimal("2118.2")
        val SCALE = PerpsScale(priceDecimals = 2, baseDecimals = 5, quoteDecimals = 6)
    }
}
