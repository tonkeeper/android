package com.tonapps.perps.data

import io.kandelabrapi.models.GetCandlesResponse

// Kandelabr has no 12h resolution: 12H rides the 4h wire feed and is aggregated client-side.
enum class PerpsChartTimeframe(
    val label: String,
    val wireResolution: String,
    val stepMillis: Long,
) {
    M1("1M", "1m", 60_000L),
    M5("5M", "5m", 300_000L),
    M15("15M", "15m", 900_000L),
    H1("1H", "1h", 3_600_000L),
    H4("4H", "4h", 14_400_000L),
    H12("12H", "4h", 14_400_000L),
    D1("1D", "1d", 86_400_000L),
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

// The wire is dense bars from one bucket-aligned start; volume arrives base-asset, the UI shows $.
fun GetCandlesResponse.toPerpsCandles(stepMillis: Long): List<PerpsCandle> {
    return candles.mapIndexedNotNull { index, candle ->
        val time = (startTs + index * stepMillis).toChartSecondsOrNull() ?: return@mapIndexedNotNull null
        val open = candle.o.toPriceOrNull() ?: return@mapIndexedNotNull null
        val high = candle.h.toPriceOrNull() ?: return@mapIndexedNotNull null
        val low = candle.l.toPriceOrNull() ?: return@mapIndexedNotNull null
        val close = candle.c.toPriceOrNull() ?: return@mapIndexedNotNull null
        if (high < low) {
            return@mapIndexedNotNull null
        }
        PerpsCandle(
            time = time,
            open = open,
            high = high,
            low = low,
            close = close,
            volumeUsd = candle.v.toVolumeOrNull()?.times(close),
        )
    }
        .associateBy { it.time }
        .values
        .sortedBy { it.time }
}

fun PerpsCandle.changeIn(candles: List<PerpsCandle>, mode: PerpsChartMode): PerpsChartChange {
    val baseline = when (mode) {
        PerpsChartMode.CANDLE -> open
        PerpsChartMode.LINE -> {
            val index = candles.indexOfFirst { it.time == time }
            candles.getOrNull(index - 1)?.close ?: open
        }
    }
    val amount = close - baseline
    val percent = if (baseline > 0.0) {
        amount / baseline * PERCENT_FACTOR
    } else {
        0.0
    }
    return PerpsChartChange(percent = percent, amount = amount, isPositive = amount >= 0.0)
}

private fun Long.toChartSecondsOrNull(): Long? {
    if (this <= 0L) {
        return null
    }
    if (this >= MILLIS_MAGNITUDE_THRESHOLD) {
        return this / MILLIS_IN_SECOND
    }
    return this
}

private fun String?.toPriceOrNull(): Double? {
    val value = this?.toDoubleOrNull() ?: return null
    if (value.isFinite() && value > 0.0) {
        return value
    }
    return null
}

private fun String?.toVolumeOrNull(): Double? {
    val value = this?.toDoubleOrNull() ?: return null
    if (value.isFinite() && value >= 0.0) {
        return value
    }
    return null
}

private const val MILLIS_IN_SECOND = 1_000L
private const val MILLIS_MAGNITUDE_THRESHOLD = 100_000_000_000L
private const val PERCENT_FACTOR = 100.0
