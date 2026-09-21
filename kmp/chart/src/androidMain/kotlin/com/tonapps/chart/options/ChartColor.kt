package com.tonapps.chart.options

import androidx.compose.ui.graphics.Color
import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

/**
 * CSS color string as understood by lightweight-charts: "#RRGGBB",
 * "rgba(r, g, b, a)" and friends.
 */
@Serializable
@JvmInline
value class ChartColor(val value: String)

fun ChartColor(color: Color): ChartColor {
    val red = (color.red * COLOR_MAX).roundToInt()
    val green = (color.green * COLOR_MAX).roundToInt()
    val blue = (color.blue * COLOR_MAX).roundToInt()
    val alpha = (color.alpha * ALPHA_PRECISION).roundToInt() / ALPHA_PRECISION
    return ChartColor("rgba($red, $green, $blue, $alpha)")
}

private const val COLOR_MAX = 255f
private const val ALPHA_PRECISION = 1000.0
