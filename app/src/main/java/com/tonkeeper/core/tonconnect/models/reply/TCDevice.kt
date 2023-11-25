package com.tonkeeper.core.tonconnect.models.reply

import com.tonkeeper.BuildConfig
import org.json.JSONObject

data class TCDevice(
    val platform: String = "android",
    val appName: String = "Tonkeeper Y",
    val appVersion: String = BuildConfig.VERSION_NAME,
    val maxProtocolVersion: Int = 2,
    // val features: List<Any> = emptyList()
): TCBase() {
    override fun toJSON(): JSONObject {
        val json = JSONObject()
        json.put("platform", platform)
        json.put("appName", appName)
        json.put("appVersion", appVersion)
        json.put("maxProtocolVersion", maxProtocolVersion)
        // json.put("features", features)
        return json
    }

}