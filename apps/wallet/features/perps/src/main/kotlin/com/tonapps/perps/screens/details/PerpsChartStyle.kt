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
    return PriceScaleOptions(scaleMargins = PriceScaleMargins(top = 0.86, bottom = 0.02))
}

internal fun PerpsChartPalette.areaOptions(priceDecimals: Int): AreaSeriesOptions {
    return AreaSeriesOptions(
        lineColor = ChartColor(green),
        topColor = ChartColor(green.copy(alpha = AREA_TOP_ALPHA)),
        bottomColor = ChartColor(green.copy(alpha = 0f)),
        lineWidth = 2,
        priceFormat = priceFormat(priceDecimals),
        priceLineVisible = false,
        lastValueVisible = false,
    )
}

internal fun PerpsChartPalette.rightPriceScaleOptions(mode: PerpsChartMode): PriceScaleOptions {
    val isLine = mode == PerpsChartMode.LINE
    return PriceScaleOptions(
        // A zero-width visible scale still reserves label room, so line mode hides it outright.
        visible = !isLine,
        borderVisible = false,
        // The markers plugin draws its own axis labels; the scale only reserves width for them.
        textColor = ChartColor(textSecondary.copy(alpha = 0f)),
        ticksVisible = false,
        minimumWidth = if (isLine) {
            0
        } else {
            PERPS_PRICE_AXIS_MIN_WIDTH
        },
        scaleMargins = PriceScaleMargins(top = 0.1, bottom = 0.22),
    )
}

internal fun PerpsChartPalette.markersOptions(
    mode: PerpsChartMode,
    priceDecimals: Int,
): PriceLevelMarkersOptions {
    val isLine = mode == PerpsChartMode.LINE
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
        axisLabelMode = if (isLine) {
            PriceLevelMarkersOptions.AxisLabelMode.Bounds
        } else {
            PriceLevelMarkersOptions.AxisLabelMode.Ladder
        },
        crosshairHorizontalVisible = !isLine,
        crosshairLabelVisible = !isLine,
        crosshairDotOnSeries = isLine,
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

internal val PERPS_VOLUME_COLOR = Color(0xFF2E3847)

private const val VOLUME_SCALE_ID = "volume"
private const val LABEL_BACKGROUND_ALPHA = 0.16f
private const val AREA_TOP_ALPHA = 0.32f
