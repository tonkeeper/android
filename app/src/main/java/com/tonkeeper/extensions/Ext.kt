package com.tonkeeper.extensions

fun createCorners(
    topLeft: Float,
    topRight: Float,
    bottomRight: Float,
    bottomLeft: Float
): FloatArray {
    return floatArrayOf(
        topLeft, topLeft,
        topRight, topRight,
        bottomRight, bottomRight,
        bottomLeft, bottomLeft
    )
}
