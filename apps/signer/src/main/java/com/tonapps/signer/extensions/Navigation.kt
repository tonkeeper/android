package com.tonapps.signer.extensions

import android.content.Context
import androidx.annotation.ColorInt
import androidx.annotation.StringRes
import com.tonapps.uikit.color.backgroundContentTintColor
import uikit.navigation.Navigation

fun Navigation.toast(@StringRes resId: Int) {
    val context = this as? Context ?: return
    toast(context.getString(resId), false, context.backgroundContentTintColor)
}

fun Navigation.toast(message: String, @ColorInt color: Int) {
    toast(message, false, color)
}

fun Navigation.toast(message: String) {
    val context = this as? Context ?: return
    toast(message, false, context.backgroundContentTintColor)
}
