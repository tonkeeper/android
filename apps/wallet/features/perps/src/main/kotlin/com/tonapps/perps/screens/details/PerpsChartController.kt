package com.tonapps.perps.screens.details

import androidx.compose.ui.graphics.Color
import com.tonapps.chart.AreaSeries
import com.tonapps.chart.LightweightChartView
import com.tonapps.chart.PriceLevelMarkers
import com.tonapps.chart.RoundedCandlestickSeries
import com.tonapps.chart.RoundedHistogramSeries
import com.tonapps.chart.data.AreaPoint
import com.tonapps.chart.data.CandlestickBar
import com.tonapps.chart.data.CrosshairEvent
import com.tonapps.chart.data.HistogramBar
import com.tonapps.chart.options.AreaSeriesOptions
import com.tonapps.chart.options.ChartColor
import com.tonapps.chart.options.ChartOptions
import com.tonapps.chart.options.PriceLevelMarker
import com.tonapps.chart.options.RoundedCandlestickSeriesOptions
import com.tonapps.chart.options.TimeScaleOptions
import com.tonapps.perps.data.PerpsCandle
import com.tonapps.perps.data.PerpsChartMode
import com.tonapps.perps.data.PerpsChartPoint
import com.tonapps.perps.data.PerpsChartTimeframe
import com.tonapps.perps.data.PerpsPositionLevels
import com.tonapps.perps.data.bidiIsolate
import com.tonapps.perps.data.perpsChartZoneOffsetSeconds

