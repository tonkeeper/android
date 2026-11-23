package com.tonapps.perps.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PerpsChartFeedTest {

    @Test
    fun barsTheFeedTrimmedDoNotPileUpInTheHeldFrame() {
        var state = PerpsChartFeedState()
        val sizes = mutableListOf<Int>()
        val heads = mutableListOf<Long>()
        for (push in 0 until 10) {
            val newest = 100_000L + push * 2_000L
            val series = ((newest - 4_999L)..newest).map(::candle)
            state = state.onChartFeedEvent(PerpsChartFeedEvent.Series(series)).state
            sizes += checkNotNull(state.held).size
            heads += checkNotNull(state.held).first().time
        }

        assertEquals(
            listOf(5_000, 7_000, 9_000, 11_000, 13_000, 15_000, 17_000, 19_000, 18_000, 20_000),
            sizes,
        )
        assertEquals(1, heads.zipWithNext().count { (before, after) -> before != after })
        assertEquals(118_000L, checkNotNull(state.held).last().time)
    }

    @Test
    fun olderPagesStopOnceTheFrameIsFull() {
        var state = PerpsChartFeedState()
            .onChartFeedEvent(PerpsChartFeedEvent.Series((100_000L until 105_000L).map(::candle)))
            .state
        var oldest = 100_000L
        while (!state.historyExhausted) {
            val page = ((oldest - 5_000L) until oldest).map(::candle)
            state = state.onChartFeedEvent(PerpsChartFeedEvent.Older(page, endTimeSeconds = oldest)).state
            oldest = checkNotNull(state.held).first().time
            assertTrue(checkNotNull(state.held).size <= 20_000)
        }

        assertEquals(20_000, checkNotNull(state.held).size)
        assertEquals(104_999L, checkNotNull(state.held).last().time)
    }

    @Test
    fun olderPagesBelowTheLimitKeepPaging() {
        val state = PerpsChartFeedState()
            .onChartFeedEvent(PerpsChartFeedEvent.Series((100L until 600L).map(::candle)))
            .state
            .onChartFeedEvent(PerpsChartFeedEvent.Older((1L until 100L).map(::candle), endTimeSeconds = 100L))
            .state

        assertFalse(state.historyExhausted)
        assertEquals(599, checkNotNull(state.held).size)
    }

    private fun candle(time: Long) = PerpsCandle(
        time = time,
        open = 1.0,
        high = 1.0,
        low = 1.0,
        close = 1.0,
        volumeUsd = null,
    )
}
