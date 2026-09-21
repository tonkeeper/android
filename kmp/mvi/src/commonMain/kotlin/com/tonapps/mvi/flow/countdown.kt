package com.tonapps.mvi.flow

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.withIndex
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

data class Countdown(
    val run: Int,
    val total: Duration,
    val left: Duration,
) {
    val finished: Boolean get() = left <= Duration.ZERO

    val seconds: Int get() = ((left.inWholeMilliseconds + 999) / 1000).toInt()

    val progress: Float get() {
        if (!total.isPositive()) {
            return 0f
        }

        return (left / total).toFloat().coerceIn(0f, 1f)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
fun <T> Flow<T>.countdown(
    tick: Duration = 1.seconds,
    running: Flow<Boolean> = flowOf(true),
    timeSource: TimeSource = TimeSource.Monotonic,
    total: (T) -> Duration?,
): Flow<Countdown?> = withIndex().flatMapLatest { (run, value) ->
    val duration = total(value)
    if (duration == null) {
        flowOf(null)
    } else {
        countdown(run, duration, tick, running, timeSource)
    }
}

fun <T : Any> Flow<T?>.countdown(
    total: Duration,
    tick: Duration = 1.seconds,
    running: Flow<Boolean> = flowOf(true),
    timeSource: TimeSource = TimeSource.Monotonic,
): Flow<Countdown?> = countdown(tick, running, timeSource) { value ->
    if (value == null) {
        null
    } else {
        total
    }
}

private fun countdown(
    run: Int,
    total: Duration,
    tick: Duration,
    running: Flow<Boolean>,
    timeSource: TimeSource,
): Flow<Countdown> = flow {
    val mark = timeSource.markNow()

    while (true) {
        running.first { it }

        val left = total - mark.elapsedNow()
        if (left <= Duration.ZERO) {
            break
        }

        emit(Countdown(run, total, left))
        delay(minOf(tick, left))
    }

    emit(Countdown(run, total, Duration.ZERO))
}
