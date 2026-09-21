package com.tonapps.chart.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.viewinterop.AndroidView
import com.tonapps.chart.LightweightChartView
import com.tonapps.chart.data.CrosshairEvent
import com.tonapps.chart.options.ChartOptions

/**
 * Compose host of [LightweightChartView].
 *
 * [options] are applied once at creation; use the view handed to [onCreated]
 * to add series and drive later updates — calls made before the JS runtime is
 * ready are queued, so the series can be configured synchronously.
 *
 * [preview] is drawn instead of the chart in Studio previews, where WebView
 * cannot run — pass a lightweight native sketch of the expected content.
 */
@Composable
fun LightweightChart(
    modifier: Modifier = Modifier,
    options: ChartOptions = ChartOptions(),
    onCrosshairMove: ((CrosshairEvent?) -> Unit)? = null,
    preview: (@Composable BoxScope.() -> Unit)? = null,
    onCreated: (LightweightChartView) -> Unit = {},
) {
    // WebView cannot run in layoutlib. LocalInspectionMode alone is not
    // enough: interactive preview mode reports it as false, so also check the
    // host view's edit mode, which stays true for every layoutlib render.
    val isToolsRender = LocalInspectionMode.current || LocalView.current.isInEditMode
    if (isToolsRender) {
        Box(modifier = modifier) {
            preview?.invoke(this)
        }
        return
    }
    AndroidView(
        modifier = modifier,
        factory = { context ->
            LightweightChartView(context).also { view ->
                view.initialize(options)
                onCreated(view)
            }
        },
        update = { view ->
            view.onCrosshairMove = onCrosshairMove
        },
        onRelease = { view ->
            view.destroy()
        },
    )
}
