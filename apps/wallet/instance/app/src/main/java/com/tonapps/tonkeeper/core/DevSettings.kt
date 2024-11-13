package com.tonapps.tonkeeper.core

import android.util.Log
import com.tonapps.tonkeeper.App

object DevSettings {

    private val prefs = App.instance.getSharedPreferences("dev_settings", 0)

    var blurEnabled: Boolean = prefs.getBoolean("blur_enabled", true)
        set(value) {
            if (field != value) {
                field = value
                prefs.edit().putBoolean("blur_enabled", value).apply()
            }
        }

    var tonConnectLogs: Boolean = prefs.getBoolean("ton_connect_logs", false)
        set(value) {
            if (field != value) {
                field = value
                prefs.edit().putBoolean("ton_connect_logs", value).apply()
            }
        }

    var ignoreSystemFontSize: Boolean = prefs.getBoolean("ignore_system_font_size", false)
        set(value) {
            if (field != value) {
                field = value
                prefs.edit().putBoolean("ignore_system_font_size", value).apply()
            }
        }


    fun tonConnectLog(message: String, error: Boolean = false) {
        if (tonConnectLogs) {
            if (error) {
                Log.e("TonConnect", message)
            } else {
                Log.d("TonConnect", message)
            }
        }
    }

}