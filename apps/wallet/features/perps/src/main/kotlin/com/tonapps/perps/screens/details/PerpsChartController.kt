package com.tonapps.perps.screens.details

import androidx.compose.ui.graphics.Color
import com.tonapps.chart.AreaSeries
import com.tonapps.chart.LightweightChartView
import com.tonapps.chart.PriceLevelMarkers
import com.tonapps.chart.RoundedCandlestickSeries
import com.tonapps.chart.RoundedHistogramSeries
import com.tonapps.chart.data.AreaPoint
import com.tonapps.chart.data.CandlestickBar
import com.tonapps.chart.data.HistogramBar
import com.tonapps.chart.options.AreaSeriesOptions
import com.tonapps.chart.options.ChartColor
import com.tonapps.chart.options.ChartOptions
import com.tonapps.chart.options.PriceLevelMarker
import com.tonapps.chart.options.RoundedCandlestickSeriesOptions
import com.tonapps.chart.options.TimeScaleOptions
import com.tonapps.perps.data.PerpsCandle
import com.tonapps.perps.data.PerpsChartMode
import com.tonapps.perps.data.PerpsChartTimeframe
import com.tonapps.perps.data.PerpsPositionLevels

internal class PerpsChartController(private var palette: PerpsChartPalette) {

    private var chart: LightweightChartView? = null
    private var candleSeries: RoundedCandlestickSeries? = null
    private var volumeSeries: RoundedHistogramSeries? = null
    private var areaSeries: AreaSeries? = null
    private var markersPlugin: PriceLevelMarkers? = null
    private var activeMarkersMode: PerpsChartMode? = null

    private var renderedCandles: List<PerpsCandle> = emptyList()
    private var renderedMode: PerpsChartMode? = null
    private var renderedTimeframe: PerpsChartTimeframe? = null
    private var renderedMarkers: List<PriceLevelMarker>? = null
    private var priceDecimals = DEFAULT_PRICE_DECIMALS

    fun attach(view: LightweightChartView) {
        chart = view
        candleSeries = view.addRoundedCandlestickSeries(palette.candlestickOptions(priceDecimals))
        volumeSeries = view.addRoundedHistogramSeries(volumeOptions()).also { series ->
            series.applyPriceScaleOptions(volumeScaleOptions())
        }
        areaSeries = view.addAreaSeries(palette.areaOptions(priceDecimals))
        resetRenderState()
    }

    fun detach() {
        markersPlugin?.detach()
        chart = null
        candleSeries = null
        volumeSeries = null
        areaSeries = null
        resetRenderState()
    }

    fun render(
        candles: List<PerpsCandle>,
        mode: PerpsChartMode,
        timeframe: PerpsChartTimeframe,
        positionLevels: PerpsPositionLevels?,
        priceDecimals: Int,
        palette: PerpsChartPalette,
    ) {
        val chart = chart ?: return

        if (palette != this.palette) {
            this.palette = palette
            chart.applyChartOptions(ChartOptions(layout = palette.layoutOptions()))
            candleSeries?.applyOptions(palette.candlestickOptions(priceDecimals))
            areaSeries?.applyOptions(palette.areaOptions(priceDecimals))
            activeMarkersMode?.let { markersMode ->
                markersPlugin?.applyOptions(palette.markersOptions(markersMode, priceDecimals))
            }
            renderedMarkers = null
        }

        if (priceDecimals != this.priceDecimals) {
            this.priceDecimals = priceDecimals
            val format = priceFormat(priceDecimals)
            candleSeries?.applyOptions(RoundedCandlestickSeriesOptions(priceFormat = format))
            areaSeries?.applyOptions(AreaSeriesOptions(priceFormat = format))
            markersPlugin?.applyOptions(palette.markersOptions(mode, priceDecimals))
            renderedMarkers = null
        }

        if (mode != renderedMode) {
            when (mode) {
                PerpsChartMode.CANDLE -> areaSeries?.setData(emptyList())
                PerpsChartMode.LINE -> {
                    candleSeries?.setData(emptyList())
                    volumeSeries?.setData(emptyList())
                }
            }
            applyAxes(chart, mode)
            renderedMode = mode
            renderedCandles = emptyList()
            renderedMarkers = null
        }

        val markers = if (mode == PerpsChartMode.CANDLE) {
            positionMarkers(positionLevels, candles.lastOrNull())
        } else {
            emptyList()
        }
        if (markers != renderedMarkers) {
            ensureMarkersPlugin(mode)
            markersPlugin?.setMarkers(markers)
            renderedMarkers = markers
        }

        if (candles != renderedCandles) {
            if (isTailExtension(renderedCandles, candles)) {
                applyTail(candles, mode, from = renderedCandles.size - 1)
            } else {
                applyFull(candles, mode)
            }
            renderedCandles = candles
        }

        // The loading state pushes an empty frame between timeframes — reset only with bars in.
        if (candles.isNotEmpty() && timeframe != renderedTimeframe) {
            chart.resetTimeScale()
            renderedTimeframe = timeframe
        }
    }

