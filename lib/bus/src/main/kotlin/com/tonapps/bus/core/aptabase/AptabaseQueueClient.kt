package com.tonapps.bus.core.aptabase

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonPrimitive
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.random.Random

private val SESSION_TIMEOUT_MILLIS = TimeUnit.HOURS.toMillis(1)
private val INITIAL_BACKOFF_MILLIS = TimeUnit.SECONDS.toMillis(1)
private val MAX_BACKOFF_MILLIS = TimeUnit.MINUTES.toMillis(5)
private const val MIN_BACKOFF_JITTER = 0.8
private const val MAX_BACKOFF_JITTER = 1.2
private const val FLUSH_THRESHOLD = 50
private const val MAX_BATCHES_PER_FLUSH = 64

class AptabaseQueueClient(
    private val store: AptabaseEventStore,
    private val environment: AptabaseEnvironment,
    private val scope: CoroutineScope,
    private val networkDispatcher: CoroutineDispatcher,
    private val flushIntervalMillis: Long,
    private val clock: () -> Long,
    private val random: Random = Random.Default,
    private val sessionIdFactory: () -> String = { UUID.randomUUID().toString().lowercase() },
) {

    class PendingEvent(
        val name: String,
        val props: Map<String, JsonPrimitive>,
        val timestampMillis: Long,
    )

    private sealed interface Input {
        class Event(val pending: PendingEvent) : Input
        class Connected(val dispatcher: AptabaseDispatcher, val sharedProps: Map<String, JsonPrimitive>) : Input
        data object Resumed : Input
        data object NetworkAvailable : Input
        class Suspended(val onComplete: () -> Unit) : Input
    }

    private val inputs = Channel<Input>(Channel.UNLIMITED)

    private var sessionId = sessionIdFactory()
    private var lastTouchedMillis = clock()
    private var isStarted = false
    private var sinceFlush = 0
    private var dispatcher: AptabaseDispatcher? = null
    private var sharedProps: Map<String, JsonPrimitive> = emptyMap()
    private var consumerJob: Job? = null
    private var tickerJob: Job? = null
    private var flushJob: Job? = null
    private var backoffMillis = 0L
    private var backoffUntilMillis = 0L

    fun track(pending: PendingEvent) {
        inputs.trySend(Input.Event(pending))
    }

    fun connect(dispatcher: AptabaseDispatcher, sharedProps: Map<String, JsonPrimitive>) {
        inputs.trySend(Input.Connected(dispatcher, sharedProps))
    }

    fun resumed() {
        inputs.trySend(Input.Resumed)
    }

    fun networkAvailable() {
        inputs.trySend(Input.NetworkAvailable)
    }

    fun suspended(onComplete: () -> Unit = {}) {
        inputs.trySend(Input.Suspended(onComplete))
    }

    fun start(): Job = scope.launch {
        if (isStarted) {
            return@launch
        }
        isStarted = true
        startConsumer()
        startTicker()
    }

    fun requestFlush(): Job = scope.launch {
        flush()
    }

    suspend fun pendingCount(): Int {
        return scope.async { store.count }.await()
    }

    private fun startConsumer() {
        consumerJob = scope.launch {
            try {
                for (input in inputs) {
                    handle(input)
                }
            } finally {
                stopTicker()
            }
        }
    }

    private suspend fun handle(input: Input) {
        when (input) {
            is Input.Event -> {
                enqueue(input.pending)
                sinceFlush++
                if (sinceFlush >= FLUSH_THRESHOLD) {
                    sinceFlush = 0
                    scope.launch { flush() }
                }
            }

            is Input.Connected -> {
                dispatcher = input.dispatcher
                sharedProps = input.sharedProps
                scope.launch { flush() }
            }

            Input.Resumed -> startTicker()

            Input.NetworkAvailable -> {
                resetBackoff()
                scope.launch { flush() }
            }

            is Input.Suspended -> {
                sinceFlush = 0
                stopTicker()
                flush()
                input.onComplete()
            }
        }
    }

    private fun enqueue(pending: PendingEvent) {
        store.append(
            AptabaseEvent(
                timestamp = pending.timestampMillis,
                sessionId = sessionId(pending.timestampMillis),
                eventName = pending.name,
                systemProps = environment.systemProps,
                props = pending.props,
            )
        )
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = scope.launch {
            while (isActive) {
                delay(flushIntervalMillis)
                flush()
            }
        }
    }

    private fun stopTicker() {
        tickerJob?.cancel()
        tickerJob = null
    }

    private suspend fun flush() {
        flushJob?.let { running ->
            running.join()
            return
        }
        if (dispatcher == null || clock() < backoffUntilMillis) {
            return
        }
        val job = scope.launch {
            try {
                performFlush()
            } finally {
                flushJob = null
            }
        }
        flushJob = job
        job.join()
    }

    private suspend fun performFlush() {
        val dispatcher = dispatcher ?: return
        repeat(MAX_BATCHES_PER_FLUSH) {
            val batch = store.peek(AptabaseDispatcher.MAX_BATCH_SIZE)
            if (batch.events.isEmpty()) {
                return
            }
            val events = batch.events.map { it.copy(props = it.props + sharedProps) }
            when (send(dispatcher, events)) {
                AptabaseDispatcher.Outcome.Delivered -> {
                    store.remove(batch.upperBound)
                    resetBackoff()
                }

                AptabaseDispatcher.Outcome.Rejected -> {
                    if (events.size == 1 || sendIndividually(dispatcher, events, batch)) {
                        store.remove(batch.upperBound)
                        resetBackoff()
                    } else {
                        return
                    }
                }

                AptabaseDispatcher.Outcome.Retry -> {
                    scheduleBackoff()
                    return
                }
            }
        }
    }

    private suspend fun sendIndividually(
        dispatcher: AptabaseDispatcher,
        events: List<AptabaseEvent>,
        batch: AptabaseEventStore.Batch,
    ): Boolean {
        val lowerBound = batch.upperBound - events.size
        events.forEachIndexed { index, event ->
            if (send(dispatcher, listOf(event)) == AptabaseDispatcher.Outcome.Retry) {
                store.remove(lowerBound + index)
                scheduleBackoff()
                return false
            }
        }
        return true
    }

    private suspend fun send(dispatcher: AptabaseDispatcher, events: List<AptabaseEvent>): AptabaseDispatcher.Outcome {
        return withContext(networkDispatcher) {
            dispatcher.send(events)
        }
    }

    private fun resetBackoff() {
        backoffMillis = 0L
        backoffUntilMillis = 0L
    }

    private fun scheduleBackoff() {
        backoffMillis = if (backoffMillis == 0L) {
            INITIAL_BACKOFF_MILLIS
        } else {
            minOf(backoffMillis * 2, MAX_BACKOFF_MILLIS)
        }
        val jitter = random.nextDouble(MIN_BACKOFF_JITTER, MAX_BACKOFF_JITTER)
        backoffUntilMillis = clock() + (backoffMillis * jitter).toLong()
    }

    private fun sessionId(atMillis: Long): String {
        if (atMillis - lastTouchedMillis > SESSION_TIMEOUT_MILLIS) {
            sessionId = sessionIdFactory()
        }
        lastTouchedMillis = atMillis
        return sessionId
    }
}
