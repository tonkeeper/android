package com.tonapps.perps.data

import io.kandelabrapi.models.Candle
import io.kandelabrapi.models.GetCandlesResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.TreeMap

class PerpsChartDataTest {

    @Test
    fun overflowingQuoteVolumeIsDroppedInsteadOfBecomingInfinite() {
        val candles = listOf(candle(time = 1L, close = 1e200), candle(time = 2L, close = 2.0))

        val withVolumes = candles.withVolumes(mapOf(1L to 1e200, 2L to 3.0))

        assertNull(withVolumes[0].volumeUsd)
        assertEquals(6.0, withVolumes[1].volumeUsd ?: 0.0, 0.0)
    }

    @Test
    fun changeIsNullWhenItCannotBeFinite() {
        assertNull(candle(time = 1L, close = 1e300).changeFrom(1e-300))

        val change = checkNotNull(candle(time = 1L, close = 110.0).changeFrom(100.0))
        assertEquals(10.0, change.amount, 1e-9)
        assertEquals(10.0, change.percent, 1e-9)
    }

    @Test
    fun nonFinitePricesFromTheWireAreDropped() {
        val response = GetCandlesResponse(
            ticker = "ETH/USD",
            resolution = "1m",
            startTs = START_MS,
            candles = listOf(
                Candle(o = "1e400", h = "2", l = "1", c = "1.5"),
                Candle(o = "Infinity", h = "2", l = "1", c = "1.5"),
                Candle(o = "NaN", h = "2", l = "1", c = "1.5"),
                Candle(o = "1", h = "2", l = "1", c = "1.5"),
            ),
        )

        val candles = response.toPerpsCandles(PerpsChartTimeframe.M1.stepMillis)

        assertEquals(listOf(START_MS / 1000 + 180), candles.map { it.time })
    }

    @Test
    fun barsOutsideTheSupportedTimeRangeAreDropped() {
        val lastSupportedMs = 4_102_444_800_000L
        val step = PerpsChartTimeframe.H1.stepMillis
        val candle = Candle(o = "1", h = "2", l = "1", c = "1.5", v = "3")

        fun times(startTs: Long) = GetCandlesResponse(
            ticker = "ETH/USD",
            resolution = "1h",
            startTs = startTs,
            candles = listOf(candle, candle),
        ).toPerpsCandles(step).map { it.time }

        assertEquals(listOf(lastSupportedMs / 1000), times(lastSupportedMs))
        assertEquals(emptyList<Long>(), times(Long.MAX_VALUE - step))
        assertEquals(emptyList<Long>(), times(5_000_000_000_000L))
        assertEquals(
            mapOf(lastSupportedMs / 1000 to 3.0),
            GetCandlesResponse("ETH/USD", "1h", lastSupportedMs, listOf(candle, candle)).toBaseVolumes(step),
        )
    }

    @Test
    fun feedMapsKeepOnlyTheNewestBars() {
        val bars = TreeMap((1L..10L).associateWith { it.toDouble() })

        bars.keepNewest(3)

        assertEquals(listOf(8L, 9L, 10L), bars.keys.toList())
    }

    private fun candle(time: Long, close: Double) = PerpsCandle(
        time = time,
        open = close,
        high = close,
        low = close,
        close = close,
        volumeUsd = null,
    )

    private companion object {
        const val START_MS = 1_700_000_000_000L
    }
}
