package com.tonapps.wallet.data.attribution.source

import android.content.Context
import androidx.core.content.edit

internal class InstallAttributionDataSource(context: Context) {

    private val prefs = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val lock = Any()

    fun markInstallReported(): Boolean {
        synchronized(lock) {
            removeLinkrunnerKeys()
            if (prefs.getBoolean(KEY_INSTALL_REPORTED, false)) {
                return false
            }
            prefs.edit(commit = true) { putBoolean(KEY_INSTALL_REPORTED, true) }
            return true
        }
    }

    private fun removeLinkrunnerKeys() {
        if (LINKRUNNER_KEYS.any(prefs::contains)) {
            prefs.edit { LINKRUNNER_KEYS.forEach { key -> remove(key) } }
        }
    }

    private companion object {
        const val PREFERENCES_NAME = "attribution"
        const val KEY_INSTALL_REPORTED = "install_attribution_reported"
        val LINKRUNNER_KEYS = listOf("touches", "linkrunner_install_resolved")
    }
}
