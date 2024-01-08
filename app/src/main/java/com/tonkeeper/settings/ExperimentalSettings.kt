package com.tonkeeper.settings

import android.content.Context
import android.os.Build
import androidx.core.content.edit
import com.tonapps.tonkeeperx.BuildConfig

class ExperimentalSettings(context: Context) {

    private companion object {
        private const val BLUR_31_KEY = "blur_31"
        private const val BLUR_LEGACY_KEY = "blur_legacy"
        private const val LIGHT_THEME_KEY = "light_theme"
        private const val BOTTOM_BG_OVER_KEY = "bottom_bg_over"
    }

    private val prefs = context.getSharedPreferences("experimental_settings", Context.MODE_PRIVATE)

    var hasBlur31: Boolean
        get() = prefs.getBoolean(BLUR_31_KEY, BuildConfig.DEBUG && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
        set(value) {
            prefs.edit {
                putBoolean(BLUR_31_KEY, value)
            }
        }

    var hasBlurLegacy: Boolean
        get() = prefs.getBoolean(BLUR_LEGACY_KEY, BuildConfig.DEBUG && Build.VERSION.SDK_INT < Build.VERSION_CODES.S)
        set(value) {
            prefs.edit {
                putBoolean(BLUR_LEGACY_KEY, value)
            }
        }

    var lightTheme: Boolean
        get() = prefs.getBoolean(LIGHT_THEME_KEY, false)
        set(value) {
            prefs.edit {
                putBoolean(LIGHT_THEME_KEY, value)
            }
        }

    var bottomBgOver: Boolean
        get() = prefs.getBoolean(BOTTOM_BG_OVER_KEY, true)
        set(value) {
            prefs.edit {
                putBoolean(BOTTOM_BG_OVER_KEY, value)
            }
        }
}