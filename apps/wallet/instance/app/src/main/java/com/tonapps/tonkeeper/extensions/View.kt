package com.tonapps.tonkeeper.extensions

import android.graphics.Rect
import android.view.View
import androidx.appcompat.widget.AppCompatTextView

fun View.isOverlapping(target: View): Boolean {
    if (target.visibility != View.VISIBLE) {
        return false
    }
    if (target is AppCompatTextView && target.text.isNullOrEmpty()) {
        return false
    }
    val tmpA = Rect()
    val tmpB = Rect()
    val hasA = getGlobalVisibleRect(tmpA)
    val hasB = target.getGlobalVisibleRect(tmpB)
    return hasA && hasB && Rect.intersects(tmpA, tmpB)
}