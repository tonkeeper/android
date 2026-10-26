package com.tonapps.perps.data

import androidx.annotation.StringRes
import com.tonapps.wallet.localization.Localization
import io.kandelabrapi.models.GetCandlesResponse
import java.util.TimeZone

// Kandelabr has no 12h resolution: 12H rides the 4h wire feed and is aggregated client-side.
enum class PerpsChartTimeframe(
    @StringRes val label: Int,
    val wireResolution: String,
    val stepMillis: Long,
) {
    M1(Localization.perps_chart_timeframe_1m, "1m", 60_000L),
    M5(Localization.perps_chart_timeframe_5m, "5m", 300_000L),
    M15(Localization.perps_chart_timeframe_15m, "15m", 900_000L),
    H1(Localization.perps_chart_timeframe_1h, "1h", 3_600_000L),
    H4(Localization.perps_chart_timeframe_4h, "4h", 14_400_000L),
    H12(Localization.perps_chart_timeframe_12h, "4h", 14_400_000L),
    D1(Localization.perps_chart_timeframe_1d, "1d", 86_400_000L),
}

enum class PerpsChartMode {
    CANDLE,
    LINE,
}

enum class PerpsChartSectionState {
    LOADING,
    EMPTY,
    FAILED,
    READY,
}

data class PerpsCandle(
    val time: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volumeUsd: Double?,
)

data class PerpsPositionLevels(
    val entry: Double?,
    val liquidation: Double?,
    val takeProfit: Double?,
    val stopLoss: Double?,
)

data class PerpsChartChange(
    val percent: Double,
    val amount: Double,
    val isPositive: Boolean,
)

data class PerpsChartPoint(val timeSec: Long, val baseTimeSec: Long)

// Dense bars from one bucket-aligned start; volume rides the PERP_TRADE series, see withVolumes.
fun GetCandlesResponse.toPerpsCandles(stepMillis: Long): List<PerpsCandle> {
    return candles.mapIndexedNotNull { index, candle ->
        val time = (startTs + index * stepMillis).toChartSecondsOrNull() ?: return@mapIndexedNotNull null
        val open = candle.o.toPositiveOrNull() ?: return@mapIndexedNotNull null
        val high = candle.h.toPositiveOrNull() ?: return@mapIndexedNotNull null
        val low = candle.l.toPositiveOrNull() ?: return@mapIndexedNotNull null
        val close = candle.c.toPositiveOrNull() ?: return@mapIndexedNotNull null
        if (high < low) {
            return@mapIndexedNotNull null
        }
        PerpsCandle(
            time = time,
            open = open,
            high = high,
            low = low,
            close = close,
            volumeUsd = null,
        )
    }
        .associateBy { it.time }
        .values
        .sortedBy { it.time }
}

fun GetCandlesResponse.toBaseVolumes(stepMillis: Long): Map<Long, Double> {
    val volumes = HashMap<Long, Double>(candles.size)
    candles.forEachIndexed { index, candle ->
        if (candle.empty != true) {
            val time = (startTs + index * stepMillis).toChartSecondsOrNull()
            val volume = candle.v.toPositiveOrNull()
            if (time != null && volume != null) {
                volumes[time] = volume
            }
        }
    }
    return volumes
}

fun List<PerpsCandle>.withVolumes(volumes: Map<Long, Double>): List<PerpsCandle> {
    return map { candle -> candle.copy(volumeUsd = volumes[candle.time]?.times(candle.close)) }
}

data class PerpsCandleHistory(
    val candles: List<PerpsCandle>,
    val volumes: Map<Long, Double>,
)

fun PerpsCandle.changeFrom(basePrice: Double): PerpsChartChange {
    val amount = close - basePrice
    val percent = if (basePrice > 0.0) {
        amount / basePrice * PERCENT_FACTOR
    } else {
        0.0
    }
    return PerpsChartChange(percent = percent, amount = amount, isPositive = amount >= 0.0)
}

fun perpsChartZoneOffsetSeconds(): Long =
    TimeZone.getDefault().getOffset(System.currentTimeMillis()) / MILLIS_IN_SECOND

private fun Long.toChartSecondsOrNull(): Long? {
    if (this <= 0L) {
        return null
    }
    if (this >= MILLIS_MAGNITUDE_THRESHOLD) {
        return this / MILLIS_IN_SECOND
    }
    return this
}

private fun String?.toPositiveOrNull(): Double? {
    val value = this?.toDoubleOrNull() ?: return null
    if (value.isFinite() && value > 0.0) {
        return value
    }
    return null
}

private const val MILLIS_IN_SECOND = 1_000L
private const val MILLIS_MAGNITUDE_THRESHOLD = 100_000_000_000L
private const val PERCENT_FACTOR = 100.0
