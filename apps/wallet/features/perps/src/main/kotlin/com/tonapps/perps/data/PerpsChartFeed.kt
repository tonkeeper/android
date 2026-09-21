package com.tonapps.perps.data

import com.tonapps.mvi.graph.KResult

sealed interface PerpsChartFeedEvent {
    data class Series(val candles: List<PerpsCandle>) : PerpsChartFeedEvent
    data object Reconnecting : PerpsChartFeedEvent

    /** The feed can never deliver for this market — terminal until the user retries. */
    data object FeedFailed : PerpsChartFeedEvent
    data object Timeout : PerpsChartFeedEvent
    data class Selection(val active: Boolean) : PerpsChartFeedEvent
}

/** staleEpoch: null = live; bumped per reconnect so the badge's linger timer restarts downstream. */
data class PerpsChartSeries(
    val candles: List<PerpsCandle>,
    val staleEpoch: Long? = null,
)

data class PerpsChartFeedState(
    val held: List<PerpsCandle>? = null,
    val emitted: List<PerpsCandle>? = null,
    val parked: List<PerpsCandle>? = null,
    val selectionActive: Boolean = false,
    val staleEpoch: Long? = null,
    // Monotonic: staleEpoch resets on live frames, and a repeated epoch value would be
    // swallowed by distinctUntilChanged downstream, skipping the linger restart.
    val lastStaleEpoch: Long = 0L,
    val poisoned: Boolean = false,
)

data class PerpsChartFeedStep(
    val state: PerpsChartFeedState,
    val emission: KResult<PerpsChartSeries, PerpsError>? = null,
)

fun PerpsChartFeedState.onChartFeedEvent(event: PerpsChartFeedEvent): PerpsChartFeedStep {
    if (poisoned) {
        return PerpsChartFeedStep(state = this)
    }

    return when (event) {
        is PerpsChartFeedEvent.Series -> onSeries(event.candles)
        is PerpsChartFeedEvent.Reconnecting -> onReconnecting()
        is PerpsChartFeedEvent.Timeout -> onTimeout()
        is PerpsChartFeedEvent.Selection -> onSelection(event.active)
        is PerpsChartFeedEvent.FeedFailed -> PerpsChartFeedStep(
            state = copy(poisoned = true),
            emission = KResult.Err(PerpsError.Unknown),
        )
    }
}

private fun PerpsChartFeedState.onSeries(candles: List<PerpsCandle>): PerpsChartFeedStep {
    if (selectionActive) {
        return PerpsChartFeedStep(state = copy(held = candles, parked = candles, staleEpoch = null))
    }
    return PerpsChartFeedStep(
        state = copy(held = candles, emitted = candles, parked = null, staleEpoch = null),
        emission = KResult.Ok(PerpsChartSeries(candles)),
    )
}

private fun PerpsChartFeedState.onReconnecting(): PerpsChartFeedStep {
    if (held == null) {
        return PerpsChartFeedStep(state = this)
    }

    val epoch = lastStaleEpoch + 1L
    // Never re-publish `held` — it may be a frame parked behind an active selection.
    val emission = emitted?.let { KResult.Ok<PerpsChartSeries, PerpsError>(PerpsChartSeries(it, epoch)) }
    return PerpsChartFeedStep(
        state = copy(staleEpoch = epoch, lastStaleEpoch = epoch),
        emission = emission,
    )
}

private fun PerpsChartFeedState.onTimeout(): PerpsChartFeedStep {
    if (held != null) {
        return PerpsChartFeedStep(state = this)
    }
    // The feed reports an empty market itself, so silence this long is a stuck feed, not no data.
    return PerpsChartFeedStep(state = this, emission = KResult.Err(PerpsError.Unknown))
}

private fun PerpsChartFeedState.onSelection(active: Boolean): PerpsChartFeedStep {
    if (!active && parked != null) {
        return PerpsChartFeedStep(
            state = copy(selectionActive = false, emitted = parked, parked = null),
            emission = KResult.Ok(PerpsChartSeries(parked, staleEpoch)),
        )
    }
    return PerpsChartFeedStep(state = copy(selectionActive = active))
}
