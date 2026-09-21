package com.tonapps.perps.data

import com.tonapps.async.Async
import com.tonapps.log.L
import com.tonapps.perps.data.hermes.HermesPriceEvent
import com.tonapps.perps.data.hermes.HermesPricesSocket
import com.tonapps.wallet.api.API
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.onTimeout
import kotlinx.coroutines.selects.select
import java.math.BigDecimal
import kotlin.math.min
import kotlin.random.Random

sealed interface PerpsPriceEvent {
    data class Snapshot(val prices: Map<String, BigDecimal>) : PerpsPriceEvent
    data object Reconnecting : PerpsPriceEvent

    /** Hermes refused the subscribe — no further attempt will be made for this stream. */
    data object Rejected : PerpsPriceEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
class PerpsPriceFeed(private val api: API) {

    fun stream(desiredTickers: Flow<Set<String>>): Flow<PerpsPriceEvent> = channelFlow {
        PriceLoop(this, desiredTickers).run()
    }.flowOn(Async.Io)

    private enum class SessionEnd {
        DELIVERED,
        SILENT,
        IDLE,
        REJECTED,
    }

    private sealed interface LoopInput {
        /** event == null: the socket's event channel closed. */
        data class Socket(val event: HermesPriceEvent?) : LoopInput
        data class Desired(val tickers: Set<String>) : LoopInput
        data object Timeout : LoopInput
    }

    // Session state is confined to the event-loop coroutine; only one socket exists at a time.
    private inner class PriceLoop(
        private val scope: ProducerScope<PerpsPriceEvent>,
        private val desiredTickers: Flow<Set<String>>,
    ) {

        private val updates = Channel<Set<String>>(Channel.CONFLATED)
        private var desired: Set<String> = emptySet()

        suspend fun run() {
            val updatesJob = scope.launch { desiredTickers.collect { updates.send(it) } }
            try {
                var backoffMs = MIN_BACKOFF_MS
                while (true) {
                    while (desired.isEmpty()) {
                        desired = updates.receive()
                    }
                    when (runSession()) {
                        SessionEnd.REJECTED -> {
                            scope.send(PerpsPriceEvent.Rejected)
                            return
                        }

                        SessionEnd.IDLE -> {
                            backoffMs = MIN_BACKOFF_MS
                            continue
                        }

                        SessionEnd.DELIVERED -> backoffMs = MIN_BACKOFF_MS
                        SessionEnd.SILENT -> Unit
                    }
                    scope.send(PerpsPriceEvent.Reconnecting)
                    delay(backoffMs.withJitter())
                    backoffMs = min(backoffMs * 2, MAX_BACKOFF_MS)
                    updates.tryReceive().getOrNull()?.let { desired = it }
                }
            } finally {
                // The flow cannot complete on a rejected session while this collector runs.
                updatesJob.cancel()
            }
        }

        private suspend fun runSession(): SessionEnd {
            val socket = HermesPricesSocket(api.hermes)
            var pingJob: Job? = null
            var opened = false
            var awaitingAck = false
            var delivered = false
            try {
                socket.connect()
                while (true) {
                    val input = select<LoopInput> {
                        socket.events.onReceiveCatching { LoopInput.Socket(it.getOrNull()) }
                        updates.onReceive { LoopInput.Desired(it) }
                        if (awaitingAck) {
                            onTimeout(SUBSCRIBE_TIMEOUT_MS) { LoopInput.Timeout }
                        }
                    }
                    when (input) {
                        is LoopInput.Desired -> {
                            desired = input.tickers
                            if (desired.isEmpty()) {
                                return SessionEnd.IDLE
                            }
                            if (opened) {
                                subscribe(socket)
                                awaitingAck = true
                            }
                        }

                        LoopInput.Timeout -> break

                        is LoopInput.Socket -> when (val event = input.event) {
                            HermesPriceEvent.Opened -> {
                                opened = true
                                subscribe(socket)
                                awaitingAck = true
                                pingJob = scope.launch { socket.keepAlive() }
                            }

                            HermesPriceEvent.Subscribed -> awaitingAck = false

                            is HermesPriceEvent.Prices -> {
                                awaitingAck = false
                                delivered = true
                                scope.send(PerpsPriceEvent.Snapshot(event.prices))
                            }

                            HermesPriceEvent.Rejected -> return SessionEnd.REJECTED

                            HermesPriceEvent.Terminated, null -> break
                        }
                    }
                }
            } finally {
                pingJob?.cancel()
                socket.close()
            }
            return if (delivered) {
                SessionEnd.DELIVERED
            } else {
                SessionEnd.SILENT
            }
        }

        private fun subscribe(socket: HermesPricesSocket) {
            val tickers = desired.sorted()
            L.d("perps prices subscribe ${tickers.size}")
            socket.subscribe(tickers)
        }
    }

    private companion object {
        const val MIN_BACKOFF_MS = 1_000L
        const val MAX_BACKOFF_MS = 30_000L
        const val SUBSCRIBE_TIMEOUT_MS = 30_000L
    }
}

private fun Long.withJitter(): Long {
    return this + (this * BACKOFF_JITTER * (Random.nextDouble() * 2.0 - 1.0)).toLong()
}

private const val BACKOFF_JITTER = 0.2
