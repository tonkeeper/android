package com.tonapps.perps.screens.details

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import com.tonapps.chart.options.AreaSeriesOptions
import com.tonapps.chart.options.BackgroundOptions
import com.tonapps.chart.options.ChartColor
import com.tonapps.chart.options.LayoutOptions
import com.tonapps.chart.options.PriceFormat
import com.tonapps.chart.options.PriceLevelMarkersOptions
import com.tonapps.chart.options.PriceLineSource
import com.tonapps.chart.options.PriceScaleMargins
import com.tonapps.chart.options.PriceScaleOptions
import com.tonapps.chart.options.RoundedCandlestickSeriesOptions
import com.tonapps.chart.options.RoundedHistogramSeriesOptions
import com.tonapps.perps.data.PerpsChartMode
import kotlin.math.pow

internal data class PerpsChartPalette(
    val page: Color,
    val green: Color,
    val red: Color,
    val blue: Color,
    val purple: Color,
    val orange: Color,
    val textSecondary: Color,
    val separator: Color,
)

internal fun PerpsChartPalette.labelBackground(color: Color): Color {
    return color.copy(alpha = LABEL_BACKGROUND_ALPHA).compositeOver(page)
}

internal fun PerpsChartPalette.layoutOptions(): LayoutOptions {
    return LayoutOptions(
        background = BackgroundOptions(color = ChartColor(page)),
        textColor = ChartColor(textSecondary),
        fontSize = 12,
        fontFamily = "ui-monospace, monospace",
        attributionLogo = false,
    )
}

internal fun PerpsChartPalette.candlestickOptions(
    priceDecimals: Int,
): RoundedCandlestickSeriesOptions {
    return RoundedCandlestickSeriesOptions(
        upColor = ChartColor(green),
        downColor = ChartColor(red),
        wickVisible = true,
        wickUpColor = ChartColor(green),
        wickDownColor = ChartColor(red),
        bodyMinHeight = 2.0,
        radius = 3.0,
        priceFormat = priceFormat(priceDecimals),
        priceLineVisible = false,
        lastValueVisible = false,
        priceLineSource = PriceLineSource.LastBar,
    )
}

internal fun volumeOptions(): RoundedHistogramSeriesOptions {
    return RoundedHistogramSeriesOptions(
        color = ChartColor(PERPS_VOLUME_COLOR),
        priceScaleId = VOLUME_SCALE_ID,
        priceFormat = PriceFormat.volume(),
        lastValueVisible = false,
        priceLineVisible = false,
        barMinHeight = 3.0,
        radius = 3.0,
    )
}

internal fun volumeScaleOptions(): PriceScaleOptions {
    return PriceScaleOptions(scaleMargins = PriceScaleMargins(top = PERPS_VOLUME_SCALE_TOP, bottom = 0.02))
}

internal fun PerpsChartPalette.areaOptions(priceDecimals: Int, rising: Boolean): AreaSeriesOptions {
    val color = if (rising) {
        green
    } else {
        red
    }
    return AreaSeriesOptions(
        lineColor = ChartColor(color),
        topColor = ChartColor(color.copy(alpha = AREA_TOP_ALPHA)),
        bottomColor = ChartColor(color.copy(alpha = 0f)),
        lineWidth = 2,
        priceFormat = priceFormat(priceDecimals),
        priceLineVisible = false,
        lastValueVisible = false,
    )
}

internal fun PerpsChartPalette.rightPriceScaleOptions(): PriceScaleOptions {
    return PriceScaleOptions(
        visible = true,
        borderVisible = false,
        // The markers plugin draws its own axis labels; the scale only reserves width for them.
        textColor = ChartColor(textSecondary.copy(alpha = 0f)),
        ticksVisible = false,
        minimumWidth = PERPS_PRICE_AXIS_MIN_WIDTH,
        scaleMargins = PriceScaleMargins(top = 0.125, bottom = 0.208),
    )
}

internal fun PerpsChartPalette.markersOptions(priceDecimals: Int, mode: PerpsChartMode): PriceLevelMarkersOptions {
    return PriceLevelMarkersOptions(
        priceDecimals = priceDecimals,
        currentPriceUpLabelColor = ChartColor(green),
        currentPriceUpLabelBackgroundColor = ChartColor(labelBackground(green)),
        currentPriceDownLabelColor = ChartColor(red),
        currentPriceDownLabelBackgroundColor = ChartColor(labelBackground(red)),
        axisLabelColor = ChartColor(textSecondary),
        dotHaloColor = ChartColor(Color.White.copy(alpha = 0.16f)),
        dotColor = ChartColor(Color.White),
        crosshairColor = ChartColor(Color.White),
        crosshairLabelColor = ChartColor(Color.White),
        crosshairLabelBackgroundColor = ChartColor(labelBackground(Color.White)),
        gridColor = ChartColor(separator),
        crosshairDotOnSeries = mode == PerpsChartMode.LINE,
        pinnedTagsBottomY = if (mode == PerpsChartMode.CANDLE) {
            PERPS_VOLUME_SCALE_TOP
        } else {
            null
        },
    )
}

internal fun priceFormat(priceDecimals: Int): PriceFormat {
    val minMove = if (priceDecimals > 0) {
        10.0.pow(-priceDecimals)
    } else {
        1.0
    }
    return PriceFormat.price(precision = priceDecimals, minMove = minMove)
}

internal const val PERPS_PRICE_AXIS_MIN_WIDTH = 88

internal const val PERPS_VOLUME_SCALE_TOP = 0.86

internal val PERPS_VOLUME_COLOR = Color(0xFF2E3847)

private const val VOLUME_SCALE_ID = "volume"
private const val LABEL_BACKGROUND_ALPHA = 0.16f
private const val AREA_TOP_ALPHA = 0.32f
