package com.tonapps.wallet.data.attribution.source

import android.content.Context
import androidx.core.content.edit
import com.tonapps.wallet.data.attribution.entities.AttributionTouch
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

internal class AttributionTouchesDataSource(context: Context) {

    private val prefs = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val lock = Any()

    var isLinkrunnerInstallResolved: Boolean
        get() = prefs.getBoolean(KEY_LINKRUNNER_INSTALL_RESOLVED, false)
        set(value) = prefs.edit { putBoolean(KEY_LINKRUNNER_INSTALL_RESOLVED, value) }

    fun markInstallReported(): Boolean {
        synchronized(lock) {
            if (prefs.getBoolean(KEY_INSTALL_REPORTED, false)) {
                return false
            }
            prefs.edit { putBoolean(KEY_INSTALL_REPORTED, true) }
            return true
        }
    }

    fun add(touch: AttributionTouch) {
        synchronized(lock) {
            val touches = (read() + touch).takeLast(MAX_TOUCHES)
            prefs.edit { putString(KEY_TOUCHES, json.encodeToString(touches)) }
        }
    }

    private fun read(): List<AttributionTouch> {
        val raw = prefs.getString(KEY_TOUCHES, null) ?: return emptyList()
        return try {
            json.decodeFromString(raw)
        } catch (_: SerializationException) {
            emptyList()
        }
    }

    private companion object {
        const val PREFERENCES_NAME = "attribution"
        const val KEY_TOUCHES = "touches"
        const val KEY_LINKRUNNER_INSTALL_RESOLVED = "linkrunner_install_resolved"
        const val KEY_INSTALL_REPORTED = "install_attribution_reported"
        const val MAX_TOUCHES = 50
    }
}
