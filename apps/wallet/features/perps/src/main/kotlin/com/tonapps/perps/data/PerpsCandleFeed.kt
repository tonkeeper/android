package com.tonapps.perps.data

import com.tonapps.async.Async
import com.tonapps.log.L
import com.tonapps.perps.data.hermes.HermesCandleEvent
import com.tonapps.perps.data.hermes.HermesCandleSocket
import com.tonapps.wallet.api.API
import io.infrastructure.ClientException
import io.kandelabrapi.models.GetCandlesRequest
import io.kandelabrapi.models.GetCandlesResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlin.math.min
import kotlin.random.Random

class PerpsCandleFeed(private val api: API) {

    fun stream(
        symbol: String,
        timeframe: PerpsChartTimeframe,
    ): Flow<PerpsChartFeedEvent> = channelFlow {
        FeedLoop(this, symbol, timeframe).run()
    }.flowOn(Async.Io)

    private enum class SessionEnd {
        FAILED,
        REJECTED,
        DELIVERED,
        SILENT,
    }

    private sealed interface LoopInput {
        /** event == null: the socket's event channel closed. */
        data class Socket(val event: HermesCandleEvent?) : LoopInput

        /** response == null: the snapshot can never load for this feed. */
        data class Snapshot(val response: GetCandlesResponse?) : LoopInput
    }

