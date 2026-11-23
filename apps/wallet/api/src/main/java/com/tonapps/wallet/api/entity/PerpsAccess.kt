package com.tonapps.wallet.api.entity

import org.json.JSONObject

enum class PerpsAccess(val key: String) {
    Available("available"),
    Restricted("restricted"),
    Hidden("hidden");

    companion object {

        fun of(key: String?): PerpsAccess? = entries.firstOrNull { it.key == key }

        fun of(config: JSONObject): PerpsAccess {
            if (config.isNull("feature_access")) {
                val perpsEnabled = config.optJSONObject("flags")?.optBoolean("perps_enabled", false) == true
                return if (perpsEnabled) {
                    Available
                } else {
                    Hidden
                }
            }
            val state = config.optJSONObject("feature_access")?.optJSONObject("perps")?.optString("state")
            return of(state) ?: Hidden
        }
    }
}
