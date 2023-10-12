package com.tonkeeper.uikit.extensions

import android.view.ViewPropertyAnimator

fun ViewPropertyAnimator.scale(float: Float): ViewPropertyAnimator {
    scaleX(float)
    scaleY(float)
    return this
}