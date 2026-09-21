package com.tonapps.wallet.api.internal

import android.content.Context
import android.content.pm.ApplicationInfo
import com.tonapps.wallet.api.entity.ConfigEntity
import org.json.JSONObject

/**
 * Debug-only overlay for `/keys/all` flags, applied on top of the fetched boot config.
 * Set from the `bootFlags` launch extra (RootActivity.EXTRA_BOOT_FLAGS).
 */
object BootConfigOverrides {

    @Volatile
    private var overlay: JSONObject? = null

    fun apply(context: Context, json: String) {
        if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) {
            return
        }
        if (json.isBlank()) {
            return
        }
        try {
            overlay = JSONObject(json)
        } catch (_: Throwable) {
        }
    }

    fun applyTo(config: ConfigEntity): ConfigEntity {
        val flags = overlay ?: return config
        if (!flags.has("multichain_enabled")) {
            return config
        }
        return config.copy(
            flags = config.flags.copy(
                multichainEnabled = flags.optBoolean("multichain_enabled"),
            ),
        )
    }
}
