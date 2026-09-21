package com.tonapps.mvi.flow

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

class CountdownTest {

    @Test
    fun ticksEverySecond() = runTest {
        val seconds = mutableListOf<Int?>()

        flowOf<Any?>(Unit)
            .countdown(total = 5.seconds, timeSource = testScheduler.timeSource)
            .onEach { seconds += it?.seconds }
            .launchIn(backgroundScope)

        advanceTimeBy(6.seconds)

        assertEquals(listOf<Int?>(5, 4, 3, 2, 1, 0), seconds)
    }

    @Test
    fun nullDataStopsTheTimer() = runTest {
        val data = MutableStateFlow<Any?>(1)
        val seconds = mutableListOf<Int?>()

        data.countdown(total = 5.seconds, timeSource = testScheduler.timeSource)
            .onEach { seconds += it?.seconds }
            .launchIn(backgroundScope)

        advanceTimeBy(2.5.seconds)
        data.value = null
        runCurrent()

        assertEquals(listOf<Int?>(5, 4, 3, null), seconds)
    }

    @Test
    fun pauseIsChargedAgainstTheDeadline() = runTest {
        val running = MutableStateFlow(true)
        val seconds = mutableListOf<Int?>()

        flowOf<Any?>(Unit)
            .countdown(
                total = 10.seconds,
                tick = 1.seconds,
                running = running,
                timeSource = testScheduler.timeSource,
            )
            .onEach { seconds += it?.seconds }
            .launchIn(backgroundScope)

        advanceTimeBy(3.5.seconds)
        assertEquals(listOf<Int?>(10, 9, 8, 7), seconds)

        running.value = false
        advanceTimeBy(30.seconds)
        runCurrent()
        assertEquals(listOf<Int?>(10, 9, 8, 7), seconds)

        running.value = true
        runCurrent()
        assertEquals(listOf<Int?>(10, 9, 8, 7, 0), seconds)
    }

    @Test
    fun shortPauseResumesWithTheRemainingTime() = runTest {
        val running = MutableStateFlow(true)
        val seconds = mutableListOf<Int?>()

        flowOf<Any?>(Unit)
            .countdown(
                total = 10.seconds,
                running = running,
                timeSource = testScheduler.timeSource,
            )
            .onEach { seconds += it?.seconds }
            .launchIn(backgroundScope)

        advanceTimeBy(2.5.seconds)
        running.value = false
        advanceTimeBy(4.seconds)
        running.value = true
        runCurrent()

        assertEquals(listOf<Int?>(10, 9, 8, 4), seconds)
    }

    @Test
    fun consecutiveFinishesAreDistinguishableByRun() = runTest {
        val data = MutableStateFlow<Any?>(1)
        val finished = mutableListOf<Int>()

        data.countdown(total = 5.seconds, timeSource = testScheduler.timeSource)
            .onEach { state ->
                if (state != null && state.finished) {
                    finished += state.run
                }
            }
            .launchIn(backgroundScope)

        advanceTimeBy(6.seconds)
        data.value = 2
        advanceTimeBy(6.seconds)

        assertEquals(listOf(0, 1), finished)
    }

    @Test
    fun perItemTotalDrivesTheDeadline() = runTest {
        val data = MutableStateFlow(3.seconds)
        val seconds = mutableListOf<Int?>()

        data.countdown(timeSource = testScheduler.timeSource) { it }
            .onEach { seconds += it?.seconds }
            .launchIn(backgroundScope)

        advanceTimeBy(4.seconds)
        data.value = 2.seconds
        advanceTimeBy(3.seconds)

        assertEquals(listOf<Int?>(3, 2, 1, 0, 2, 1, 0), seconds)
    }
}
