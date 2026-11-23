package com.tonapps.perps.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

class PerpsPositionProximityTest {

    @Test
    fun closestToLiquidationGoesFirstRegardlessOfSide() {
        val farLong = position(marketIndex = 1, distance = "-30")
        val nearLong = position(marketIndex = 2, distance = "-4.5")
        val nearestShort = position(marketIndex = 3, distance = "2.1")

        val ordered = listOf(farLong, nearLong, nearestShort).sortedByLiquidationProximity()

        assertEquals(listOf(nearestShort, nearLong, farLong), ordered)
    }

    @Test
    fun positionsWithoutDistanceGoLastInInputOrder() {
        val firstUnknown = position(marketIndex = 1, distance = null)
        val far = position(marketIndex = 2, distance = "-80")
        val secondUnknown = position(marketIndex = 3, distance = null)
        val near = position(marketIndex = 4, distance = "1")

        val ordered = listOf(firstUnknown, far, secondUnknown, near).sortedByLiquidationProximity()

        assertEquals(listOf(near, far, firstUnknown, secondUnknown), ordered)
    }

    @Test
    fun equalDistancesKeepInputOrder() {
        val short = position(marketIndex = 1, distance = "5")
        val long = position(marketIndex = 2, distance = "-5")

        assertEquals(listOf(short, long), listOf(short, long).sortedByLiquidationProximity())
    }

    private fun position(marketIndex: Int, distance: String?) = PerpsPosition(
        marketIndex = marketIndex,
        symbol = "M$marketIndex",
        iconUrl = null,
        side = PerpsPositionSide.LONG,
        margin = BigDecimal.TEN,
        unrealizedPnl = BigDecimal.ZERO,
        equity = BigDecimal.TEN,
        leverage = 10,
        liquidationDistancePct = distance?.let(::BigDecimal),
    )
}
