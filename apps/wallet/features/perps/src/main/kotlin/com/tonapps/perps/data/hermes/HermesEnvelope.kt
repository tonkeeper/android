package com.tonapps.perps.data.hermes

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

@Serializable
internal data class InboundEnvelope(
    val channel: String? = null,
    val message: InboundMessage? = null,
)

@Serializable
internal data class InboundMessage(
    val name: String? = null,
    val reqid: JsonElement? = null,
    val payload: JsonElement? = null,
)

@Serializable
internal data class ErrorPayload(
    val code: String? = null,
    val description: String? = null,
)

internal const val HERMES_ERROR_ALREADY_SUBSCRIBED = "already.subscribed"

internal val hermesJson: Json = Json { ignoreUnknownKeys = true }

internal fun hermesFrame(
    channel: String,
    name: String,
    reqid: Long,
    payload: JsonObject? = null,
): String {
    val frame = buildJsonObject {
        put("channel", channel)
        putJsonObject("message") {
            put("name", name)
            put("reqid", reqid)
            payload?.let { put("payload", it) }
        }
    }
    return frame.toString()
}
