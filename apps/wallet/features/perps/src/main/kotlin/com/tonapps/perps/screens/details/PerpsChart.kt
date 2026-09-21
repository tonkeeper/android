package com.tonapps.perps.screens.details

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tonapps.chart.compose.LightweightChart
import com.tonapps.chart.options.ChartOptions
import com.tonapps.chart.options.CrosshairLineOptions
import com.tonapps.chart.options.CrosshairMode
import com.tonapps.chart.options.CrosshairOptions
import com.tonapps.chart.options.GridLineOptions
import com.tonapps.chart.options.GridOptions
import com.tonapps.chart.options.HandleScrollOptions
import com.tonapps.chart.options.TimeScaleOptions
import com.tonapps.chart.options.TrackingModeExitMode
import com.tonapps.chart.options.TrackingModeOptions
import com.tonapps.log.L
import com.tonapps.perps.data.PerpsCandle
import com.tonapps.perps.data.PerpsChartMode
import com.tonapps.perps.data.PerpsChartSectionState
import com.tonapps.perps.data.PerpsChartTimeframe
import com.tonapps.perps.data.PerpsPositionLevels
import com.tonapps.wallet.localization.Localization
import ui.components.moon.MoonLoader
import ui.preview.ThemedPreview
import ui.theme.UIKit
import kotlin.math.max
import kotlin.math.min

@Composable
internal fun PerpsChart(
    candles: List<PerpsCandle>,
    mode: PerpsChartMode,
    timeframe: PerpsChartTimeframe,
    positionLevels: PerpsPositionLevels?,
    priceDecimals: Int,
    sectionState: PerpsChartSectionState,
    isStale: Boolean,
    onCrosshairTime: (Long?) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = UIKit.colorScheme
    val palette = remember(scheme) {
        PerpsChartPalette(
            page = scheme.background.page,
            green = scheme.accent.green,
            red = scheme.accent.red,
            blue = scheme.accent.blue,
            purple = scheme.accent.purple,
            orange = scheme.accent.orange,
            textSecondary = scheme.text.secondary,
            separator = scheme.separator.common,
        )
    }
    // Not keyed on the palette: attach() runs once per view (AndroidView factory), so a swapped
    // controller would never re-attach; render() re-applies the palette in place instead.
    val controller = remember { PerpsChartController(palette) }
    val hiddenCrosshairLine = CrosshairLineOptions(visible = false, labelVisible = false)
    val hiddenGridLine = GridLineOptions(visible = false)

    DisposableEffect(controller) {
        onDispose { controller.detach() }
    }

    Box(modifier = modifier) {
        LightweightChart(
            modifier = Modifier.fillMaxSize(),
            preview = {
                PerpsChartSketch(
                    candles = candles,
                    mode = mode,
                    modifier = Modifier.fillMaxSize(),
                )
            },
            options = ChartOptions(
                layout = palette.layoutOptions(),
                timeScale = TimeScaleOptions(
                    rightOffset = 0.0,
                    barSpacing = 12.0,
                    borderVisible = false,
                    timeVisible = true,
                    fixRightEdge = true,
                ),
                crosshair = CrosshairOptions(
                    mode = CrosshairMode.Normal,
                    vertLine = hiddenCrosshairLine,
                    horzLine = hiddenCrosshairLine,
                ),
                grid = GridOptions(vertLines = hiddenGridLine, horzLines = hiddenGridLine),
                handleScroll = HandleScrollOptions(vertTouchDrag = false),
                trackingMode = TrackingModeOptions(exitMode = TrackingModeExitMode.OnTouchEnd),
            ),
            onCrosshairMove = { event -> onCrosshairTime(event?.time) },
            onCreated = { view ->
                view.onChartError = { message -> L.e(message) }
                controller.attach(view)
            },
        )

        SideEffect {
            controller.render(
                candles = candles,
                mode = mode,
                timeframe = timeframe,
                positionLevels = positionLevels,
                priceDecimals = priceDecimals,
                palette = palette,
            )
        }

        if (sectionState == PerpsChartSectionState.READY && isStale) {
            MoonLoader(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .size(16.dp),
            )
        }

        if (sectionState != PerpsChartSectionState.READY) {
            // Opaque cover: hides the leftover grid and axes but keeps the WebView alive.
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(palette.page),
                contentAlignment = Alignment.Center,
            ) {
                when (sectionState) {
                    PerpsChartSectionState.LOADING -> MoonLoader(modifier = Modifier.size(24.dp))

                    PerpsChartSectionState.EMPTY -> Text(
                        text = stringResource(Localization.perps_chart_empty),
                        style = UIKit.typography.body2,
                        color = UIKit.colorScheme.text.secondary,
                    )

                    PerpsChartSectionState.FAILED -> PerpsChartFailed(onRetry = onRetry)

                    PerpsChartSectionState.READY -> Unit
                }
            }
        }
    }
}

@Composable
private fun PerpsChartFailed(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        Text(
            text = stringResource(Localization.perps_chart_failed),
            style = UIKit.typography.body2,
            color = UIKit.colorScheme.text.secondary,
        )
        Box(
            modifier = Modifier
                .height(32.dp)
                .clip(CircleShape)
                .background(UIKit.colorScheme.buttonSecondary.primaryBackground)
                .clickable(onClick = onRetry)
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(Localization.perps_retry),
                style = UIKit.typography.label2,
                color = UIKit.colorScheme.buttonSecondary.primaryForeground,
            )
        }
    }
}

