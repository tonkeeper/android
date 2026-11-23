package com.tonapps.perps.trade

import com.tonapps.chainkit.perpetual.PerpsOrderCategory
import com.tonapps.chainkit.perpetual.PerpsRestingOrder
import com.tonapps.chainkit.perpetual.PerpsRestingOrderStatus
import com.tonapps.chainkit.perpetual.PerpsRestingOrderType
import com.tonapps.chainkit.perpetual.PerpsSide
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PerpsRenewableLegsTest {

    @Test
    fun `one leg inside the lead renews both`() {
        val takeProfit = leg(PerpsOrderCategory.TakeProfit, expiryUnixMs = NOW + LEAD - 1)
        val stopLoss = leg(PerpsOrderCategory.StopLoss, expiryUnixMs = NOW + LEAD * 3)

        assertEquals(takeProfit to stopLoss, renewableLegs(listOf(takeProfit, stopLoss), NOW, CONFIRMED))
    }

    @Test
    fun `a single due leg renews alone`() {
        val stopLoss = leg(PerpsOrderCategory.StopLoss, expiryUnixMs = NOW + 1)

        assertEquals(null to stopLoss, renewableLegs(listOf(stopLoss), NOW, CONFIRMED))
    }

    @Test
    fun `a leg expiring exactly at the lead is not due yet`() {
        val takeProfit = leg(PerpsOrderCategory.TakeProfit, expiryUnixMs = NOW + LEAD)

        assertNull(renewableLegs(listOf(takeProfit), NOW, CONFIRMED))
    }

    @Test
    fun `an already expired leg still renews`() {
        val takeProfit = leg(PerpsOrderCategory.TakeProfit, expiryUnixMs = NOW - LEAD)

        assertEquals(takeProfit to null, renewableLegs(listOf(takeProfit), NOW, CONFIRMED))
    }

    @Test
    fun `a leg without an expiry is never due`() {
        assertNull(renewableLegs(listOf(leg(PerpsOrderCategory.TakeProfit, expiryUnixMs = null)), NOW, CONFIRMED))
        assertNull(renewableLegs(listOf(leg(PerpsOrderCategory.TakeProfit, expiryUnixMs = 0L)), NOW, CONFIRMED))
    }

    @Test
    fun `a leg missing its trigger is left out of the renewal`() {
        val takeProfit = leg(PerpsOrderCategory.TakeProfit, expiryUnixMs = NOW + 1)
        val stopLoss = leg(PerpsOrderCategory.StopLoss, expiryUnixMs = NOW + LEAD * 3, triggerPrice = null)

        assertEquals(takeProfit to null, renewableLegs(listOf(takeProfit, stopLoss), NOW, CONFIRMED))
    }

    @Test
    fun `orders that are not live position legs are ignored`() {
        val canceled = leg(PerpsOrderCategory.TakeProfit, expiryUnixMs = NOW + 1, status = PerpsRestingOrderStatus.Canceled)
        val sized = leg(PerpsOrderCategory.StopLoss, expiryUnixMs = NOW + 1, positionTied = false)
        val limit = leg(PerpsOrderCategory.Open, expiryUnixMs = NOW + 1)

        assertNull(renewableLegs(listOf(canceled, sized, limit), NOW, CONFIRMED))
    }

    @Test
    fun `a leg this device did not record is not renewed`() {
        val takeProfit = leg(PerpsOrderCategory.TakeProfit, expiryUnixMs = NOW + 1)
        val stopLoss = leg(PerpsOrderCategory.StopLoss, expiryUnixMs = NOW + 1)

        assertEquals(null to stopLoss, renewableLegs(listOf(takeProfit, stopLoss), NOW, mapOf(STOP_LOSS_INDEX to TRIGGER)))
    }

    @Test
    fun `a leg recorded at another trigger is not renewed`() {
        val takeProfit = leg(PerpsOrderCategory.TakeProfit, expiryUnixMs = NOW + 1)

        assertNull(renewableLegs(listOf(takeProfit), NOW, mapOf(TAKE_PROFIT_INDEX to TRIGGER + 1)))
    }

    @Test
    fun `a recorded take profit renews without an unrecorded stop loss`() {
        val takeProfit = leg(PerpsOrderCategory.TakeProfit, expiryUnixMs = NOW + 1)
        val stopLoss = leg(PerpsOrderCategory.StopLoss, expiryUnixMs = NOW + 1)

        assertEquals(takeProfit to null, renewableLegs(listOf(takeProfit, stopLoss), NOW, mapOf(TAKE_PROFIT_INDEX to TRIGGER)))
    }

    @Test
    fun `nothing recorded renews nothing`() {
        val takeProfit = leg(PerpsOrderCategory.TakeProfit, expiryUnixMs = NOW + 1)
        val stopLoss = leg(PerpsOrderCategory.StopLoss, expiryUnixMs = NOW + 1)

        assertNull(renewableLegs(listOf(takeProfit, stopLoss), NOW, emptyMap()))
    }

    @Test
    fun `an unrecorded stop loss without a trigger is not cancelled with the take profit`() {
        val takeProfit = leg(PerpsOrderCategory.TakeProfit, expiryUnixMs = NOW + 1)
        val stopLoss = leg(PerpsOrderCategory.StopLoss, expiryUnixMs = NOW + 1, triggerPrice = null)

        assertEquals(takeProfit to null, renewableLegs(listOf(takeProfit, stopLoss), NOW, mapOf(TAKE_PROFIT_INDEX to TRIGGER)))
    }

    @Test
    fun `an unrecorded leg without a trigger renews nothing`() {
        val stopLoss = leg(PerpsOrderCategory.StopLoss, expiryUnixMs = NOW + 1, triggerPrice = null)

        assertNull(renewableLegs(listOf(stopLoss), NOW, mapOf(TAKE_PROFIT_INDEX to TRIGGER)))
    }

    @Test
    fun `a recorded leg that lost its trigger is dropped`() {
        val stopLoss = leg(PerpsOrderCategory.StopLoss, expiryUnixMs = NOW + 1, triggerPrice = null)

        assertNull(renewableLegs(listOf(stopLoss), NOW, CONFIRMED))
    }

    @Test
    fun `a recorded leg behind an unrecorded one of the same category is not renewed`() {
        val foreign = leg(PerpsOrderCategory.StopLoss, expiryUnixMs = NOW + 1).copy(clientOrderIndex = FOREIGN_INDEX)
        val stopLoss = leg(PerpsOrderCategory.StopLoss, expiryUnixMs = NOW + 1)

        assertNull(renewableLegs(listOf(foreign, stopLoss), NOW, CONFIRMED))
    }

    private fun leg(
        category: PerpsOrderCategory,
        expiryUnixMs: Long?,
        triggerPrice: Long? = TRIGGER,
        status: PerpsRestingOrderStatus = PerpsRestingOrderStatus.Open,
        positionTied: Boolean = true,
    ) = PerpsRestingOrder(
        orderIndex = category.ordinal.toLong(),
        clientOrderIndex = CLIENT_ORDER_INDEX_BASE + category.ordinal,
        side = PerpsSide.Short,
        category = category,
        orderType = PerpsRestingOrderType.TakeProfit,
        status = status,
        reduceOnly = true,
        positionTied = positionTied,
        remainingBaseAmount = 0L,
        triggerPrice = triggerPrice,
        expiryUnixMs = expiryUnixMs,
    )

    private companion object {
        const val NOW = 1_800_000_000_000L
        const val LEAD = PerpsProductRules.AUTO_CLOSE_RENEWAL_LEAD_MS
        const val TRIGGER = 100_000L
        const val CLIENT_ORDER_INDEX_BASE = 1_000L
        val TAKE_PROFIT_INDEX = CLIENT_ORDER_INDEX_BASE + PerpsOrderCategory.TakeProfit.ordinal
        val STOP_LOSS_INDEX = CLIENT_ORDER_INDEX_BASE + PerpsOrderCategory.StopLoss.ordinal
        const val FOREIGN_INDEX = 9_999L
        val CONFIRMED = mapOf(TAKE_PROFIT_INDEX to TRIGGER, STOP_LOSS_INDEX to TRIGGER)
    }
}
