package com.tonapps.chart

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.annotation.RawRes
import com.tonapps.chart.data.CrosshairEvent
import com.tonapps.chart.data.CrosshairPoint
import com.tonapps.chart.options.AreaSeriesOptions
import com.tonapps.chart.options.ChartOptions
import com.tonapps.chart.options.PriceScaleOptions
import com.tonapps.chart.options.RoundedCandlestickSeriesOptions
import com.tonapps.chart.options.RoundedHistogramSeriesOptions
import com.tonapps.chart.options.TimeScaleOptions
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.abs

/**
 * WebView host of TradingView lightweight-charts and the custom plugins
 * (rounded candles/histogram, price-level markers) from the vendored JS bundle.
 *
 * All chart calls are queued until the JS runtime reports readiness, so series
 * can be added right after [initialize] without waiting for a callback.
 */
@SuppressLint("SetJavaScriptEnabled")
class LightweightChartView(context: Context) : FrameLayout(context) {

    /** Null when the crosshair leaves the chart or the finger is lifted. */
    var onCrosshairMove: ((CrosshairEvent?) -> Unit)? = null

    /** JS-side failures, reported as "where: message" — wire to logging. */
    var onChartError: ((String) -> Unit)? = null

    internal val json = Json {
        encodeDefaults = true
        explicitNulls = false
    }

    private val webView = WebView(context)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val pendingScripts = mutableListOf<String>()
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var initialized = false
    private var runtimeInjected = false
    private var isChartReady = false
    private var seriesCounter = 0
    private var pluginCounter = 0
    private var downX = 0f
    private var downY = 0f

