package com.tonapps.perps.trade

import com.tonapps.perps.data.PerpsOrderSide
import io.perpsapi.models.ClientMetricPositionClose
import io.perpsapi.models.ClientMetricPositionOpen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal

class PerpsWaitMetricTest {

    @Test
    fun `a market open is a position_open wait`() {
        assertEquals(
            ClientMetricPositionOpen(id = METRIC_ID, type = "position_open", durationMs = DURATION_MS),
            perpsWaitMetric(open(limitPrice = null), METRIC_ID, DURATION_MS),
        )
    }

    @Test
    fun `a limit open sends no wait`() {
        assertNull(perpsWaitMetric(open(limitPrice = BigDecimal("2345.5")), METRIC_ID, DURATION_MS))
    }

    @Test
    fun `a close is a position_close wait`() {
        assertEquals(
            ClientMetricPositionClose(id = METRIC_ID, type = "position_close", durationMs = DURATION_MS),
            perpsWaitMetric(PerpsTradeRequest.Close(WALLET_ID, MARKET_INDEX), METRIC_ID, DURATION_MS),
        )
    }

    @Test
    fun `a reduce, an add and a margin change send no wait`() {
        val requests = listOf(
            PerpsTradeRequest.Reduce(WALLET_ID, MARKET_INDEX, BigDecimal.ONE, PerpsAutoCloseEdit.Keep),
            PerpsTradeRequest.Add(WALLET_ID, MARKET_INDEX, BigDecimal.ONE, PerpsAutoCloseEdit.Keep),
            PerpsTradeRequest.Margin(WALLET_ID, MARKET_INDEX, BigDecimal.ONE, add = true),
        )
        for (request in requests) {
            assertNull(perpsWaitMetric(request, METRIC_ID, DURATION_MS))
        }
    }

    private fun open(limitPrice: BigDecimal?) = PerpsTradeRequest.Open(
        walletId = WALLET_ID,
        marketIndex = MARKET_INDEX,
        side = PerpsOrderSide.LONG,
        marginQuote = BigDecimal.TEN,
        leverage = 5,
        autoClose = null,
        limitPrice = limitPrice,
    )

    private companion object {
        const val WALLET_ID = "wallet"
        const val MARKET_INDEX = 1
        const val METRIC_ID = "metric"
        const val DURATION_MS = 2140L
    }
}
