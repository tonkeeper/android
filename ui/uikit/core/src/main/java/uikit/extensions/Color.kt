package uikit.extensions

import androidx.core.graphics.blue
import androidx.core.graphics.green
import androidx.core.graphics.red

fun Int.withAlpha(alpha: Int): Int {
    return this and 0x00ffffff or (alpha shl 24)
}

fun Int.withAlpha(alpha: Float): Int {
    return this and 0x00ffffff or ((alpha * 255).toInt() shl 24)
}

fun Int.isDark(): Boolean {
    val darkness = 1 - (0.299 * red + 0.587 * green + 0.114 * blue) / 255
    return darkness >= 0.5
}

fun Int.darken(factor: Float): Int {
    val red = (red * (1 - factor)).toInt()
    val green = (green * (1 - factor)).toInt()
    val blue = (blue * (1 - factor)).toInt()
    return android.graphics.Color.rgb(red, green, blue)
}

fun Int.lighten(factor: Float): Int {
    val red = (red + (255 - red) * factor).toInt()
    val green = (green + (255 - green) * factor).toInt()
    val blue = (blue + (255 - blue) * factor).toInt()
    return android.graphics.Color.rgb(red, green, blue)
}