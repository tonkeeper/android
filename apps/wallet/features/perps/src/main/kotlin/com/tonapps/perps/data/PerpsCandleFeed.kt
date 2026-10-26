package com.tonapps.perps.data

import com.tonapps.async.Async
import com.tonapps.log.L
import com.tonapps.perps.data.hermes.HermesCandleEvent
import com.tonapps.perps.data.hermes.HermesCandleSocket
import com.tonapps.wallet.api.API
import io.infrastructure.ClientException
import io.kandelabrapi.models.GetCandlesBatchRequest
import io.kandelabrapi.models.GetCandlesBatchResponse
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
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

class PerpsCandleFeed(private val api: API) {

    fun stream(
        symbol: String,
        timeframe: PerpsChartTimeframe,
    ): Flow<PerpsChartFeedEvent> = channelFlow {
        FeedLoop(this, symbol, timeframe).run()
    }.flowOn(Async.Io)

    suspend fun olderCandles(
        symbol: String,
        timeframe: PerpsChartTimeframe,
        endTimeSeconds: Long,
    ): List<PerpsCandle>? = withContext(Async.Io) {
        try {
            val ticker = "$symbol/USD"
            val request = historyRequest(ticker, timeframe, endTsMillis = endTimeSeconds * MILLIS_IN_SECOND)
            val history = api.kandelabr.candles.getCandlesBatch(request).toHistory(ticker, timeframe)
                ?: return@withContext null
            val bars = history.candles.withVolumes(history.volumes)
            val series = if (timeframe == PerpsChartTimeframe.H12) {
                bars.aggregateHalfDay()
            } else {
                bars
            }
            series.filter { it.time < endTimeSeconds }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            L.e(e)
            null
        }
    }

    private enum class SessionEnd {
        FAILED,
        REJECTED,
        DELIVERED,
        SILENT,
        RELOGIN,
    }

    private sealed interface LoopInput {
        /** event == null: the socket's event channel closed. */
        data class Socket(val event: HermesCandleEvent?) : LoopInput

        /** history == null: the snapshot can never load for this feed. */
        data class Snapshot(val history: PerpsCandleHistory?) : LoopInput
    }

    // Merge state is confined to the event-loop coroutine; the snapshot fetch is a child job.
    private inner class FeedLoop(
        private val scope: ProducerScope<PerpsChartFeedEvent>,
        symbol: String,
        private val timeframe: PerpsChartTimeframe,
    ) {

        private val ticker = "$symbol/USD"
        private val priceFeedId = "$ticker/${timeframe.wireResolution}"
        private val volumeFeedId = "${ticker.volumeTicker()}/${timeframe.wireResolution}"

        // The renderer full-resets (losing scroll and zoom) when a non-tail bar's price changes.
        private val bars = sortedMapOf<Long, PerpsCandle>()
        private val volumes = HashMap<Long, Double>()
        private var emitted: List<PerpsCandle>? = null
        private var snapshotLoaded = false

        private val snapshots = Channel<PerpsCandleHistory?>(Channel.CONFLATED)
        private var snapshotJob: Job? = null
        private var loginRefreshUsed = false

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

                        SessionEnd.RELOGIN -> continue
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
            var volumeRetryJob: Job? = null
            var volumeBackoffMs = MIN_BACKOFF_MS
            var delivered = false
            var loginToken: String? = null
            try {
                socket.connect()
                while (true) {
                    val input = select<LoopInput> {
                        socket.events.onReceiveCatching { LoopInput.Socket(it.getOrNull()) }
                        snapshots.onReceive { LoopInput.Snapshot(it) }
                    }
                    when (input) {
                        is LoopInput.Snapshot -> if (!applySnapshot(input.history)) {
                            return SessionEnd.FAILED
                        }

                        is LoopInput.Socket -> when (val event = input.event) {
                            HermesCandleEvent.Opened -> {
                                loginToken = openFeed(socket)
                                pingJob = scope.launch { socket.keepAlive() }
                                ensureSnapshotFetch()
                            }

                            is HermesCandleEvent.Candles -> {
                                if (event.feedId.equals(priceFeedId, ignoreCase = true)) {
                                    if (event.response.matchesFeed(ticker, timeframe)) {
                                        delivered = true
                                        mergeAndEmit(event.response.toPerpsCandles(timeframe.stepMillis))
                                    }
                                } else if (event.feedId.equals(volumeFeedId, ignoreCase = true)) {
                                    if (event.response.resolution == timeframe.wireResolution) {
                                        volumeBackoffMs = MIN_BACKOFF_MS
                                        mergeVolumes(event.response.toBaseVolumes(timeframe.stepMillis))
                                        mergeAndEmit(emptyList())
                                    }
                                }
                            }

                            HermesCandleEvent.LoggedIn -> loginRefreshUsed = false

                            HermesCandleEvent.LoginRejected -> if (!loginRefreshUsed) {
                                loginRefreshUsed = true
                                loginToken?.let { expired ->
                                    try {
                                        api.hermes.refreshAccessToken(expired)
                                    } catch (e: Throwable) {
                                        L.e(e)
                                    }
                                }
                                return SessionEnd.RELOGIN
                            }

                            is HermesCandleEvent.FeedError -> when {
                                event.feedId.equals(priceFeedId, ignoreCase = true) -> {
                                    if (event.permanent) {
                                        return SessionEnd.REJECTED
                                    }
                                    break
                                }
                                event.feedId.equals(volumeFeedId, ignoreCase = true) -> {
                                    if (!event.permanent && volumeRetryJob?.isActive != true) {
                                        val retryDelayMs = volumeBackoffMs.withJitter()
                                        volumeRetryJob = scope.launch {
                                            delay(retryDelayMs)
                                            socket.subscribe(volumeFeedId)
                                        }
                                        volumeBackoffMs = min(volumeBackoffMs * 2, MAX_BACKOFF_MS)
                                    }
                                }

                                else -> break
                            }

                            HermesCandleEvent.Terminated, null -> break
                        }
                    }
                }
            } finally {
                pingJob?.cancel()
                volumeRetryJob?.cancel()
                socket.close()
            }
            return if (delivered) {
                SessionEnd.DELIVERED
            } else {
                SessionEnd.SILENT
            }
        }

