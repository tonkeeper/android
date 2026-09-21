package com.tonapps.chart

import com.tonapps.chart.data.AreaPoint
import com.tonapps.chart.data.CandlestickBar
import com.tonapps.chart.data.HistogramBar
import com.tonapps.chart.options.AreaSeriesOptions
import com.tonapps.chart.options.PriceLevelMarker
import com.tonapps.chart.options.PriceLevelMarkersOptions
import com.tonapps.chart.options.PriceScaleOptions
import com.tonapps.chart.options.RoundedCandlestickSeriesOptions
import com.tonapps.chart.options.RoundedHistogramSeriesOptions
import kotlinx.serialization.encodeToString

sealed class ChartSeries(
    protected val chart: LightweightChartView,
    val id: String,
) {

    /** Options of the price scale this series is attached to. */
    fun applyPriceScaleOptions(options: PriceScaleOptions) {
        chart.submit("window.AndroidChart.applySeriesPriceScaleOptions('$id', ${chart.json.encodeToString(options)});")
    }

    fun attachPriceLevelMarkers(options: PriceLevelMarkersOptions): PriceLevelMarkers {
        val pluginId = chart.nextPluginId()
        chart.submit(
            "window.AndroidChart.attachPriceLevelMarkers('$pluginId', '$id', ${chart.json.encodeToString(options)});"
        )
        return PriceLevelMarkers(chart, pluginId)
    }
}

class AreaSeries internal constructor(
    chart: LightweightChartView,
    id: String,
) : ChartSeries(chart, id) {

    fun applyOptions(options: AreaSeriesOptions) {
        chart.submit("window.AndroidChart.applySeriesOptions('$id', ${chart.json.encodeToString(options)});")
    }

    fun setData(data: List<AreaPoint>) {
        chart.submit("window.AndroidChart.setSeriesData('$id', ${chart.json.encodeToString(data)});")
    }

    fun update(point: AreaPoint) {
        chart.submit("window.AndroidChart.updateSeriesBar('$id', ${chart.json.encodeToString(point)});")
    }
}

class RoundedCandlestickSeries internal constructor(
    chart: LightweightChartView,
    id: String,
) : ChartSeries(chart, id) {

    fun applyOptions(options: RoundedCandlestickSeriesOptions) {
        chart.submit("window.AndroidChart.applySeriesOptions('$id', ${chart.json.encodeToString(options)});")
    }

    fun setData(data: List<CandlestickBar>) {
        chart.submit("window.AndroidChart.setSeriesData('$id', ${chart.json.encodeToString(data)});")
    }

    fun update(bar: CandlestickBar) {
        chart.submit("window.AndroidChart.updateSeriesBar('$id', ${chart.json.encodeToString(bar)});")
    }
}

class RoundedHistogramSeries internal constructor(
    chart: LightweightChartView,
    id: String,
) : ChartSeries(chart, id) {

    fun applyOptions(options: RoundedHistogramSeriesOptions) {
        chart.submit("window.AndroidChart.applySeriesOptions('$id', ${chart.json.encodeToString(options)});")
    }

    fun setData(data: List<HistogramBar>) {
        chart.submit("window.AndroidChart.setSeriesData('$id', ${chart.json.encodeToString(data)});")
    }

    fun update(bar: HistogramBar) {
        chart.submit("window.AndroidChart.updateSeriesBar('$id', ${chart.json.encodeToString(bar)});")
    }
}

/** Handle of an attached price-level markers plugin instance. */
class PriceLevelMarkers internal constructor(
    private val chart: LightweightChartView,
    private val pluginId: String,
) {

    fun setMarkers(markers: List<PriceLevelMarker>) {
        chart.submit("window.AndroidChart.setPriceLevelMarkers('$pluginId', ${chart.json.encodeToString(markers)});")
    }

    fun applyOptions(options: PriceLevelMarkersOptions) {
        chart.submit(
            "window.AndroidChart.applyPriceLevelMarkersOptions('$pluginId', ${chart.json.encodeToString(options)});"
        )
    }

    fun detach() {
        chart.submit("window.AndroidChart.detachPriceLevelMarkers('$pluginId');")
    }
}