    init {
        webView.settings.javaScriptEnabled = true
        webView.settings.allowFileAccess = false
        // The chart manages its own typography; system font scale must not
        // resize the canvas text out of sync with the native UI.
        webView.settings.textZoom = 100
        webView.settings.allowContentAccess = false
        webView.setBackgroundColor(Color.TRANSPARENT)
        webView.isHorizontalScrollBarEnabled = false
        webView.isVerticalScrollBarEnabled = false
        webView.overScrollMode = View.OVER_SCROLL_NEVER
        webView.addJavascriptInterface(BridgeInterface(), BRIDGE_NAME)
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                injectRuntime()
            }
        }
        addView(webView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    /** Loads the chart page and creates the JS chart. Safe to call once. */
    fun initialize(options: ChartOptions = ChartOptions()) {
        if (initialized) {
            return
        }
        initialized = true
        pendingScripts.add(0, "window.AndroidChart.init(${json.encodeToString(options)});")
        webView.loadDataWithBaseURL(BASE_URL, PAGE_HTML, "text/html", "utf-8", null)
    }

    fun applyChartOptions(options: ChartOptions) {
        submit("window.AndroidChart.applyChartOptions(${json.encodeToString(options)});")
    }

    fun addAreaSeries(options: AreaSeriesOptions = AreaSeriesOptions()): AreaSeries {
        val id = nextSeriesId()
        submit("window.AndroidChart.addSeries('$id', 'area', ${json.encodeToString(options)});")
        return AreaSeries(this, id)
    }

    fun addRoundedCandlestickSeries(
        options: RoundedCandlestickSeriesOptions = RoundedCandlestickSeriesOptions(),
    ): RoundedCandlestickSeries {
        val id = nextSeriesId()
        submit("window.AndroidChart.addSeries('$id', 'roundedCandlestick', ${json.encodeToString(options)});")
        return RoundedCandlestickSeries(this, id)
    }

    fun addRoundedHistogramSeries(
        options: RoundedHistogramSeriesOptions = RoundedHistogramSeriesOptions(),
    ): RoundedHistogramSeries {
        val id = nextSeriesId()
        submit("window.AndroidChart.addSeries('$id', 'roundedHistogram', ${json.encodeToString(options)});")
        return RoundedHistogramSeries(this, id)
    }

    fun applyPriceScaleOptions(scaleId: String, options: PriceScaleOptions) {
        submit("window.AndroidChart.applyPriceScaleOptions('$scaleId', ${json.encodeToString(options)});")
    }

    fun applyTimeScaleOptions(options: TimeScaleOptions) {
        submit("window.AndroidChart.applyTimeScaleOptions(${json.encodeToString(options)});")
    }

    fun resetTimeScale() {
        submit("window.AndroidChart.resetTimeScale();")
    }

    /** Releases the WebView; the view is unusable afterwards. */
    fun destroy() {
        onCrosshairMove = null
        onChartError = null
        pendingScripts.clear()
        removeView(webView)
        webView.destroy()
    }

    internal fun submit(script: String) {
        if (isChartReady) {
            webView.evaluateJavascript(script, null)
        } else {
            pendingScripts.add(script)
        }
    }

    internal fun nextPluginId(): String {
        val id = "plugin$pluginCounter"
        pluginCounter += 1
        return id
    }

    // A vertical drag belongs to the native scroll container; horizontal pan
    // and pinch stay on the chart.
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                parent?.requestDisallowInterceptTouchEvent(true)
            }

            MotionEvent.ACTION_POINTER_DOWN -> parent?.requestDisallowInterceptTouchEvent(true)

            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount == 1) {
                    val dx = abs(event.x - downX)
                    val dy = abs(event.y - downY)
                    if (dy > touchSlop && dy > dx) {
                        parent?.requestDisallowInterceptTouchEvent(false)
                    }
                }
            }
        }
        return super.dispatchTouchEvent(event)
    }

    private fun nextSeriesId(): String {
        val id = "series$seriesCounter"
        seriesCounter += 1
        return id
    }

    private fun injectRuntime() {
        if (runtimeInjected) {
            return
        }
        runtimeInjected = true
        evaluateRawScript(R.raw.lightweight_charts)
        evaluateRawScript(R.raw.rounded_candlestick_series)
        evaluateRawScript(R.raw.rounded_histogram_series)
        evaluateRawScript(R.raw.price_level_markers)
        evaluateRawScript(R.raw.chart_bridge)
        val initScript = pendingScripts.removeAt(0)
        webView.evaluateJavascript(initScript, null)
    }

    private fun evaluateRawScript(@RawRes resId: Int) {
        val script = resources.openRawResource(resId).bufferedReader().use { reader ->
            reader.readText()
        }
        webView.evaluateJavascript(script, null)
    }

    private fun handleBridgeMessage(payload: String) {
        val message = runCatching { json.parseToJsonElement(payload).jsonObject }.getOrNull() ?: return
        when (message["type"]?.jsonPrimitive?.content) {
            "ready" -> {
                isChartReady = true
                val scripts = pendingScripts.toList()
                pendingScripts.clear()
                scripts.forEach { script ->
                    webView.evaluateJavascript(script, null)
                }
            }

            "crosshairMove" -> dispatchCrosshairMove(message)

            "layout" -> Log.d(LOG_TAG, "layout: $payload")

            "error" -> {
                val where = message["where"]?.jsonPrimitive?.content
                val description = message["message"]?.jsonPrimitive?.content
                Log.w(LOG_TAG, "js error in $where: $description")
                onChartError?.invoke("$where: $description")
            }
        }
    }

    private fun dispatchCrosshairMove(message: JsonObject) {
        val listener = onCrosshairMove ?: return
        val time = (message["time"] as? JsonPrimitive)?.doubleOrNull?.toLong()
        val point = message["point"] as? JsonObject
        if (time == null || point == null) {
            listener(null)
            return
        }
        val x = point["x"]?.jsonPrimitive?.doubleOrNull ?: return
        val y = point["y"]?.jsonPrimitive?.doubleOrNull ?: return
        val seriesData = message["seriesData"] as? JsonObject
        listener(
            CrosshairEvent(
                time = time,
                point = CrosshairPoint(x = x.toFloat(), y = y.toFloat()),
                seriesData = seriesData ?: JsonObject(emptyMap()),
            )
        )
    }

    private inner class BridgeInterface {

        @JavascriptInterface
        fun postMessage(payload: String) {
            mainHandler.post {
                handleBridgeMessage(payload)
            }
        }
    }

    private companion object {
        const val LOG_TAG = "LightweightChart"
        const val BRIDGE_NAME = "AndroidChartBridge"
        const val BASE_URL = "https://lightweight-charts.local/"
        val PAGE_HTML = """
            <!DOCTYPE html>
            <html>
            <head>
            <meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, minimum-scale=1.0, user-scalable=no">
            <style>html, body { margin: 0; padding: 0; width: 100%; height: 100%; background: transparent; overflow: hidden; }</style>
            </head>
            <body></body>
            </html>
        """.trimIndent()
    }
}
