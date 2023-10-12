package com.tonkeeper.uikit.interpolator

import android.view.animation.Interpolator
import kotlin.math.abs

class ReverseInterpolator: Interpolator {
    override fun getInterpolation(input: Float): Float {
        return reverseInput(input)
    }

    private fun reverseInput(input: Float): Float {
        return if (input <= 0.5) input * 2 else abs(input - 1) * 2
    }
}