        private fun openFeed(socket: HermesCandleSocket): String? {
            val token = try {
                api.hermes.accessToken()
            } catch (e: Throwable) {
                L.e(e)
                null
            }
            if (token != null) {
                socket.login(token)
            }
            socket.subscribe(priceFeedId)
            socket.subscribe(volumeFeedId)
            return token
        }

        private fun ensureSnapshotFetch() {
            if (snapshotJob?.isActive == true) {
                return
            }
            snapshotJob = scope.launch { snapshots.send(fetchSnapshot()) }
        }

        private suspend fun applySnapshot(history: PerpsCandleHistory?): Boolean {
            if (history == null) {
                return false
            }
            mergeVolumes(history.volumes)
            snapshotLoaded = true
            mergeAndEmit(history.candles)
            return true
        }

        private fun mergeVolumes(incoming: Map<Long, Double>) {
            for ((time, volume) in incoming) {
                volumes.merge(time, volume) { old, new -> max(old, new) }
            }
        }

        private suspend fun mergeAndEmit(candles: List<PerpsCandle>) {
            for (bar in candles) {
                bars[bar.time] = bar
            }
            if (!snapshotLoaded) {
                return
            }
            val merged = bars.values.toList().withVolumes(volumes)
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

        private suspend fun loadSnapshotOnce(): PerpsCandleHistory? {
            return try {
                api.kandelabr.candles.getCandlesBatch(historyRequest(ticker, timeframe, endTsMillis = null))
                    .toHistory(ticker, timeframe)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                L.e(e)
                null
            }
        }

        private suspend fun fetchSnapshot(): PerpsCandleHistory? {
            val request = historyRequest(ticker, timeframe, endTsMillis = null)
            var backoffMs = MIN_BACKOFF_MS
            while (true) {
                try {
                    return api.kandelabr.candles.getCandlesBatch(request).toHistory(ticker, timeframe)
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

    private fun historyRequest(
        ticker: String,
        timeframe: PerpsChartTimeframe,
        endTsMillis: Long?,
    ): GetCandlesBatchRequest {
        return GetCandlesBatchRequest(
            requests = listOf(
                candlesRequest(ticker, timeframe, endTsMillis),
                candlesRequest(ticker.volumeTicker(), timeframe, endTsMillis),
            ),
        )
    }

    private fun candlesRequest(
        ticker: String,
        timeframe: PerpsChartTimeframe,
        endTsMillis: Long?,
    ): GetCandlesRequest {
        return GetCandlesRequest(
            ticker = ticker,
            resolution = GetCandlesRequest.Resolution.entries
                .first { it.value == timeframe.wireResolution },
            endTs = endTsMillis,
            limit = if (timeframe == PerpsChartTimeframe.H12) {
                AGGREGATED_SNAPSHOT_BARS
            } else {
                SNAPSHOT_BARS
            },
        )
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
        const val MILLIS_IN_SECOND = 1_000L
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

private fun GetCandlesBatchResponse.toHistory(
    ticker: String,
    timeframe: PerpsChartTimeframe,
): PerpsCandleHistory? {
    val prices = responses.getOrNull(0)
    if (prices == null || !prices.matchesFeed(ticker, timeframe)) {
        return null
    }
    val volumes = responses.getOrNull(1)
        ?.takeIf { it.matchesFeed(ticker.volumeTicker(), timeframe) }
        ?.toBaseVolumes(timeframe.stepMillis)
        .orEmpty()
    return PerpsCandleHistory(prices.toPerpsCandles(timeframe.stepMillis), volumes)
}

private fun String.volumeTicker(): String = "$this/$VOLUME_INSTRUMENT"

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
private const val VOLUME_INSTRUMENT = "PERP_TRADE"
