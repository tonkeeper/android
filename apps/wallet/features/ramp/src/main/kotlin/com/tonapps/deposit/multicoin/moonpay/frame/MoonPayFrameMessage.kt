package com.tonapps.deposit.multicoin.moonpay.frame

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

@Serializable
data class MoonPayFrameMessage(
    val version: Int = PROTOCOL_VERSION,
    val meta: Meta,
    val kind: String,
    val payload: JsonElement? = null,
) {

    val channelId: String
        get() = meta.channelId

    fun encode(): String = moonPayFrameJson.encodeToString(serializer(), this)

    @Serializable
    data class Meta(val channelId: String)

    companion object {

        const val PROTOCOL_VERSION = 2

        fun decode(data: String): MoonPayFrameMessage {
            return try {
                moonPayFrameJson.decodeFromString(serializer(), data)
            } catch (e: SerializationException) {
                throw MoonPayFrameException.Malformed("not an envelope", e)
            } catch (e: IllegalArgumentException) {
                throw MoonPayFrameException.Malformed("not an envelope", e)
            }
        }
    }
}

sealed interface MoonPayFrameCommand {

    val kind: String

    fun message(channelId: String): MoonPayFrameMessage

    data object Ack : MoonPayFrameCommand {
        override val kind: String = "ack"
        override fun message(channelId: String): MoonPayFrameMessage {
            return MoonPayFrameMessage(meta = MoonPayFrameMessage.Meta(channelId), kind = kind)
        }
    }

    data class SetQuote(val signature: String) : MoonPayFrameCommand {
        override val kind: String = "setQuote"
        override fun message(channelId: String): MoonPayFrameMessage {
            val payload = buildJsonObject {
                putJsonObject("quote") { put("signature", signature) }
            }
            return MoonPayFrameMessage(meta = MoonPayFrameMessage.Meta(channelId), kind = kind, payload = payload)
        }
    }
}

internal val moonPayFrameJson: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true
    coerceInputValues = true
}
