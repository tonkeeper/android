package com.tonapps.bus.core.aptabase

import kotlinx.serialization.json.JsonPrimitive
import java.io.IOException

val testEnvironment = AptabaseEnvironment(
    isDebug = true,
    osVersion = "15",
    locale = "en",
    appVersion = "27.0.0",
    appBuildNumber = "1756900000",
    deviceModel = "Pixel 8",
)

fun testEvent(
    name: String = "test_event",
    timestamp: Long = 1_725_000_000_000L,
    sessionId: String = "session",
    props: Map<String, JsonPrimitive> = mapOf("key" to JsonPrimitive("value")),
): AptabaseEvent {
    return AptabaseEvent(
        timestamp = timestamp,
        sessionId = sessionId,
        eventName = name,
        systemProps = testEnvironment.systemProps,
        props = props,
    )
}

class FakeHttpClient : AptabaseHttpClient {

    class Request(
        val url: String,
        val headers: Map<String, String>,
        val body: String,
    )

    val requests = ArrayList<Request>()
    private val responses = ArrayDeque<() -> AptabaseHttpClient.Response>()

    var defaultCode = 200

    fun enqueue(code: Int, body: String = "") {
        responses.addLast { AptabaseHttpClient.Response(code, body) }
    }

    fun enqueueFailure(message: String = "offline") {
        responses.addLast { throw IOException(message) }
    }

    override fun post(url: String, headers: Map<String, String>, body: ByteArray): AptabaseHttpClient.Response {
        requests.add(Request(url, headers, body.toString(Charsets.UTF_8)))
        val response = responses.removeFirstOrNull() ?: return AptabaseHttpClient.Response(defaultCode, "")
        return response()
    }
}
