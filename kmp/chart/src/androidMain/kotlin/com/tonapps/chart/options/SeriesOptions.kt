package com.tonapps.chart.options

import kotlinx.serialization.Serializable

@Serializable
data class AreaSeriesOptions(
    val lineColor: ChartColor? = null,
    val topColor: ChartColor? = null,
    val bottomColor: ChartColor? = null,
    val lineWidth: Int? = null,
    val lineStyle: LineStyle? = null,
    val crosshairMarkerVisible: Boolean? = null,
    val priceFormat: PriceFormat? = null,
    val priceScaleId: String? = null,
    val priceLineVisible: Boolean? = null,
    val priceLineSource: PriceLineSource? = null,
    val lastValueVisible: Boolean? = null,
    val visible: Boolean? = null,
)

/** Options of the custom rounded-candlestick series from rounded_candlestick_series.js. */
@Serializable
data class RoundedCandlestickSeriesOptions(
    val upColor: ChartColor? = null,
    val downColor: ChartColor? = null,
    val wickVisible: Boolean? = null,
    val wickUpColor: ChartColor? = null,
    val wickDownColor: ChartColor? = null,
    val bodyMinHeight: Double? = null,
    val radius: Double? = null,
    val priceFormat: PriceFormat? = null,
    val priceScaleId: String? = null,
    val priceLineVisible: Boolean? = null,
    val priceLineSource: PriceLineSource? = null,
    val lastValueVisible: Boolean? = null,
    val visible: Boolean? = null,
)

/** Options of the custom rounded-histogram series from rounded_histogram_series.js. */
@Serializable
data class RoundedHistogramSeriesOptions(
    val color: ChartColor? = null,
    val base: Double? = null,
    val barMinHeight: Double? = null,
    val radius: Double? = null,
    val priceFormat: PriceFormat? = null,
    val priceScaleId: String? = null,
    val priceLineVisible: Boolean? = null,
    val lastValueVisible: Boolean? = null,
    val visible: Boolean? = null,
)
