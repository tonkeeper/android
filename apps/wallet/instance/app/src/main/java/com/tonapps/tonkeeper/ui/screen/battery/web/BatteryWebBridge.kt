package com.tonapps.tonkeeper.ui.screen.battery.web

import com.tonapps.log.L
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.text.isNotBlank

internal class BatteryWebBridge(
    private val loadData: suspend (expiredAccessToken: String?) -> Data,
) {

    private enum class Method(val value: String) {
        GET_DATA("get-data"),
        REFRESH_DATA("refresh-data");

        companion object {
            fun from(value: String?): Method? = entries.firstOrNull { it.value == value }
        }
    }

    @Serializable
    data class Data(
        val walletId: String,
        val deviceToken: String,
        val walletToken: String,
    )

    suspend fun handleMessage(message: String): String? {
        val json = try {
            Json.parseToJsonElement(message) as? JsonObject ?: return ignored("expected JSON object")
        } catch (_: IllegalArgumentException) {
            return ignored("invalid JSON")
        }

        val queryId = json.string("queryId")
            ?: return ignored("missing or invalid queryId")

        val method = Method.from(json.string("type"))
            ?: return ignored("unknown method, queryId=$queryId")

        val expiredAccessToken = when (method) {
            Method.GET_DATA -> null
            Method.REFRESH_DATA -> json.string("accessToken")
                ?: return ignored("missing or invalid accessToken, queryId=$queryId")
        }

        L.d("Request received: method=${method.value}, queryId=$queryId")
        val data = loadData(expiredAccessToken)

        L.d(
            "Response prepared: method=${method.value}, queryId=$queryId, " +
                "walletId=${data.walletId}, " +
                "hasDeviceToken=${data.deviceToken.isNotBlank()}, " +
                "hasProofToken=${data.walletToken.isNotBlank()}"
        )

        return buildJsonObject {
            put("queryId", queryId)
            put("payload", Json.encodeToString(data))
        }.toString()
    }

    private fun ignored(reason: String): String? {
        L.d("Message ignored: $reason")
        return null
    }

    private fun JsonObject.string(key: String): String? {
        val value = get(key) as? JsonPrimitive ?: return null
        return value.takeIf { it.isString && it.content.isNotBlank() }
            ?.content
    }
}
