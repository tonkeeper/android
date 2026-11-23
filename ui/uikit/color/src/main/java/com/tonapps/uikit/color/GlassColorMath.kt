package com.tonapps.uikit.color

import kotlin.math.roundToInt

object GlassColorMath {

    private val STOP_CHANNEL_DELTAS = intArrayOf(-8, -8, 0, 0, 12)
    private const val REGULAR_TINT_ALPHA = 0.8f
    private const val PROMINENT_WHITE_ALPHA = 0.94f
    private const val CHANNEL_MAX = 255
    private const val CHANNEL_MASK = 0xFF
    private const val RGB_MASK = 0xFFFFFF
    private const val ALPHA_SHIFT = 24
    private const val RED_SHIFT = 16
    private const val GREEN_SHIFT = 8
    private const val BLUE_SHIFT = 0
    private val CHANNEL_SHIFTS = intArrayOf(RED_SHIFT, GREEN_SHIFT, BLUE_SHIFT)

    fun fillPositions(): FloatArray = floatArrayOf(0f, 0.15f, 0.4f, 0.7f, 1f)

    fun over(src: Int, dst: Int): Int {
        val alpha = channel(src, ALPHA_SHIFT) / CHANNEL_MAX.toFloat()
        return mapChannels { shift ->
            (channel(src, shift) * alpha + channel(dst, shift) * (1f - alpha)).roundToInt()
        }
    }

    fun fillStops(style: GlassStyle, tint: Int, page: Int): IntArray {
        val target = when (style) {
            GlassStyle.Regular -> over(withAlpha(tint, REGULAR_TINT_ALPHA), page)
            GlassStyle.Prominent -> {
                val base = over(withAlpha(RGB_MASK, PROMINENT_WHITE_ALPHA), page)
                mapChannels { shift -> channel(base, shift) + channel(tint, shift) - CHANNEL_MAX }
            }
        }
        return IntArray(STOP_CHANNEL_DELTAS.size) { index ->
            val shifted = mapChannels { shift -> channel(target, shift) + STOP_CHANNEL_DELTAS[index] }
            when (style) {
                GlassStyle.Prominent -> shifted
                GlassStyle.Regular -> {
                    val pageWeight = 1f - REGULAR_TINT_ALPHA
                    val source = mapChannels { shift ->
                        ((channel(shifted, shift) - channel(page, shift) * pageWeight) / REGULAR_TINT_ALPHA).roundToInt()
                    }
                    withAlpha(source, REGULAR_TINT_ALPHA)
                }
            }
        }
    }

    private fun channel(color: Int, shift: Int): Int = (color ushr shift) and CHANNEL_MASK

    private fun withAlpha(color: Int, alpha: Float): Int {
        val alphaByte = (alpha * CHANNEL_MAX).roundToInt()
        return (alphaByte shl ALPHA_SHIFT) or (color and RGB_MASK)
    }

    private inline fun mapChannels(value: (shift: Int) -> Int): Int {
        var color = CHANNEL_MASK shl ALPHA_SHIFT
        for (shift in CHANNEL_SHIFTS) {
            color = color or (value(shift).coerceIn(0, CHANNEL_MAX) shl shift)
        }
        return color
    }
}
