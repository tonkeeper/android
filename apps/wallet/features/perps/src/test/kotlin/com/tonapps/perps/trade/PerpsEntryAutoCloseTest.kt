package com.tonapps.perps.trade

import com.tonapps.chainkit.perpetual.PerpsOrderCategory
import com.tonapps.chainkit.perpetual.PerpsRestingOrder
import com.tonapps.chainkit.perpetual.PerpsRestingOrderStatus
import com.tonapps.chainkit.perpetual.PerpsRestingOrderType
import com.tonapps.chainkit.perpetual.PerpsSide
import com.tonapps.chainkit.perpetual.PerpsTradeError
import com.tonapps.chainkit.perpetual.PerpsTradeException
import com.tonapps.perps.data.PerpsAutoCloseDraft
import com.tonapps.perps.data.PerpsOrderSide
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.math.BigDecimal

class PerpsEntryAutoCloseTest {

    @Test
    fun `percent legs move to the fill price`() {
        val placed = PerpsAutoCloseDraft(
            takeProfit = BigDecimal("82847.3"),
            stopLoss = BigDecimal("81862.9"),
            takeProfitRoi = BigDecimal("50"),
            stopLossRoi = BigDecimal("-10"),
        )

        val target = longFill(placed, resting(takeProfit = 828_473L, stopLoss = 818_629L))

        assertEquals(BigDecimal("82860.4"), target.takeProfit)
        assertEquals(BigDecimal("81875.9"), target.stopLoss)
    }

    @Test
    fun `a leg typed as a price keeps its price`() {
        val placed = PerpsAutoCloseDraft(
            takeProfit = BigDecimal("83000.0"),
            stopLoss = BigDecimal("81862.9"),
            stopLossRoi = BigDecimal("-10"),
        )

        val target = longFill(placed, resting(takeProfit = 830_000L, stopLoss = 818_629L))

        assertEquals(BigDecimal("83000.0"), target.takeProfit)
        assertEquals(BigDecimal("81875.9"), target.stopLoss)
    }

    @Test
    fun `a short moves its legs the other way`() {
        val placed = PerpsAutoCloseDraft(
            takeProfit = BigDecimal("98.10"),
            stopLoss = BigDecimal("100.60"),
            takeProfitRoi = BigDecimal("20"),
            stopLossRoi = BigDecimal("-5"),
        )

        val target = entryAutoClose(
            placed = placed,
            restingOrders = resting(takeProfit = 9_810L, stopLoss = 10_060L),
            entryPrice = BigDecimal("100.00"),
            leverage = BigDecimal(10),
            side = PerpsOrderSide.SHORT,
            priceDecimals = 2,
        )

        assertEquals(BigDecimal("98.00"), target.takeProfit)
        assertEquals(BigDecimal("100.50"), target.stopLoss)
    }

    @Test
    fun `legs that are not resting yet are reported as not found`() {
        val placed = PerpsAutoCloseDraft(
            takeProfit = BigDecimal("82847.3"),
            stopLoss = BigDecimal("81862.9"),
            takeProfitRoi = BigDecimal("50"),
            stopLossRoi = BigDecimal("-10"),
        )

        val error = assertThrows(PerpsTradeException::class.java) {
            longFill(placed, resting(takeProfit = 828_473L, stopLoss = null))
        }

        assertEquals(PerpsTradeError.OrderNotFound, error.kind)
    }

    @Test
    fun `legs changed after the open are left alone`() {
        val placed = PerpsAutoCloseDraft(
            takeProfit = BigDecimal("82847.3"),
            stopLoss = BigDecimal("81862.9"),
            takeProfitRoi = BigDecimal("50"),
            stopLossRoi = BigDecimal("-10"),
        )

        val edited = assertThrows(PerpsTradeException::class.java) {
            longFill(placed, resting(takeProfit = 835_000L, stopLoss = 818_629L))
        }
        val added = assertThrows(PerpsTradeException::class.java) {
            longFill(placed.copy(takeProfit = null, takeProfitRoi = null), resting(takeProfit = 835_000L, stopLoss = 818_629L))
        }

        assertEquals(PerpsTradeError.InvalidAutoClose, edited.kind)
        assertEquals(PerpsTradeError.InvalidAutoClose, added.kind)
    }

    @Test
    fun `a leg moved within two points of ROI is re-priced`() {
        val placed = PerpsAutoCloseDraft(
            takeProfit = BigDecimal("104.90"),
            stopLoss = null,
            takeProfitRoi = BigDecimal("50"),
        )

        val target = longFillAtHundred(placed, resting(takeProfit = 10_490L, stopLoss = null))

        assertEquals(BigDecimal("105.00"), target.takeProfit)
    }

    @Test
    fun `a leg moved over two points of ROI keeps the confirmed trigger`() {
        val placed = PerpsAutoCloseDraft(
            takeProfit = null,
            stopLoss = BigDecimal("97.50"),
            stopLossRoi = BigDecimal("-50"),
        )

        val target = entryAutoClose(
            placed = placed,
            restingOrders = resting(takeProfit = null, stopLoss = 9_750L),
            entryPrice = BigDecimal("98.05"),
            leverage = BigDecimal(20),
            side = PerpsOrderSide.LONG,
            priceDecimals = 2,
        )

        assertEquals(BigDecimal("97.50"), target.stopLoss)
    }

    @Test
    fun `each leg is capped on its own`() {
        val placed = PerpsAutoCloseDraft(
            takeProfit = BigDecimal("101.94"),
            stopLoss = BigDecimal("98.90"),
            takeProfitRoi = BigDecimal("50"),
            stopLossRoi = BigDecimal("-10"),
        )

        val target = longFillAtHundred(placed, resting(takeProfit = 10_194L, stopLoss = 9_890L))

        assertEquals(BigDecimal("101.94"), target.takeProfit)
        assertEquals(BigDecimal("99.00"), target.stopLoss)
    }

    private fun longFillAtHundred(placed: PerpsAutoCloseDraft, restingOrders: List<PerpsRestingOrder>) = entryAutoClose(
        placed = placed,
        restingOrders = restingOrders,
        entryPrice = BigDecimal("100.00"),
        leverage = BigDecimal(10),
        side = PerpsOrderSide.LONG,
        priceDecimals = 2,
    )

    private fun longFill(placed: PerpsAutoCloseDraft, restingOrders: List<PerpsRestingOrder>) = entryAutoClose(
        placed = placed,
        restingOrders = restingOrders,
        entryPrice = BigDecimal("82040.0"),
        leverage = BigDecimal(50),
        side = PerpsOrderSide.LONG,
        priceDecimals = 1,
    )

    private fun resting(takeProfit: Long?, stopLoss: Long?): List<PerpsRestingOrder> = listOfNotNull(
        takeProfit?.let { leg(PerpsOrderCategory.TakeProfit, PerpsRestingOrderType.TakeProfit, it) },
        stopLoss?.let { leg(PerpsOrderCategory.StopLoss, PerpsRestingOrderType.StopLoss, it) },
    )

    private fun leg(category: PerpsOrderCategory, type: PerpsRestingOrderType, triggerPrice: Long) = PerpsRestingOrder(
        orderIndex = category.ordinal.toLong(),
        side = PerpsSide.Short,
        category = category,
        orderType = type,
        status = PerpsRestingOrderStatus.Open,
        reduceOnly = true,
        positionTied = true,
        remainingBaseAmount = 0L,
        triggerPrice = triggerPrice,
        expiryUnixMs = EXPIRY_MS,
    )

    private companion object {
        const val EXPIRY_MS = 1_800_000_000_000L
    }
}
