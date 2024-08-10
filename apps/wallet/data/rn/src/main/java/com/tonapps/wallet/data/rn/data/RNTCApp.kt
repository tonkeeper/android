package com.tonapps.wallet.data.rn.data

import com.tonapps.security.Security
import com.tonapps.security.hex
import org.json.JSONObject

data class RNTCApp(
    val name: String,
    val url: String,
    val icon: String,
    val notificationsEnabled: Boolean,
    val connections: List<RNTCConnection>
) {

    val appId: String by lazy {
        val parsedUrl = url.split("?")[0].removeSuffix("/")
        hex(Security.sha256(parsedUrl))
    }

    constructor(json: JSONObject) : this(
        name = json.getString("name"),
        url = json.getString("url"),
        icon = json.getString("icon"),
        notificationsEnabled = json.optBoolean("notificationsEnabled"),
        connections = json.optJSONArray("connections")?.let { connections ->
            List(connections.length()) { i -> RNTCConnection(connections.getJSONObject(i)) }
        } ?: emptyList()
    )

    fun toJSON(): JSONObject {
        val json = JSONObject()
        json.put("name", name)
        json.put("url", url)
        json.put("icon", icon)
        json.put("notificationsEnabled", notificationsEnabled)
        json.put("connections", RNTCConnection.toJSONArray(connections))
        return json
    }
}