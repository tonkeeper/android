package com.tonapps.bus.core.aptabase

import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@Serializable
data class AptabaseEvent(
    val timestamp: Long,
    val sessionId: String,
    val eventName: String,
    val systemProps: SystemProps,
    val props: Map<String, JsonPrimitive>,
) {

    @Serializable
    data class SystemProps(
        val isDebug: Boolean,
        val osName: String,
        val osVersion: String,
        val locale: String,
        val appVersion: String,
        val appBuildNumber: String,
        val sdkVersion: String,
        val deviceModel: String,
    )
}

object AptabaseProps {

    fun normalize(props: Map<String, Any>): Map<String, Any> {
        return props.mapValues { (_, value) -> normalizeValue(value) }
    }

    fun toJson(props: Map<String, Any>): Map<String, JsonPrimitive> {
        return props.mapValues { (_, value) ->
            when (val scalar = normalizeValue(value)) {
                is Boolean -> JsonPrimitive(scalar)
                is Number -> JsonPrimitive(scalar)
                else -> JsonPrimitive(scalar.toString())
            }
        }
    }

    private fun normalizeValue(value: Any): Any {
        return when (value) {
            is String, is Boolean, is Number -> value
            is Iterable<*> -> joinValues(value.map { it.toString() })
            is Array<*> -> joinValues(value.map { it.toString() })
            else -> value.toString()
        }
    }

    private fun joinValues(values: List<String>): String {
        return values.joinToString(",")
    }
}

object AptabaseCoding {

    private val json = Json {
        ignoreUnknownKeys = true
    }

    private val wireTimestampFormatter = DateTimeFormatter
        .ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
        .withZone(ZoneOffset.UTC)

    fun encodeStorage(event: AptabaseEvent): String {
        return json.encodeToString(AptabaseEvent.serializer(), event)
    }

    fun decodeStorage(line: String): AptabaseEvent? {
        return try {
            json.decodeFromString(AptabaseEvent.serializer(), line)
        } catch (e: SerializationException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    fun encodeWire(events: List<AptabaseEvent>): ByteArray {
        val array = buildJsonArray {
            events.forEach { event ->
                add(wireObject(event))
            }
        }
        return array.toString().toByteArray(Charsets.UTF_8)
    }

    fun wireTimestamp(timestampMillis: Long): String {
        return wireTimestampFormatter.format(Instant.ofEpochMilli(timestampMillis))
    }

    private fun wireObject(event: AptabaseEvent): JsonObject {
        return buildJsonObject {
            put("timestamp", wireTimestamp(event.timestamp))
            put("sessionId", event.sessionId)
            put("eventName", event.eventName)
            put("systemProps", json.encodeToJsonElement(AptabaseEvent.SystemProps.serializer(), event.systemProps))
            put("props", JsonObject(event.props))
        }
    }
}
