package com.tonapps.wallet.data.settings.folder

import android.content.Context

internal class ImportLegacyFolder(context: Context): BaseSettingsFolder(context, "import_legacy_folder") {

    private companion object {
        private const val PASSCODE_KEY = "passcode"
        private const val SETTINGS_KEY = "settings"
    }

    var passcode: Boolean
        get() = getBoolean(PASSCODE_KEY, false)
        set(value) = putBoolean(PASSCODE_KEY, value)


    var settings: Boolean
        get() = getBoolean(SETTINGS_KEY, false)
        set(value) = putBoolean(SETTINGS_KEY, value)
}