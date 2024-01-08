package uikit.extensions

fun Int.withAlpha(alpha: Int): Int {
    return this and 0x00ffffff or (alpha shl 24)
}

fun Int.withAlpha(alpha: Float): Int {
    return this and 0x00ffffff or ((alpha * 255).toInt() shl 24)
}