internal class PerpsChartController(
    private var palette: PerpsChartPalette,
    private val entryLabel: String,
    private val liquidationLabel: String,
) {

    var onReachedLeftEdge: () -> Unit = {}
    var onCandleChanged: () -> Unit = {}

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
    private var renderedLevels: PerpsPositionLevels? = null
    private var priceDecimals = DEFAULT_PRICE_DECIMALS
    private var lineRising = true
    private var zoneOffsetSeconds = perpsChartZoneOffsetSeconds()
    private var lastRange: Pair<Double, Double>? = null
    private var leftmostIndex = 0
    private var rightmostIndex: Int? = null
    private var olderLoadArmed = true
    private var crosshairTime: Long? = null

    fun attach(view: LightweightChartView) {
        chart = view
        candleSeries = view.addRoundedCandlestickSeries(palette.candlestickOptions(priceDecimals))
        volumeSeries = view.addRoundedHistogramSeries(volumeOptions()).also { series ->
            series.applyPriceScaleOptions(volumeScaleOptions())
        }
        areaSeries = view.addAreaSeries(palette.areaOptions(priceDecimals, lineRising))
        applyAxes(view)
        view.onVisibleLogicalRangeChange = ::onVisibleLogicalRangeChange
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

        val offsetSeconds = perpsChartZoneOffsetSeconds()
        if (offsetSeconds != zoneOffsetSeconds) {
            zoneOffsetSeconds = offsetSeconds
            renderedCandles = emptyList()
            renderedTimeframe = null
        }

        if (palette != this.palette) {
            this.palette = palette
            chart.applyChartOptions(ChartOptions(layout = palette.layoutOptions()))
            candleSeries?.applyOptions(palette.candlestickOptions(priceDecimals))
            areaSeries?.applyOptions(palette.areaOptions(priceDecimals, lineRising))
            markersPlugin?.applyOptions(palette.markersOptions(priceDecimals, activeMarkersMode ?: mode))
            renderedMarkers = null
        }

        if (priceDecimals != this.priceDecimals) {
            this.priceDecimals = priceDecimals
            val format = priceFormat(priceDecimals)
            candleSeries?.applyOptions(RoundedCandlestickSeriesOptions(priceFormat = format))
            areaSeries?.applyOptions(AreaSeriesOptions(priceFormat = format))
            markersPlugin?.applyOptions(palette.markersOptions(priceDecimals, activeMarkersMode ?: mode))
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
            renderedMode = mode
            renderedCandles = emptyList()
            renderedMarkers = null
        }

        if (candles != renderedCandles) {
            olderLoadArmed = true
            if (matchesAt(renderedCandles, candles, offset = 0)) {
                applyTail(candles, mode, from = renderedCandles.size - 1)
                if (mode == PerpsChartMode.CANDLE &&
                    (0 until renderedCandles.size - 1).any { renderedCandles[it].volumeUsd != candles[it].volumeUsd }
                ) {
                    volumeSeries?.setData(candles.mapNotNull { it.toVolumeBar() })
                }
            } else {
                val oldestTime = renderedCandles.firstOrNull()?.time
                val prepended = candles.indexOfFirst { it.time == oldestTime }
                val range = lastRange
                applyFull(candles, mode)
                if (prepended > 0 && range != null && timeframe == renderedTimeframe &&
                    matchesAt(renderedCandles, candles, prepended)
                ) {
                    val shifted = (range.first + prepended) to (range.second + prepended)
                    lastRange = shifted
                    leftmostIndex += prepended
                    rightmostIndex = rightmostIndex?.plus(prepended)
                    chart.setVisibleLogicalRange(shifted.first, shifted.second)
                }
            }
            renderedCandles = candles
        }

        renderedLevels = positionLevels
        refreshLineRising()
        syncMarkers(mode)

        // The loading state pushes an empty frame between timeframes — reset only with bars in.
        if (candles.isNotEmpty() && timeframe != renderedTimeframe) {
            chart.resetTimeScale()
            renderedTimeframe = timeframe
        }
    }

    fun toUtcSeconds(chartTime: Long): Long = chartTime - zoneOffsetSeconds

    fun pointAt(event: CrosshairEvent?): PerpsChartPoint? {
        val time = event?.time?.let(::toUtcSeconds)
        if (time != null && time != crosshairTime) {
            onCandleChanged()
        }
        crosshairTime = time
        if (time == null) {
            return null
        }
        val index = event.leftmostIndex ?: 0
        val base = renderedCandles.getOrNull(index.coerceAtLeast(0)) ?: renderedCandles.lastOrNull()
        return PerpsChartPoint(timeSec = time, baseTimeSec = base?.time ?: time)
    }

    private fun onVisibleLogicalRangeChange(from: Double, to: Double, leftmostIndex: Int, rightmostIndex: Int) {
        lastRange = from to to
        this.leftmostIndex = leftmostIndex
        this.rightmostIndex = rightmostIndex
        refreshLineRising()
        if (renderedMode == PerpsChartMode.LINE) {
            syncMarkers(PerpsChartMode.LINE)
        }
        if (from >= LOAD_OLDER_THRESHOLD_BARS) {
            olderLoadArmed = true
        } else if (olderLoadArmed) {
            olderLoadArmed = false
            onReachedLeftEdge()
        }
    }

    private fun refreshLineRising() {
        val candles = renderedCandles
        if (candles.isEmpty()) {
            return
        }
        val leftmost = leftmostIndex.coerceIn(0, candles.lastIndex)
        val rightmost = rightmostIndex?.coerceIn(leftmost, candles.lastIndex) ?: candles.lastIndex
        val rising = candles[rightmost].close >= candles[leftmost].close
        if (rising != lineRising) {
            lineRising = rising
            areaSeries?.applyOptions(palette.areaOptions(priceDecimals, rising))
        }
    }

    private fun syncMarkers(mode: PerpsChartMode) {
        val markers = positionMarkers(renderedLevels, renderedCandles.lastOrNull(), mode)
        if (markers != renderedMarkers) {
            ensureMarkersPlugin(mode)
            markersPlugin?.setMarkers(markers)
            renderedMarkers = markers
        }
    }

    private fun resetRenderState() {
        lastRange = null
        leftmostIndex = 0
        rightmostIndex = null
        olderLoadArmed = true
        crosshairTime = null
        markersPlugin = null
        activeMarkersMode = null
        renderedCandles = emptyList()
        renderedMode = null
        renderedTimeframe = null
        renderedMarkers = null
        renderedLevels = null
    }

    private fun applyAxes(chart: LightweightChartView) {
        chart.applyPriceScaleOptions(RIGHT_SCALE_ID, palette.rightPriceScaleOptions())
        chart.applyTimeScaleOptions(
            TimeScaleOptions(
                rightOffset = 0.0,
                fixLeftEdge = true,
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
        markersPlugin = series.attachPriceLevelMarkers(palette.markersOptions(priceDecimals, mode))
        activeMarkersMode = mode
    }

    private fun applyFull(candles: List<PerpsCandle>, mode: PerpsChartMode) {
        when (mode) {
            PerpsChartMode.CANDLE -> {
                candleSeries?.setData(candles.map { it.toBar() })
                volumeSeries?.setData(candles.mapNotNull { it.toVolumeBar() })
            }

            PerpsChartMode.LINE -> areaSeries?.setData(
                candles.map { AreaPoint(time = it.time + zoneOffsetSeconds, value = it.close) }
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
                    AreaPoint(time = candle.time + zoneOffsetSeconds, value = candle.close)
                )
            }
        }
    }

    private fun matchesAt(old: List<PerpsCandle>, new: List<PerpsCandle>, offset: Int): Boolean {
        if (old.isEmpty() || new.size < offset + old.size) {
            return false
        }
        for (index in 0 until old.size - 1) {
            if (old[index].copy(volumeUsd = null) != new[offset + index].copy(volumeUsd = null)) {
                return false
            }
        }
        return old.last().time == new[offset + old.size - 1].time
    }

    private fun positionMarkers(
        levels: PerpsPositionLevels?,
        lastCandle: PerpsCandle?,
        mode: PerpsChartMode,
    ): List<PriceLevelMarker> {
        val markers = mutableListOf<PriceLevelMarker>()
        if (levels != null) {
            markers.addLevel("takeProfit", levels.takeProfit, "TP", palette.blue)
            markers.addLevel("entry", levels.entry, entryLabel, palette.purple)
            markers.addLevel("stopLoss", levels.stopLoss, "SL", palette.orange)
            markers.addLevel("liquidation", levels.liquidation, liquidationLabel, palette.red)
        }
        if (lastCandle != null) {
            val isUp = when (mode) {
                PerpsChartMode.CANDLE -> lastCandle.close >= lastCandle.open
                PerpsChartMode.LINE -> lineRising
            }
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
                title = bidiIsolate(title),
                color = ChartColor(color),
                lineColor = ChartColor(color),
                backgroundColor = ChartColor(palette.labelBackground(color)),
                align = PriceLevelMarker.Alignment.Left,
                lineVisible = true,
            )
        )
    }

    private fun PerpsCandle.toBar(): CandlestickBar {
        return CandlestickBar(
            time = time + zoneOffsetSeconds,
            open = open,
            high = high,
            low = low,
            close = close,
        )
    }

    private fun PerpsCandle.toVolumeBar(): HistogramBar? {
        val volume = volumeUsd ?: return null
        return HistogramBar(time = time + zoneOffsetSeconds, value = volume)
    }

    private companion object {
        const val DEFAULT_PRICE_DECIMALS = 2
        const val RIGHT_SCALE_ID = "right"
        const val LOAD_OLDER_THRESHOLD_BARS = 10.0
    }
}
