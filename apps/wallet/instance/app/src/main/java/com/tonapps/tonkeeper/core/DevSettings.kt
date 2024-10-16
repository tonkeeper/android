package com.tonapps.tonkeeper.core

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

}