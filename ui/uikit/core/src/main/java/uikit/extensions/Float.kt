package uikit.extensions

import kotlin.math.roundToInt

fun Float.range(start: Float, end: Float): Float {
    val value = start + (end - start) * this
    if (value.isNaN() || value.isInfinite()) {
        return 0f
    }
    return value
}

fun Float.range(start: Int, end: Int): Int {
    return range(start.toFloat(), end.toFloat()).roundToInt()
}