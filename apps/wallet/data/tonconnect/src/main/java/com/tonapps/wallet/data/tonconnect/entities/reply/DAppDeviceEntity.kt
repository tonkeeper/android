package com.tonapps.wallet.data.tonconnect.entities.reply

import org.json.JSONArray
import org.json.JSONObject

data class DAppDeviceEntity(
    val platform: String = "android",
    val appName: String = "Tonkeeper",
    val appVersion: String = "4.10.1", // BuildConfig.VERSION_NAME,
    val maxProtocolVersion: Int = 2,
    val sendMaxMessages: Int,
): DAppReply() {

    override fun toJSON(): JSONObject {
        val json = JSONObject()
        json.put("platform", platform)
        json.put("appName", appName)
        json.put("appVersion", appVersion)
        json.put("maxProtocolVersion", maxProtocolVersion)
        json.put("features", buildFeatures())
        return json
    }

    private fun buildFeatures(): JSONArray {
        val array = JSONArray()
        array.put("SendTransaction")
        array.put(JSONObject().apply {
            put("name", "SendTransaction")
            put("maxMessages", sendMaxMessages)
        })
        return array
    }
}