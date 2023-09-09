package com.tonkeeper

import android.content.Context

object AppSettings {

    private val prefs = App.instance.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    var appsTabs: Boolean
        get() = prefs.getBoolean("apps_tabs", false)
        set(value) = prefs.edit().putBoolean("apps_tabs", value).apply()

    var russianLanguage: Boolean
        get() = prefs.getBoolean("russian_language", false)
        set(value) = prefs.edit().putBoolean("russian_language", value).apply()

    var singleColumn: Boolean
        get() = prefs.getBoolean("single_column", false)
        set(value) = prefs.edit().putBoolean("single_column", value).apply()

}