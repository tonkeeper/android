package com.tonapps.bus.core.aptabase

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AptabaseDispatcherTest {

    private val http = FakeHttpClient()
    private val dispatcher = AptabaseDispatcher("https://analytics.example.com/", "A-SH-1", http)

    @Test
    fun postsBatchToEventsEndpointWithHeaders() {
        http.enqueue(200)

        val outcome = dispatcher.send(listOf(testEvent("a"), testEvent("b")))

        assertEquals(AptabaseDispatcher.Outcome.Delivered, outcome)
        val request = http.requests.single()
        assertEquals("https://analytics.example.com/api/v0/events", request.url)
        assertEquals("application/json", request.headers["Content-Type"])
        assertEquals("A-SH-1", request.headers["App-Key"])
        assertEquals(2, Json.parseToJsonElement(request.body).jsonArray.size)
    }

    @Test
    fun emptyBatchIsDeliveredWithoutRequest() {
        assertEquals(AptabaseDispatcher.Outcome.Delivered, dispatcher.send(emptyList()))
        assertTrue(http.requests.isEmpty())
    }

    @Test
    fun oversizedBatchIsAProgrammingError() {
        val events = List(AptabaseDispatcher.MAX_BATCH_SIZE + 1) { testEvent() }
        assertThrows(IllegalArgumentException::class.java) { dispatcher.send(events) }
        assertTrue(http.requests.isEmpty())
    }

    @Test
    fun serverAndThrottlingStatusesRetry() {
        for (code in listOf(500, 502, 503, 408, 425, 429)) {
            http.enqueue(code)
            assertEquals("status $code", AptabaseDispatcher.Outcome.Retry, dispatcher.send(listOf(testEvent())))
        }
    }

    @Test
    fun clientErrorsAreRejected() {
        for (code in listOf(400, 401, 403, 404, 413, 422)) {
            http.enqueue(code, "bad request")
            assertEquals("status $code", AptabaseDispatcher.Outcome.Rejected, dispatcher.send(listOf(testEvent())))
        }
    }

    @Test
    fun anySuccessStatusIsDelivered() {
        for (code in listOf(200, 201, 202, 204)) {
            http.enqueue(code)
            assertEquals("status $code", AptabaseDispatcher.Outcome.Delivered, dispatcher.send(listOf(testEvent())))
        }
    }

    @Test
    fun networkFailureRetries() {
        http.enqueueFailure()
        assertEquals(AptabaseDispatcher.Outcome.Retry, dispatcher.send(listOf(testEvent())))
    }
}
