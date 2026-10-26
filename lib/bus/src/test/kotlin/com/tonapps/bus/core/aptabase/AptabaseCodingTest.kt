package com.tonapps.bus.core.aptabase

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AptabaseCodingTest {

    private enum class Kind { Native }

    @Test
    fun wireTimestampIsUtcSecondsWithoutMillis() {
        assertEquals("1970-01-01T00:00:00Z", AptabaseCoding.wireTimestamp(0L))
        assertEquals("2024-08-30T06:40:00Z", AptabaseCoding.wireTimestamp(1_725_000_000_999L))
    }

    @Test
    fun wireBodyMatchesSdkShape() {
        val event = testEvent(
            name = "swap_open",
            timestamp = 1_725_000_000_000L,
            sessionId = "abc",
            props = mapOf(
                "text" to JsonPrimitive("hello"),
                "count" to JsonPrimitive(3),
                "flag" to JsonPrimitive(true),
            ),
        )

        val body = Json.parseToJsonElement(AptabaseCoding.encodeWire(listOf(event)).toString(Charsets.UTF_8)).jsonArray
        assertEquals(1, body.size)

        val wire = body.single().jsonObject
        assertEquals(setOf("timestamp", "sessionId", "eventName", "systemProps", "props"), wire.keys)
        assertEquals("2024-08-30T06:40:00Z", wire.getValue("timestamp").jsonPrimitive.content)
        assertEquals("abc", wire.getValue("sessionId").jsonPrimitive.content)
        assertEquals("swap_open", wire.getValue("eventName").jsonPrimitive.content)

        val systemProps = wire.getValue("systemProps").jsonObject
        assertEquals(
            setOf("isDebug", "osName", "osVersion", "locale", "appVersion", "appBuildNumber", "sdkVersion", "deviceModel"),
            systemProps.keys,
        )
        assertEquals(true, systemProps.getValue("isDebug").jsonPrimitive.booleanOrNull)
        assertEquals("Android", systemProps.getValue("osName").jsonPrimitive.content)
        assertEquals("aptabase-kotlin@0.0.8", systemProps.getValue("sdkVersion").jsonPrimitive.content)
        assertEquals("Pixel 8", systemProps.getValue("deviceModel").jsonPrimitive.content)

        val props = wire.getValue("props").jsonObject
        assertEquals("\"hello\"", props.getValue("text").toString())
        assertEquals("3", props.getValue("count").toString())
        assertEquals("true", props.getValue("flag").toString())
    }

    @Test
    fun wireBodyKeepsBatchOrder() {
        val events = (1..3).map { testEvent(name = "event_$it") }
        val body = Json.parseToJsonElement(AptabaseCoding.encodeWire(events).toString(Charsets.UTF_8)).jsonArray
        assertEquals(listOf("event_1", "event_2", "event_3"), body.map { it.jsonObject.getValue("eventName").jsonPrimitive.content })
    }

    @Test
    fun storageRoundTripIsLossless() {
        val event = testEvent(timestamp = 1_725_000_000_123L, props = mapOf("n" to JsonPrimitive(1.5)))
        val line = AptabaseCoding.encodeStorage(event)
        assertTrue(!line.contains('\n'))
        assertEquals(event, AptabaseCoding.decodeStorage(line))
    }

    @Test
    fun corruptStorageLinesAreSkipped() {
        assertNull(AptabaseCoding.decodeStorage("not json"))
        assertNull(AptabaseCoding.decodeStorage("{\"timestamp\":\"oops\"}"))
        assertNull(AptabaseCoding.decodeStorage("{}"))
    }

    @Test
    fun propsNormalizeToScalars() {
        val normalized = AptabaseProps.normalize(
            mapOf(
                "text" to "hello",
                "count" to 3,
                "amount" to 1.5,
                "flag" to false,
                "list" to listOf("b", "a"),
                "set" to linkedSetOf("y", "x"),
                "array" to arrayOf(2, 1),
                "enum" to Kind.Native,
                "nested" to mapOf("k" to "v"),
            )
        )

        assertEquals("hello", normalized["text"])
        assertEquals(3, normalized["count"])
        assertEquals(1.5, normalized["amount"])
        assertEquals(false, normalized["flag"])
        assertEquals("b,a", normalized["list"])
        assertEquals("y,x", normalized["set"])
        assertEquals("2,1", normalized["array"])
        assertEquals("Native", normalized["enum"])
        assertEquals("{k=v}", normalized["nested"])
    }

    @Test
    fun propsToJsonKeepsNumericAndBooleanTypes() {
        val json = AptabaseProps.toJson(mapOf("count" to 3L, "flag" to true, "text" to "x", "list" to listOf(1)))

        assertEquals("3", json.getValue("count").toString())
        assertEquals("true", json.getValue("flag").toString())
        assertEquals("\"x\"", json.getValue("text").toString())
        assertEquals("\"1\"", json.getValue("list").toString())
    }
}
