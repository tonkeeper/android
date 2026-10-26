package com.tonapps.tonkeeper.extensions

import android.content.Context
import androidx.annotation.ColorInt
import androidx.annotation.StringRes
import com.tonapps.uikit.color.accentGreenColor
import com.tonapps.uikit.color.accentOrangeColor
import com.tonapps.uikit.color.backgroundContentTintColor
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.localization.Localization
import uikit.navigation.Navigation
import uikit.widget.ToastView

fun Navigation.toast(@StringRes resId: Int, duration: Long = ToastView.DURATION_DEFAULT) {
    val context = this as? Context ?: return
    toast(context.getString(resId), false, context.backgroundContentTintColor, duration)
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

fun Navigation.warningToast(@StringRes resId: Int, duration: Long = ToastView.DURATION_DEFAULT) {
    val context = this as? Context ?: return
    toast(
        message = context.getString(resId),
        loading = false,
        color = context.backgroundContentTintColor,
        duration = duration,
        iconRes = UIKitIcon.ic_exclamationmark_triangle_16,
        iconTint = context.accentOrangeColor,
    )
}

fun Navigation.toastLoading(loading: Boolean) {
    val context = this as? Context ?: return
    toast(context.getString(Localization.loading), loading, context.backgroundContentTintColor)
}

