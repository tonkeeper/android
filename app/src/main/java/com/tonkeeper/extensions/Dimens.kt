package com.tonkeeper.extensions

import android.content.res.Resources
import kotlin.math.roundToInt

val Int.dp: Int
    get() {
        val density = Resources.getSystem().displayMetrics.density
        return (this * density).roundToInt()
    }

val Float.dp: Float
    get() {
        val density = Resources.getSystem().displayMetrics.density
        return (this * density)
    }

val Int.sp: Int
    get() {
        val density = Resources.getSystem().displayMetrics.scaledDensity
        return (this * density).roundToInt()
    }

val Float.sp: Float
    get() {
        val density = Resources.getSystem().displayMetrics.scaledDensity
        return (this * density)
    }