package com.tonkeeper.core.tonconnect.models.reply

import com.tonapps.tonkeeperx.BuildConfig
import org.json.JSONArray
import org.json.JSONObject

data class TCResultDevice(
    val platform: String = "android",
    val appName: String = "Tonkeeper",
    val appVersion: String = "3.5.431", // BuildConfig.VERSION_NAME,
    val maxProtocolVersion: Int = 2,
    // val features: List<Any> = emptyList()
): TCBase() {
    override fun toJSON(): JSONObject {
        val json = JSONObject()
        json.put("platform", platform)
        json.put("appName", appName)
        json.put("appVersion", appVersion)
        json.put("maxProtocolVersion", maxProtocolVersion)

        // TODO: fix this
        json.put("features", JSONArray("[\n" +
                "    \"SendTransaction\",\n" +
                "    {\n" +
                "        \"name\": \"SendTransaction\",\n" +
                "        \"maxMessages\": 4\n" +
                "    }\n" +
                "]"))
        return json
    }

}