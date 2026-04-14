package com.tonapps.core.extensions

import android.content.Context
import androidx.annotation.DrawableRes
import ui.ComposeIcon

fun Context.externalDrawableUrl(@DrawableRes resId: Int): String {
    return "android.resource://$packageName/$resId"
}

fun Context.composeIcon(
    @DrawableRes resId: Int,
    tintColor: Int? = null
) = ComposeIcon(
    url = externalDrawableUrl(resId),
    tintColor = tintColor
)
