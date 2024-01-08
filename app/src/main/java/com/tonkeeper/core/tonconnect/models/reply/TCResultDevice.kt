package com.tonkeeper.core.tonconnect.models.reply

import org.json.JSONArray
import org.json.JSONObject

data class TCResultDevice(
    val platform: String = "android",
    val appName: String = "Tonkeeper",
    val appVersion: String = "3.4.0", // BuildConfig.VERSION_NAME,
    val maxProtocolVersion: Int = 2,
    val features: List<String> = listOf("SendTransaction")
): TCBase() {

    override fun toJSON(): JSONObject {
        val json = JSONObject()
        json.put("platform", platform)
        json.put("appName", appName)
        json.put("appVersion", appVersion)
        json.put("maxProtocolVersion", maxProtocolVersion)
        json.put("features", JSONArray(features))
        return json
    }

}