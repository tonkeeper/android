package com.tonapps.chart.data

import com.tonapps.chart.options.ChartColor
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/** [time] is a UTC timestamp in seconds, as lightweight-charts expects. */
@Serializable
data class AreaPoint(
    val time: Long,
    val value: Double,
)

@Serializable
data class CandlestickBar(
    val time: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
)

@Serializable
data class HistogramBar(
    val time: Long,
    val value: Double,
    val color: ChartColor? = null,
)

/**
 * Crosshair position reported by the chart. [seriesData] maps series ids to the
 * raw bar payload under the crosshair (`{time, value}` / `{time, open, ...}`).
 */
data class CrosshairEvent(
    val time: Long,
    val point: CrosshairPoint,
    val seriesData: JsonObject,
)

data class CrosshairPoint(
    val x: Float,
    val y: Float,
)
