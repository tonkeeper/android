package com.tonapps.tonkeeper.extensions

import android.content.Context
import androidx.annotation.ColorInt
import androidx.annotation.StringRes
import com.tonapps.tonkeeper.fragment.camera.CameraFragment
import com.tonapps.uikit.color.backgroundContentTintColor
import com.tonapps.wallet.localization.Localization
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

fun Navigation.toastLoading(loading: Boolean) {
    val context = this as? Context ?: return
    toast(context.getString(Localization.loading), loading, context.backgroundContentTintColor)
}

fun Navigation.openCamera() {
    add(CameraFragment.newInstance())
}
