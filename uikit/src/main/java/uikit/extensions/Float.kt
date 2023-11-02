package uikit.extensions

import kotlin.math.roundToInt

fun Float.range(start: Float, end: Float): Float {
    return start + (end - start) * this
}

fun Float.range(start: Int, end: Int): Int {
    return ((start + (end - start) * this)).roundToInt()
}