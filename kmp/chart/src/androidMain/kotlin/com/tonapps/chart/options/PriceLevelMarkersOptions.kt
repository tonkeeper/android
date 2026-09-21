package com.tonapps.chart.options

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Models of the custom price-level markers plugin (price_level_markers.js):
 * position lines, the live-price bubble and the custom axis/crosshair drawing.
 * Field names mirror the plugin options one-to-one.
 */
@Serializable
data class PriceLevelMarker(
    val id: String,
    val price: Double,
    val title: String? = null,
    val color: ChartColor,
    val lineColor: ChartColor? = null,
    val backgroundColor: ChartColor? = null,
    val align: Alignment = Alignment.Left,
    val lineVisible: Boolean? = null,
    val direction: Direction? = null,
    val isCurrentPrice: Boolean = false,
) {

    @Serializable
    enum class Alignment {
        @SerialName("left")
        Left,

        @SerialName("right")
        Right,
    }

    @Serializable
    enum class Direction {
        @SerialName("up")
        Up,

        @SerialName("down")
        Down,
    }
}

@Serializable
data class PriceLevelMarkersOptions(
    val markers: List<PriceLevelMarker> = emptyList(),
    val priceDecimals: Int? = null,
    val currentPriceUpLabelColor: ChartColor? = null,
    val currentPriceUpLabelBackgroundColor: ChartColor? = null,
    val currentPriceDownLabelColor: ChartColor? = null,
    val currentPriceDownLabelBackgroundColor: ChartColor? = null,
    val axisLabelColor: ChartColor? = null,
    val dotHaloColor: ChartColor? = null,
    val dotColor: ChartColor? = null,
    val crosshairColor: ChartColor? = null,
    val crosshairLabelColor: ChartColor? = null,
    val crosshairLabelBackgroundColor: ChartColor? = null,
    val gridColor: ChartColor? = null,
    val axisLabelMode: AxisLabelMode? = null,
    val crosshairHorizontalVisible: Boolean? = null,
    val crosshairLabelVisible: Boolean? = null,
    val crosshairDotOnSeries: Boolean? = null,
) {

    @Serializable
    enum class AxisLabelMode {
        /** Nice-stepped price ticks in the axis gutter (candle mode). */
        @SerialName("ladder")
        Ladder,

        /** Only the top/bottom price, overlaid over the full-width chart (line mode). */
        @SerialName("bounds")
        Bounds,
    }
}
