package com.tonapps.tonkeeper.core

import android.util.Log
import com.tonapps.tonkeeper.App
import androidx.core.content.edit
import com.tonapps.extensions.putBoolean
import com.tonapps.extensions.putLong
import com.tonapps.extensions.putString

object DevSettings {

    private val prefs = App.instance.getSharedPreferences("dev_settings", 0)

    var blurEnabled: Boolean = prefs.getBoolean("blur_enabled", true)
        set(value) {
            if (field != value) {
                field = value
                prefs.putBoolean("blur_enabled", value)
            }
        }

    var firstLaunchDate: Long = prefs.getLong("first_launch_date", 0)
        set(value) {
            if (field != value) {
                field = value
                prefs.putLong("first_launch_date", value)
            }
        }

    var firstLaunchDeeplink: String = prefs.getString("first_launch_deeplink", "") ?: ""
        set(value) {
            if (field != value) {
                field = value
                prefs.putString("first_launch_deeplink", value)
            }
        }

    var firstLaunchDeeplink: String = prefs.getString("first_launch_deeplink", "") ?: ""
        set(value) {
            if (field != value) {
                field = value
                prefs.edit().putString("first_launch_deeplink", value).apply()
            }
        }

    var tonConnectLogs: Boolean = prefs.getBoolean("ton_connect_logs", false)
        set(value) {
            if (field != value) {
                field = value
                prefs.putBoolean("ton_connect_logs", value)
            }
        }

    var ignoreSystemFontSize: Boolean = prefs.getBoolean("ignore_system_font_size", false)
        set(value) {
            if (field != value) {
                field = value
                prefs.putBoolean("ignore_system_font_size", value)
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