package com.tonapps.tonkeeper.core

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.webkit.internal.ApiFeature.T
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.tonapps.tonkeeperx.R
import uikit.extensions.drawable

enum class LauncherIcon(
    val type: String,
    @DrawableRes val iconRes: Int,
    @DrawableRes val bgDrawableRes: Int = 0,
    @ColorRes val bgColorRes: Int = 0,
    @DrawableRes val fgRes: Int,
) {
    Default(
        type = "Default",
        iconRes = R.mipmap.ic_default_launcher,
        bgColorRes = R.color.ic_default_launcher_background,
        fgRes = R.drawable.ic_default_launcher_foreground
    ),

    Accent(
        type = "Accent",
        iconRes = R.mipmap.ic_accent_launcher,
        bgColorRes = R.color.ic_accent_launcher_background,
        fgRes = R.drawable.ic_accent_launcher_foreground
    ),

    Dark(
        type = "Dark",
        iconRes = R.mipmap.ic_dark_launcher,
        bgDrawableRes = R.drawable.ic_dark_launcher_background,
        fgRes = R.drawable.ic_dark_launcher_foreground
    ),

    Light(
        type = "Light",
        iconRes = R.mipmap.ic_light_launcher,
        bgColorRes = R.color.ic_light_launcher_background,
        fgRes = R.drawable.ic_light_launcher_foreground
    );

    fun getComponent(context: Context): ComponentName {
        return ComponentName(context.packageName, "com.tonapps.tonkeeper.${type}LauncherIcon")
    }

    fun isEnabled(context: Context): Boolean {
        val enableState = context.packageManager.getComponentEnabledSetting(getComponent(context))
        return enableState == PackageManager.COMPONENT_ENABLED_STATE_ENABLED || enableState == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT && this == Default
    }

    fun getBackgroundDrawable(context: Context): Drawable {
        return if (bgDrawableRes != 0) {
            context.drawable(bgDrawableRes)
        } else {
            ColorDrawable(context.getColor(bgColorRes))
        }
    }

    fun getForegroundDrawable(context: Context): Drawable {
        return context.drawable(fgRes)
    }

    companion object {

        fun setEnable(context: Context, newIcon: LauncherIcon): Boolean {
            try {
                if (newIcon.isEnabled(context)) {
                    return false
                }

                val packageManager = context.packageManager
                for (icon in entries) {
                    val component = icon.getComponent(context)
                    val newState = if (icon == newIcon) {
                        PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                    } else {
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                    }
                    packageManager.setComponentEnabledSetting(component, newState, PackageManager.DONT_KILL_APP)
                }
                return true
            } catch (e: Throwable) {
                FirebaseCrashlytics.getInstance().recordException(e)
                return false
            }
        }
    }
}