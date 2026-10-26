package com.tonapps.bus.core.aptabase

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.coroutines.CoroutineContext
import kotlin.random.Random

private const val START_MILLIS = 1_725_000_000_000L
private const val FLUSH_INTERVAL_MILLIS = 60_000L

class AptabaseQueueClientTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val http = FakeHttpClient()
    private var sessionCounter = 0

    private val directory: File by lazy { File(folder.root, "analytics") }

    private object LowestJitter : Random() {
        override fun nextBits(bitCount: Int): Int = 0
    }

    private class GatedDispatcher : CoroutineDispatcher() {
        private val queue = ArrayDeque<Runnable>()

        override fun dispatch(context: CoroutineContext, block: Runnable) {
            queue.addLast(block)
        }

        fun drain() {
            while (queue.isNotEmpty()) {
                queue.removeFirst().run()
            }
        }
    }

    private fun TestScope.now(): Long = START_MILLIS + testScheduler.currentTime

    private fun TestScope.store() = AptabaseEventStore(directory = directory, clock = { now() })

    private fun TestScope.client(
        scope: CoroutineScope = backgroundScope,
        store: AptabaseEventStore = store(),
        networkDispatcher: CoroutineDispatcher = StandardTestDispatcher(testScheduler),
    ): AptabaseQueueClient {
        return AptabaseQueueClient(
            store = store,
            environment = testEnvironment,
            scope = scope,
            networkDispatcher = networkDispatcher,
            flushIntervalMillis = FLUSH_INTERVAL_MILLIS,
            clock = { now() },
            random = LowestJitter,
            sessionIdFactory = { "session-${++sessionCounter}" },
        )
    }

    private fun dispatcher() = AptabaseDispatcher("https://analytics.example.com", "A-SH-1", http)

    private fun startConnected(client: AptabaseQueueClient, sharedProps: Map<String, JsonPrimitive> = emptyMap()) {
        client.start()
        client.connect(dispatcher(), sharedProps)
    }

    private fun TestScope.track(client: AptabaseQueueClient, name: String) {
        client.track(AptabaseQueueClient.PendingEvent(name, mapOf("k" to JsonPrimitive("v")), now()))
    }

    private fun sentNames(): List<String> {
        return http.requests.flatMap { request ->
            Json.parseToJsonElement(request.body).jsonArray.map { it.jsonObject.getValue("eventName").jsonPrimitive.content }
        }
    }

    private fun sentSessionIds(): List<String> {
        return http.requests.flatMap { request ->
            Json.parseToJsonElement(request.body).jsonArray.map { it.jsonObject.getValue("sessionId").jsonPrimitive.content }
        }
    }

    @Test
    fun eventsArePersistedAndFlushedByTicker() = runTest {
        val client = client()
        startConnected(client)
        runCurrent()

        track(client, "a")
        track(client, "b")
        runCurrent()

        assertEquals(2, client.pendingCount())
        assertTrue(http.requests.isEmpty())

        advanceTimeBy(FLUSH_INTERVAL_MILLIS)
        runCurrent()

        assertEquals(listOf("a", "b"), sentNames())
        assertEquals(0, client.pendingCount())
        assertEquals(1, http.requests.size)
    }

    @Test
    fun thresholdFlushesWithoutWaitingForTicker() = runTest {
        val client = client()
        startConnected(client)
        runCurrent()

        repeat(49) { track(client, "e$it") }
        runCurrent()
        assertTrue(http.requests.isEmpty())

        track(client, "e49")
        runCurrent()

        assertEquals(50, sentNames().size)
        assertEquals(listOf(25, 25), http.requests.map { Json.parseToJsonElement(it.body).jsonArray.size })
    }

    @Test
    fun batchesAreCappedAndOrdered() = runTest {
        val client = client()
        startConnected(client)
        runCurrent()

        repeat(60) { track(client, "e$it") }
        runCurrent()
        advanceTimeBy(FLUSH_INTERVAL_MILLIS)
        runCurrent()

        assertEquals(List(60) { "e$it" }, sentNames())
        assertEquals(listOf(25, 25, 10), http.requests.map { Json.parseToJsonElement(it.body).jsonArray.size })
        assertEquals(0, client.pendingCount())
    }

    @Test
    fun retryKeepsEventsAndBacksOffExponentially() = runTest {
        val client = client()
        startConnected(client)
        runCurrent()
        track(client, "a")
        runCurrent()

        http.enqueue(503)
        advanceTimeBy(FLUSH_INTERVAL_MILLIS)
        runCurrent()
        assertEquals(1, http.requests.size)
        assertEquals(1, client.pendingCount())

        http.enqueue(503)
        client.requestFlush()
        runCurrent()
        assertEquals(1, http.requests.size)

        advanceTimeBy(800)
        runCurrent()
        client.requestFlush()
        runCurrent()
        assertEquals(2, http.requests.size)

        advanceTimeBy(1_599)
        runCurrent()
        client.requestFlush()
        runCurrent()
        assertEquals(2, http.requests.size)

        advanceTimeBy(1)
        runCurrent()
        client.requestFlush()
        runCurrent()
        assertEquals(3, http.requests.size)
        assertEquals(listOf("a", "a", "a"), sentNames())
        assertEquals(0, client.pendingCount())
    }

    @Test
    fun backoffIsCappedAtFiveMinutes() = runTest {
        val client = client()
        startConnected(client)
        client.suspended()
        runCurrent()
        track(client, "a")
        runCurrent()

        repeat(12) {
            http.enqueue(500)
            advanceTimeBy(TimeUnit.MINUTES.toMillis(10))
            runCurrent()
            client.requestFlush()
            runCurrent()
        }
        assertEquals(12, http.requests.size)

        http.enqueue(500)
        advanceTimeBy(TimeUnit.MINUTES.toMillis(4) - 1)
        runCurrent()
        client.requestFlush()
        runCurrent()
        assertEquals(12, http.requests.size)

        advanceTimeBy(1)
        runCurrent()
        client.requestFlush()
        runCurrent()
        assertEquals(13, http.requests.size)
    }

    @Test
    fun rejectedBatchIsDroppedAndNotRetried() = runTest {
        val client = client()
        startConnected(client)
        runCurrent()
        track(client, "bad")
        runCurrent()

        http.enqueue(400, "invalid")
        advanceTimeBy(FLUSH_INTERVAL_MILLIS)
        runCurrent()

        assertEquals(1, http.requests.size)
        assertEquals(0, client.pendingCount())

        advanceTimeBy(FLUSH_INTERVAL_MILLIS)
        runCurrent()
        assertEquals(1, http.requests.size)
    }

    @Test
    fun networkFailureRetriesLater() = runTest {
        val client = client()
        startConnected(client)
        runCurrent()
        track(client, "a")
        runCurrent()

        http.enqueueFailure()
        advanceTimeBy(FLUSH_INTERVAL_MILLIS)
        runCurrent()
        assertEquals(1, client.pendingCount())

        advanceTimeBy(FLUSH_INTERVAL_MILLIS)
        runCurrent()
        assertEquals(0, client.pendingCount())
        assertEquals(listOf("a", "a"), sentNames())
    }

    @Test
    fun connectFlushesQueueLeftByPreviousRun() = runTest {
        store().apply {
            append(testEvent("leftover_1", timestamp = now()))
            append(testEvent("leftover_2", timestamp = now()))
        }

        val client = client()
        startConnected(client)
        runCurrent()

        assertEquals(listOf("leftover_1", "leftover_2"), sentNames())
        assertEquals(0, client.pendingCount())
    }

    @Test
    fun suspendedFlushesImmediatelyAndStopsTicker() = runTest {
        val client = client()
        startConnected(client)
        runCurrent()
        track(client, "a")
        var completed = false
        client.suspended { completed = true }
        runCurrent()

        assertEquals(listOf("a"), sentNames())
        assertTrue(completed)

        track(client, "b")
        runCurrent()
        advanceTimeBy(FLUSH_INTERVAL_MILLIS * 3)
        runCurrent()
        assertEquals(listOf("a"), sentNames())

        client.resumed()
        runCurrent()
        advanceTimeBy(FLUSH_INTERVAL_MILLIS)
        runCurrent()
        assertEquals(listOf("a", "b"), sentNames())
    }

    @Test
    fun eventsLoggedBeforeSuspendAreSentBeforeCompletion() = runTest {
        val client = client()
        startConnected(client)
        runCurrent()

        repeat(5) { track(client, "e$it") }
        var namesAtCompletion = emptyList<String>()
        client.suspended { namesAtCompletion = sentNames() }
        runCurrent()

        assertEquals(List(5) { "e$it" }, namesAtCompletion)
    }

    @Test
    fun sessionRotatesAfterAnHourOfInactivity() = runTest {
        val client = client()
        startConnected(client)
        runCurrent()

        track(client, "a")
        advanceTimeBy(TimeUnit.MINUTES.toMillis(59))
        track(client, "b")
        advanceTimeBy(TimeUnit.HOURS.toMillis(1) + 1)
        track(client, "c")
        runCurrent()
        client.requestFlush()
        runCurrent()

        assertEquals(listOf("session-1", "session-1", "session-2"), sentSessionIds())
    }

    @Test
    fun eventTimestampIsTheOneCapturedAtTrackTime() = runTest {
        val client = client()
        startConnected(client)
        runCurrent()

        val captured = now()
        client.track(AptabaseQueueClient.PendingEvent("a", emptyMap(), captured))
        advanceTimeBy(FLUSH_INTERVAL_MILLIS)
        runCurrent()

        val wire = Json.parseToJsonElement(http.requests.single().body).jsonArray.single().jsonObject
        assertEquals(AptabaseCoding.wireTimestamp(captured), wire.getValue("timestamp").jsonPrimitive.content)
    }

    @Test
    fun startIsIdempotent() = runTest {
        val client = client()
        startConnected(client)
        client.start()
        runCurrent()
        track(client, "a")
        runCurrent()
        advanceTimeBy(FLUSH_INTERVAL_MILLIS)
        runCurrent()

        assertEquals(listOf("a"), sentNames())
    }

    @Test
    fun eventsBeforeConnectArePersistedAndSentOnConnect() = runTest {
        val client = client()
        client.start()
        runCurrent()

        track(client, "a")
        track(client, "b")
        runCurrent()
        advanceTimeBy(FLUSH_INTERVAL_MILLIS * 2)
        runCurrent()

        assertEquals(2, client.pendingCount())
        assertTrue(http.requests.isEmpty())

        client.connect(dispatcher(), emptyMap())
        runCurrent()

        assertEquals(listOf("a", "b"), sentNames())
        assertEquals(0, client.pendingCount())
    }

    @Test
    fun sharedPropsAreMergedAtSendTimeOnly() = runTest {
        val store = store()
        val client = client(store = store)
        client.start()
        runCurrent()
        track(client, "a")
        runCurrent()

        assertEquals(setOf("k"), store.peek(1).events.single().props.keys)

        client.connect(dispatcher(), mapOf("firebase_user_id" to JsonPrimitive("install"), "k" to JsonPrimitive("shared")))
        runCurrent()

        val props = Json.parseToJsonElement(http.requests.single().body).jsonArray.single().jsonObject.getValue("props").jsonObject
        assertEquals("install", props.getValue("firebase_user_id").jsonPrimitive.content)
        assertEquals("shared", props.getValue("k").jsonPrimitive.content)
    }

    @Test
    fun networkAvailableResetsBackoffAndFlushes() = runTest {
        val client = client()
        startConnected(client)
        client.suspended()
        runCurrent()
        track(client, "a")
        runCurrent()

        http.enqueue(503)
        client.requestFlush()
        runCurrent()
        assertEquals(1, http.requests.size)

        client.requestFlush()
        runCurrent()
        assertEquals(1, http.requests.size)

        client.networkAvailable()
        runCurrent()
        assertEquals(2, http.requests.size)
        assertEquals(0, client.pendingCount())
    }

    @Test
    fun flushWithoutDispatcherIsNoop() = runTest {
        val client = client()
        client.start()
        runCurrent()
        track(client, "a")
        runCurrent()

        client.requestFlush()
        client.networkAvailable()
        client.suspended()
        runCurrent()

        assertTrue(http.requests.isEmpty())
        assertEquals(1, client.pendingCount())
    }

    @Test
    fun rejectedBatchFallsBackToIndividualSendsAndDropsOnlyInvalidEvents() = runTest {
        val client = client()
        startConnected(client)
        runCurrent()
        track(client, "good_1")
        track(client, "bad")
        track(client, "good_2")
        runCurrent()

        http.enqueue(400, "batch invalid")
        http.enqueue(200)
        http.enqueue(400, "event invalid")
        http.enqueue(200)
        advanceTimeBy(FLUSH_INTERVAL_MILLIS)
        runCurrent()

        assertEquals(4, http.requests.size)
        assertEquals(listOf(3, 1, 1, 1), http.requests.map { Json.parseToJsonElement(it.body).jsonArray.size })
        assertEquals(listOf("good_1", "bad", "good_2"), sentNames().drop(3))
        assertEquals(0, client.pendingCount())
    }

    @Test
    fun individualFallbackKeepsTailWhenNetworkDropsMidway() = runTest {
        val client = client()
        startConnected(client)
        client.suspended()
        runCurrent()
        track(client, "a")
        track(client, "b")
        track(client, "c")
        runCurrent()

        http.enqueue(400)
        http.enqueue(200)
        http.enqueueFailure()
        client.requestFlush()
        runCurrent()

        assertEquals(3, http.requests.size)
        assertEquals(2, client.pendingCount())

        advanceTimeBy(1_000)
        runCurrent()
        client.requestFlush()
        runCurrent()

        assertEquals(listOf("b", "c"), sentNames().drop(5))
        assertEquals(0, client.pendingCount())
    }

    @Test
    fun cancelledFlushCallerDoesNotStrandLaterFlushes() = runTest {
        val network = GatedDispatcher()
        val client = client(networkDispatcher = network)
        startConnected(client)
        client.suspended()
        runCurrent()
        track(client, "a")
        runCurrent()

        val caller = client.requestFlush()
        runCurrent()
        caller.cancel()
        runCurrent()
        network.drain()
        runCurrent()
        assertEquals(listOf("a"), sentNames())

        track(client, "b")
        runCurrent()
        client.requestFlush()
        runCurrent()
        network.drain()
        runCurrent()

        assertEquals(listOf("a", "b"), sentNames())
        assertEquals(0, client.pendingCount())
    }
}