    // Merge state is confined to the event-loop coroutine; the snapshot fetch is a child job.
    private inner class FeedLoop(
        private val scope: ProducerScope<PerpsChartFeedEvent>,
        symbol: String,
        private val timeframe: PerpsChartTimeframe,
    ) {

        private val ticker = "$symbol/USD"
        private val feedId = "$ticker/${timeframe.wireResolution}"

        // The renderer full-resets (losing scroll and zoom) when anything but the tail changes.
        private val bars = sortedMapOf<Long, PerpsCandle>()
        private var emitted: List<PerpsCandle>? = null
        private var snapshotLoaded = false

        private val snapshots = Channel<GetCandlesResponse?>(Channel.CONFLATED)
        private var snapshotJob: Job? = null

        suspend fun run() {
            try {
                var backoffMs = MIN_BACKOFF_MS
                while (true) {
                    when (runSession()) {
                        SessionEnd.FAILED -> {
                            scope.send(PerpsChartFeedEvent.FeedFailed)
                            return
                        }

                        SessionEnd.REJECTED -> {
                            // An in-flight fetch can still emit Reconnecting and flash a stale badge.
                            snapshotJob?.cancel()
                            if (emitted == null) {
                                applySnapshot(snapshots.tryReceive().getOrNull())
                            }
                            if (emitted == null) {
                                applySnapshot(loadSnapshotOnce())
                            }
                            if (emitted == null) {
                                scope.send(PerpsChartFeedEvent.FeedFailed)
                                return
                            }
                            refreshWithoutFeed()
                        }

                        SessionEnd.DELIVERED -> backoffMs = MIN_BACKOFF_MS
                        SessionEnd.SILENT -> Unit
                    }
                    scope.send(PerpsChartFeedEvent.Reconnecting)
                    val pending = snapshots.tryReceive()
                    if (pending.isSuccess && !applySnapshot(pending.getOrThrow())) {
                        scope.send(PerpsChartFeedEvent.FeedFailed)
                        return
                    }
                    if (!snapshotLoaded) {
                        ensureSnapshotFetch()
                    }
                    delay(backoffMs.withJitter())
                    backoffMs = min(backoffMs * 2, MAX_BACKOFF_MS)
                }
            } finally {
                // A FAILED exit can leave the fetch retrying; the flow cannot complete while it runs.
                snapshotJob?.cancel()
            }
        }

        private suspend fun runSession(): SessionEnd {
            val socket = HermesCandleSocket(api.hermes)
            var pingJob: Job? = null
            var delivered = false
            try {
                socket.connect()
                while (true) {
                    val input = select<LoopInput> {
                        socket.events.onReceiveCatching { LoopInput.Socket(it.getOrNull()) }
                        snapshots.onReceive { LoopInput.Snapshot(it) }
                    }
                    when (input) {
                        is LoopInput.Snapshot -> if (!applySnapshot(input.response)) {
                            return SessionEnd.FAILED
                        }

                        is LoopInput.Socket -> when (val event = input.event) {
                            HermesCandleEvent.Opened -> {
                                openFeed(socket)
                                pingJob = scope.launch { socket.keepAlive() }
                                ensureSnapshotFetch()
                            }

                            is HermesCandleEvent.Candles -> {
                                if (event.response.matchesFeed(ticker, timeframe)) {
                                    delivered = true
                                    mergeAndEmit(event.response)
                                }
                            }

                            HermesCandleEvent.FeedRejected -> return SessionEnd.REJECTED

                            HermesCandleEvent.Terminated, null -> break
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

        private fun openFeed(socket: HermesCandleSocket) {
            val token = try {
                api.hermes.accessToken()
            } catch (e: Throwable) {
                L.e(e)
                null
            }
            if (token != null) {
                socket.login(token)
            }
            socket.subscribe(feedId)
        }

        private fun ensureSnapshotFetch() {
            if (snapshotJob?.isActive == true) {
                return
            }
            snapshotJob = scope.launch { snapshots.send(fetchSnapshot()) }
        }

        private suspend fun applySnapshot(response: GetCandlesResponse?): Boolean {
            if (response == null || !response.matchesFeed(ticker, timeframe)) {
                return false
            }
            snapshotLoaded = true
            mergeAndEmit(response)
            return true
        }

        private suspend fun mergeAndEmit(response: GetCandlesResponse) {
            for (bar in response.toPerpsCandles(timeframe.stepMillis)) {
                bars[bar.time] = bar
            }
            if (!snapshotLoaded) {
                return
            }
            val merged = bars.values.toList()
            val series = if (timeframe == PerpsChartTimeframe.H12) {
                merged.aggregateHalfDay()
            } else {
                merged
            }
            // An empty series once the snapshot is in is the market's verdict, not a loading state.
            if (series == emitted) {
                return
            }
            emitted = series
            scope.send(PerpsChartFeedEvent.Series(series))
        }

        // Hermes refused this feed for good: REST keeps the history moving, the badge marks it as not live.
        private suspend fun refreshWithoutFeed(): Nothing {
            while (true) {
                scope.send(PerpsChartFeedEvent.Reconnecting)
                delay(REST_REFRESH_MS)
                applySnapshot(loadSnapshotOnce())
            }
        }

        private suspend fun loadSnapshotOnce(): GetCandlesResponse? {
            return try {
                api.kandelabr.candles.getCandles(candlesRequest())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                L.e(e)
                null
            }
        }

        private fun candlesRequest(): GetCandlesRequest {
            return GetCandlesRequest(
                ticker = ticker,
                resolution = GetCandlesRequest.Resolution.entries
                    .first { it.value == timeframe.wireResolution },
                limit = if (timeframe == PerpsChartTimeframe.H12) {
                    AGGREGATED_SNAPSHOT_BARS
                } else {
                    SNAPSHOT_BARS
                },
            )
        }

        private suspend fun fetchSnapshot(): GetCandlesResponse? {
            val request = candlesRequest()
            var backoffMs = MIN_BACKOFF_MS
            while (true) {
                try {
                    return api.kandelabr.candles.getCandles(request)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: ClientException) {
                    L.e(e)
                    if (e.statusCode == HTTP_BAD_REQUEST || e.statusCode == HTTP_NOT_FOUND) {
                        return null
                    }
                } catch (e: Throwable) {
                    L.e(e)
                }
                scope.send(PerpsChartFeedEvent.Reconnecting)
                delay(backoffMs)
                backoffMs = min(backoffMs * 2, MAX_BACKOFF_MS)
            }
        }
    }

    private companion object {
        const val SNAPSHOT_BARS = 500
        const val AGGREGATED_SNAPSHOT_BARS = 1500

        const val MIN_BACKOFF_MS = 1_000L

        // The badge's 90 s stale linger downstream assumes both caps stay here.
        const val MAX_BACKOFF_MS = 30_000L
        const val REST_REFRESH_MS = 30_000L

        const val HTTP_BAD_REQUEST = 400
        const val HTTP_NOT_FOUND = 404
    }
}

private fun GetCandlesResponse.matchesFeed(
    ticker: String,
    timeframe: PerpsChartTimeframe,
): Boolean {
    if (this.ticker.equals(ticker, ignoreCase = true) && resolution == timeframe.wireResolution) {
        return true
    }
    L.e("perps feed $ticker/${timeframe.wireResolution} got ${this.ticker}/$resolution")
    return false
}

private fun List<PerpsCandle>.aggregateHalfDay(): List<PerpsCandle> {
    val buckets = groupBy { it.time / HALF_DAY_SECONDS * HALF_DAY_SECONDS }
        .toSortedMap()
        .toList()
    // The oldest bucket can start mid-window and would draw a short bar; the newest is forming.
    val complete = if (buckets.size > 1 && buckets.first().second.size < BARS_PER_HALF_DAY) {
        buckets.drop(1)
    } else {
        buckets
    }
    return complete.map { (bucketStart, bars) ->
        PerpsCandle(
            time = bucketStart,
            open = bars.first().open,
            high = bars.maxOf { it.high },
            low = bars.minOf { it.low },
            close = bars.last().close,
            volumeUsd = bars.mapNotNull { it.volumeUsd }.takeIf { it.isNotEmpty() }?.sum(),
        )
    }
}

private fun Long.withJitter(): Long {
    return this + (this * BACKOFF_JITTER * (Random.nextDouble() * 2.0 - 1.0)).toLong()
}

private const val HALF_DAY_SECONDS = 43_200L
private const val BARS_PER_HALF_DAY = 3
private const val BACKOFF_JITTER = 0.2
