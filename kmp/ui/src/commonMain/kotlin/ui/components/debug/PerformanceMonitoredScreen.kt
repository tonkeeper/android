package ui.components.debug

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.TimeSource
import kotlin.time.TimeSource.Monotonic.ValueTimeMark

@Composable
fun PerformanceMonitoredScreen(
    content: @Composable () -> Unit
) {
    val startTime = remember { TimeSource.Monotonic.markNow() }
    val performanceTracker = remember { PerformanceTracker() }

    CompositionLocalProvider(LocalPerformanceTracker provides performanceTracker) {
        content()
    }

    DisposableEffect(Unit) {
        performanceTracker.startMonitoring()
        onDispose {
            performanceTracker.stopMonitoring()
            val compositionTime = startTime.elapsedNow()
            println("PerformanceMonitor: Composition time: ${compositionTime.toString(DurationUnit.MILLISECONDS)}")
        }
    }
}

val LocalPerformanceTracker = compositionLocalOf { PerformanceTracker() }
class PerformanceTracker {
    private var startTime: ValueTimeMark = TimeSource.Monotonic.markNow()
    private val metrics = mutableMapOf<String, Duration>()

    fun startMonitoring() {
        startTime = TimeSource.Monotonic.markNow()
    }

    fun stopMonitoring() {
        metrics["totalTime"] = startTime.elapsedNow()

        logMetrics()
    }

    fun trackImageLoad(imageUrl: String, loadTime: Duration) {
        metrics["image_$imageUrl"] = loadTime
    }

    private fun logMetrics() {
        metrics.forEach { (key, value) ->
            println("PerformanceMonitor: $key: ${value.toString(DurationUnit.MILLISECONDS)}")
        }
    }
}