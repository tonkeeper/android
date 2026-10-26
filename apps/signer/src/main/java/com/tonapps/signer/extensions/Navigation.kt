package com.tonapps.signer.extensions

import android.content.Context
import androidx.annotation.ColorInt
import androidx.annotation.StringRes
import com.tonapps.uikit.color.accentGreenColor
import com.tonapps.uikit.color.backgroundContentTintColor
import com.tonapps.uikit.icon.UIKitIcon
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

fun Navigation.confirmedToast(message: String) {
    val context = this as? Context ?: return
    toast(
        message = message,
        loading = false,
        color = context.backgroundContentTintColor,
        iconRes = UIKitIcon.ic_checkmark_circle_16,
        iconTint = context.accentGreenColor,
    )
}