// Native sketch for Studio previews, where the WebView chart cannot run.
@Composable
private fun PerpsChartSketch(
    candles: List<PerpsCandle>,
    mode: PerpsChartMode,
    modifier: Modifier = Modifier,
) {
    if (candles.isEmpty()) {
        return
    }
    val green = UIKit.colorScheme.accent.green
    val red = UIKit.colorScheme.accent.red

    Canvas(modifier = modifier) {
        val plotWidth = size.width - PERPS_PRICE_AXIS_MIN_WIDTH.dp.toPx()
        val priceTop = size.height * 0.06f
        val priceBottom = size.height * 0.78f
        val volumeBottom = size.height * 0.98f
        val volumeTop = size.height * 0.84f
        val maxHigh = candles.maxOf { it.high }.toFloat()
        val minLow = candles.minOf { it.low }.toFloat()
        val priceRange = max(maxHigh - minLow, 1f)
        val maxVolume = max(candles.maxOf { it.volumeUsd ?: 0.0 }.toFloat(), 1f)
        val slot = plotWidth / candles.size
        val bodyWidth = slot * 0.62f
        val cornerRadius = CornerRadius(min(bodyWidth / 2f, 3.dp.toPx()))
        val minBodyHeight = 2.dp.toPx()

        fun priceY(price: Double): Float {
            return priceTop + (maxHigh - price.toFloat()) / priceRange * (priceBottom - priceTop)
        }

        if (mode == PerpsChartMode.LINE) {
            candles.forEachIndexed { index, candle ->
                val next = candles.getOrNull(index + 1) ?: return@forEachIndexed
                drawLine(
                    color = green,
                    start = Offset(slot * index + slot / 2f, priceY(candle.close)),
                    end = Offset(slot * (index + 1) + slot / 2f, priceY(next.close)),
                    strokeWidth = 2.dp.toPx(),
                )
            }
            return@Canvas
        }

        candles.forEachIndexed { index, candle ->
            val centerX = slot * index + slot / 2f
            val color = if (candle.close >= candle.open) {
                green
            } else {
                red
            }
            drawLine(
                color = color,
                start = Offset(centerX, priceY(candle.high)),
                end = Offset(centerX, priceY(candle.low)),
                strokeWidth = 1.5.dp.toPx(),
            )
            val bodyTop = priceY(max(candle.open, candle.close))
            val bodyHeight = max(priceY(min(candle.open, candle.close)) - bodyTop, minBodyHeight)
            drawRoundRect(
                color = color,
                topLeft = Offset(centerX - bodyWidth / 2f, bodyTop),
                size = Size(bodyWidth, bodyHeight),
                cornerRadius = cornerRadius,
            )
            val volumeHeight = (candle.volumeUsd ?: 0.0).toFloat() / maxVolume * (volumeBottom - volumeTop)
            drawRoundRect(
                color = PERPS_VOLUME_COLOR,
                topLeft = Offset(centerX - bodyWidth / 2f, volumeBottom - volumeHeight),
                size = Size(bodyWidth, volumeHeight),
                cornerRadius = cornerRadius,
            )
        }
    }
}

internal val previewCandles = listOf(
    PerpsCandle(1_751_446_800L, 64_820.0, 65_120.0, 64_610.0, 65_040.0, 4_100_000.0),
    PerpsCandle(1_751_450_400L, 65_040.0, 65_480.0, 64_980.0, 65_390.0, 5_240_000.0),
    PerpsCandle(1_751_454_000L, 65_390.0, 65_600.0, 65_060.0, 65_120.0, 3_180_000.0),
    PerpsCandle(1_751_457_600L, 65_120.0, 65_320.0, 64_740.0, 64_830.0, 6_010_000.0),
    PerpsCandle(1_751_461_200L, 64_830.0, 65_010.0, 64_320.0, 64_460.0, 4_780_000.0),
    PerpsCandle(1_751_464_800L, 64_460.0, 65_180.0, 64_400.0, 65_120.0, 7_320_000.0),
    PerpsCandle(1_751_468_400L, 65_120.0, 65_740.0, 65_080.0, 65_680.0, 5_900_000.0),
    PerpsCandle(1_751_472_000L, 65_680.0, 66_020.0, 65_540.0, 65_760.0, 4_460_000.0),
    PerpsCandle(1_751_475_600L, 65_760.0, 66_310.0, 65_700.0, 66_240.0, 6_640_000.0),
    PerpsCandle(1_751_479_200L, 66_240.0, 66_380.0, 65_910.0, 65_980.0, 3_920_000.0),
    PerpsCandle(1_751_482_800L, 65_980.0, 66_240.0, 65_820.0, 66_180.0, 4_310_000.0),
    PerpsCandle(1_751_486_400L, 66_180.0, 66_460.0, 66_020.0, 66_141.7, 5_120_000.0),
)

@Preview
@Composable
private fun PerpsChartPreview() {
    ThemedPreview(isDarkOnly = true) {
        PerpsChart(
            candles = previewCandles,
            mode = PerpsChartMode.CANDLE,
            timeframe = PerpsChartTimeframe.H1,
            positionLevels = null,
            priceDecimals = 2,
            sectionState = PerpsChartSectionState.READY,
            isStale = false,
            onCrosshairTime = {},
            onRetry = {},
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp),
        )
    }
}

@Preview
@Composable
private fun PerpsChartFailedPreview() {
    ThemedPreview(isDarkOnly = true) {
        PerpsChart(
            candles = emptyList(),
            mode = PerpsChartMode.CANDLE,
            timeframe = PerpsChartTimeframe.H1,
            positionLevels = null,
            priceDecimals = 2,
            sectionState = PerpsChartSectionState.FAILED,
            isStale = false,
            onCrosshairTime = {},
            onRetry = {},
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp),
        )
    }
}
