package com.tonapps.perps.data.hermes

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.longOrNull
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
internal const val HERMES_ERROR_INVALID_CREDENTIALS = "invalid.credentials"
internal const val HERMES_ERROR_ALREADY_AUTHENTICATED = "already.authenticated"

internal const val NAME_LOGIN_RESPONSE = "loginResponse"
internal const val NAME_ERROR_RESPONSE = "errorResponse"
internal const val NAME_ERROR_MESSAGE = "errorMessage"

internal val hermesJson: Json = Json { ignoreUnknownKeys = true }

internal enum class HermesLoginReply { ACCEPTED, REJECTED, FAILED }

internal fun InboundMessage.loginReply(loginRequestId: Long): HermesLoginReply? {
    val id = (reqid as? JsonPrimitive)?.longOrNull
    if (loginRequestId == 0L || id != loginRequestId) {
        return null
    }
    if (name == NAME_LOGIN_RESPONSE) {
        return HermesLoginReply.ACCEPTED
    }
    if (name != NAME_ERROR_RESPONSE && name != NAME_ERROR_MESSAGE) {
        return null
    }
    val code = payload?.let { hermesJson.decodeFromJsonElement<ErrorPayload>(it) }?.code
    return when (code) {
        HERMES_ERROR_ALREADY_AUTHENTICATED -> HermesLoginReply.ACCEPTED
        HERMES_ERROR_INVALID_CREDENTIALS -> HermesLoginReply.REJECTED
        else -> HermesLoginReply.FAILED
    }
}

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
