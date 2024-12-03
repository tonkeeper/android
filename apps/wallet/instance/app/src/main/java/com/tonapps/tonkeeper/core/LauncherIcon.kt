package com.tonapps.tonkeeper.core

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.annotation.DrawableRes
import androidx.webkit.internal.ApiFeature.T
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.tonapps.tonkeeperx.R

enum class LauncherIcon(val type: String, @DrawableRes val iconRes: Int) {
    Default("Default", R.mipmap.ic_launcher),
    Accent("Accent", R.mipmap.ic_accent_launcher),
    Dark("Dark", R.mipmap.ic_dark_launcher),
    Light("Light", R.mipmap.ic_light_launcher);

    fun getComponent(context: Context): ComponentName {
        return ComponentName(context.packageName, "com.tonapps.tonkeeper.${type}LauncherIcon")
    }

    fun isEnabled(context: Context): Boolean {
        val enableState = context.packageManager.getComponentEnabledSetting(getComponent(context))
        return enableState == PackageManager.COMPONENT_ENABLED_STATE_ENABLED || enableState == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT && this == Default
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