    private fun resetRenderState() {
        markersPlugin = null
        activeMarkersMode = null
        renderedCandles = emptyList()
        renderedMode = null
        renderedTimeframe = null
        renderedMarkers = null
    }

    private fun applyAxes(chart: LightweightChartView, mode: PerpsChartMode) {
        chart.applyPriceScaleOptions(RIGHT_SCALE_ID, palette.rightPriceScaleOptions(mode))
        chart.applyTimeScaleOptions(
            TimeScaleOptions(
                rightOffset = 0.0,
                fixLeftEdge = mode == PerpsChartMode.LINE,
                fixRightEdge = true,
            )
        )
    }

    private fun ensureMarkersPlugin(mode: PerpsChartMode) {
        if (activeMarkersMode == mode) {
            return
        }
        markersPlugin?.detach()
        val series = when (mode) {
            PerpsChartMode.CANDLE -> candleSeries
            PerpsChartMode.LINE -> areaSeries
        } ?: return
        markersPlugin = series.attachPriceLevelMarkers(palette.markersOptions(mode, priceDecimals))
        activeMarkersMode = mode
    }

    private fun applyFull(candles: List<PerpsCandle>, mode: PerpsChartMode) {
        when (mode) {
            PerpsChartMode.CANDLE -> {
                candleSeries?.setData(candles.map { it.toBar() })
                volumeSeries?.setData(candles.mapNotNull { it.toVolumeBar() })
            }

            PerpsChartMode.LINE -> areaSeries?.setData(
                candles.map { AreaPoint(time = it.time, value = it.close) }
            )
        }
    }

    private fun applyTail(candles: List<PerpsCandle>, mode: PerpsChartMode, from: Int) {
        for (index in from.coerceAtLeast(0) until candles.size) {
            val candle = candles[index]
            when (mode) {
                PerpsChartMode.CANDLE -> {
                    candleSeries?.update(candle.toBar())
                    candle.toVolumeBar()?.let { volumeSeries?.update(it) }
                }

                PerpsChartMode.LINE -> areaSeries?.update(
                    AreaPoint(time = candle.time, value = candle.close)
                )
            }
        }
    }

    private fun isTailExtension(old: List<PerpsCandle>, new: List<PerpsCandle>): Boolean {
        if (old.isEmpty() || new.size < old.size) {
            return false
        }
        for (index in 0 until old.size - 1) {
            if (old[index] != new[index]) {
                return false
            }
        }
        return old.last().time == new[old.size - 1].time
    }

    private fun positionMarkers(
        levels: PerpsPositionLevels?,
        lastCandle: PerpsCandle?,
    ): List<PriceLevelMarker> {
        val markers = mutableListOf<PriceLevelMarker>()
        if (levels != null) {
            markers.addLevel("takeProfit", levels.takeProfit, "TP", palette.blue)
            markers.addLevel("entry", levels.entry, "Entry", palette.purple)
            markers.addLevel("stopLoss", levels.stopLoss, "SL", palette.orange)
            markers.addLevel("liquidation", levels.liquidation, "Liq", palette.red)
        }
        if (lastCandle != null) {
            val isUp = lastCandle.close >= lastCandle.open
            val color = if (isUp) {
                palette.green
            } else {
                palette.red
            }
            val direction = if (isUp) {
                PriceLevelMarker.Direction.Up
            } else {
                PriceLevelMarker.Direction.Down
            }
            markers.add(
                PriceLevelMarker(
                    id = "currentPrice",
                    price = lastCandle.close,
                    title = null,
                    color = ChartColor(color),
                    lineColor = ChartColor(color),
                    backgroundColor = ChartColor(palette.labelBackground(color)),
                    align = PriceLevelMarker.Alignment.Right,
                    lineVisible = true,
                    direction = direction,
                    isCurrentPrice = true,
                )
            )
        }
        return markers
    }

    private fun MutableList<PriceLevelMarker>.addLevel(
        id: String,
        price: Double?,
        title: String,
        color: Color,
    ) {
        if (price == null) {
            return
        }
        add(
            PriceLevelMarker(
                id = id,
                price = price,
                title = title,
                color = ChartColor(color),
                lineColor = ChartColor(color),
                backgroundColor = ChartColor(palette.labelBackground(color)),
                align = PriceLevelMarker.Alignment.Left,
                lineVisible = true,
            )
        )
    }

    private fun PerpsCandle.toBar(): CandlestickBar {
        return CandlestickBar(time = time, open = open, high = high, low = low, close = close)
    }

    private fun PerpsCandle.toVolumeBar(): HistogramBar? {
        val volume = volumeUsd ?: return null
        return HistogramBar(time = time, value = volume)
    }

    private companion object {
        const val DEFAULT_PRICE_DECIMALS = 2
        const val RIGHT_SCALE_ID = "right"
    }
}
