package com.tonkeeper.extensions

import android.content.Context
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.ColorInt
import androidx.annotation.ColorRes
import androidx.annotation.DimenRes
import androidx.annotation.Dimension
import androidx.annotation.LayoutRes

@ColorInt
fun Context.getColor(@ColorRes resId: Int): Int {
    return resources.getColor(resId, theme)
}

fun Context.inflate(
    @LayoutRes layoutId: Int,
    root: ViewGroup? = null,
    attachToRoot: Boolean = false
): View {
    return LayoutInflater.from(this).inflate(layoutId, root, attachToRoot)
}

@Dimension
fun Context.getDimension(@DimenRes resId: Int): Float {
    return resources.getDimension(resId)
}