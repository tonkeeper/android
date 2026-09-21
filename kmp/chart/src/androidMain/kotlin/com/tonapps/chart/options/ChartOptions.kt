package com.tonapps.chart.options

import kotlinx.serialization.Serializable

/**
 * Subset of the lightweight-charts `ChartOptions` used by the app. Field names
 * match the JS API one-to-one — the objects are serialized to JSON and passed
 * to `LightweightCharts.createChart` / `chart.applyOptions` verbatim.
 * Null means "keep the library default".
 */
@Serializable
data class ChartOptions(
    val layout: LayoutOptions? = null,
    val rightPriceScale: PriceScaleOptions? = null,
    val leftPriceScale: PriceScaleOptions? = null,
    val timeScale: TimeScaleOptions? = null,
    val crosshair: CrosshairOptions? = null,
    val grid: GridOptions? = null,
    val handleScroll: HandleScrollOptions? = null,
    val handleScale: HandleScaleOptions? = null,
    val trackingMode: TrackingModeOptions? = null,
)

@Serializable
data class LayoutOptions(
    val background: BackgroundOptions? = null,
    val textColor: ChartColor? = null,
    val fontSize: Int? = null,
    val fontFamily: String? = null,
    val attributionLogo: Boolean? = null,
)

@Serializable
data class BackgroundOptions(
    val type: String = "solid",
    val color: ChartColor,
)

@Serializable
data class PriceScaleOptions(
    val visible: Boolean? = null,
    val autoScale: Boolean? = null,
    val borderVisible: Boolean? = null,
    val borderColor: ChartColor? = null,
    val textColor: ChartColor? = null,
    val ticksVisible: Boolean? = null,
    val minimumWidth: Int? = null,
    val scaleMargins: PriceScaleMargins? = null,
)

@Serializable
data class PriceScaleMargins(
    val top: Double,
    val bottom: Double,
)

@Serializable
data class TimeScaleOptions(
    val rightOffset: Double? = null,
    val barSpacing: Double? = null,
    val minBarSpacing: Double? = null,
    val fixLeftEdge: Boolean? = null,
    val fixRightEdge: Boolean? = null,
    val borderVisible: Boolean? = null,
    val visible: Boolean? = null,
    val timeVisible: Boolean? = null,
    val secondsVisible: Boolean? = null,
)

@Serializable
data class CrosshairOptions(
    val mode: CrosshairMode? = null,
    val vertLine: CrosshairLineOptions? = null,
    val horzLine: CrosshairLineOptions? = null,
)

@Serializable
data class CrosshairLineOptions(
    val color: ChartColor? = null,
    val width: Int? = null,
    val style: LineStyle? = null,
    val visible: Boolean? = null,
    val labelVisible: Boolean? = null,
    val labelBackgroundColor: ChartColor? = null,
)

@Serializable
data class GridOptions(
    val vertLines: GridLineOptions? = null,
    val horzLines: GridLineOptions? = null,
)

@Serializable
data class GridLineOptions(
    val color: ChartColor? = null,
    val style: LineStyle? = null,
    val visible: Boolean? = null,
)

@Serializable
data class HandleScrollOptions(
    val mouseWheel: Boolean? = null,
    val pressedMouseMove: Boolean? = null,
    val horzTouchDrag: Boolean? = null,
    val vertTouchDrag: Boolean? = null,
)

@Serializable
data class HandleScaleOptions(
    val mouseWheel: Boolean? = null,
    val pinch: Boolean? = null,
    val axisPressedMouseMove: Boolean? = null,
    val axisDoubleClickReset: Boolean? = null,
)

@Serializable
data class TrackingModeOptions(
    val exitMode: TrackingModeExitMode,
)

@Serializable
data class PriceFormat(
    val type: String,
    val precision: Int? = null,
    val minMove: Double? = null,
) {

    companion object {
        fun price(precision: Int, minMove: Double): PriceFormat {
            return PriceFormat(type = "price", precision = precision, minMove = minMove)
        }

        fun volume(): PriceFormat {
            return PriceFormat(type = "volume")
        }
    }
}
