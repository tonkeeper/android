package com.tonapps.core.flags

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.core.content.edit
import org.json.JSONObject

@SuppressLint("StaticFieldLeak")
object FeatureManager {

    private const val PREFERENCES_NAME = "AppFeatureManager"

    private lateinit var context: Context
    private lateinit var remote: RemoteConfigProvider

    private var staticOverrides: Map<String, Boolean> = emptyMap()

    private val prefs by lazy {
        context.getSharedPreferences(
            PREFERENCES_NAME,
            Context.MODE_PRIVATE
        )
    }

    fun initialize(context: Context, remote: RemoteConfigProvider) {
        this.context = context
        this.remote = remote
    }

    fun applyStaticOverrides(json: String) {
        if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) {
            return
        }
        if (json.isBlank()) {
            return
        }
        try {
            val obj = JSONObject(json)
            val map = HashMap<String, Boolean>(obj.length())
            obj.keys().forEach { key ->
                map[key] = obj.getBoolean(key)
            }
            staticOverrides = map
        } catch (ignored: Throwable) {
        }
    }

    fun setFeatureEnabled(feature: FeatureKey, isEnabled: Boolean) {
        prefs.edit {
            putBoolean(feature.featureKey, isEnabled)
        }
    }

    fun setFeatureValue(feature: FeatureKey, value: String) {
        prefs.edit {
            putString(valueKey(feature), value)
        }
    }

    fun reset(feature: FeatureKey) {
        prefs.edit {
            remove(feature.featureKey)
                .remove(valueKey(feature))
        }
    }

    fun getRemoteValue(feature: FeatureKey): Pair<Boolean, String> {
        return remote.isFeatureEnabled(feature) to remote.getFeatureValue(feature, "<empty>")
    }

    fun isOverridden(feature: FeatureKey): Boolean {
        return prefs.contains(feature.featureKey) || prefs.contains(valueKey(feature))
    }

    fun isStaticOverridden(feature: FeatureKey): Boolean {
        return staticOverrides.containsKey(feature.featureKey)
    }

    fun isEnabled(feature: FeatureKey): Boolean {
        staticOverrides[feature.featureKey]?.let { return it }
        return prefs.getBoolean(feature.featureKey, remote.isFeatureEnabled(feature))
    }

    fun isDisabled(feature: FeatureKey): Boolean {
        return isEnabled(feature).not()
    }

    fun optValue(feature: FeatureKey): String? {
        return prefs.getString(valueKey(feature), remote.optFeatureValue(feature))
    }

    fun getValue(feature: FeatureKey, default: String): String {
        return prefs.getString(valueKey(feature), null)
            ?: remote.getFeatureValue(feature, default)
    }

    private fun valueKey(feature: FeatureKey): String {
        return "value_${feature.featureKey}"
    }
}
