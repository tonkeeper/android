package com.tonapps.mvi.flow

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class WithLatestFromTest {

    @Test
    fun samplesTheLatestValueOfOther() = runTest {
        val trigger = MutableSharedFlow<String>(extraBufferCapacity = 8)
        val other = MutableStateFlow(1)
        val result = mutableListOf<String>()

        trigger
            .withLatestFrom(other) { value, latest -> "$value$latest" }
            .onEach { result += it }
            .launchIn(backgroundScope)
        runCurrent()

        trigger.tryEmit("a")
        runCurrent()

        other.value = 2
        trigger.tryEmit("b")
        runCurrent()

        assertEquals(listOf("a1", "b2"), result)
    }

    @Test
    fun otherDoesNotDriveTheOutput() = runTest {
        val trigger = MutableSharedFlow<String>(extraBufferCapacity = 8)
        val other = MutableStateFlow(1)
        val result = mutableListOf<String>()

        trigger
            .withLatestFrom(other) { value, latest -> "$value$latest" }
            .onEach { result += it }
            .launchIn(backgroundScope)
        runCurrent()

        trigger.tryEmit("a")
        runCurrent()

        other.value = 2
        other.value = 3
        runCurrent()

        assertEquals(listOf("a1"), result)
    }

    @Test
    fun samplesSeveralFlowsAtTheSameEmission() = runTest {
        val trigger = MutableSharedFlow<String>(extraBufferCapacity = 8)
        val first = MutableStateFlow(1)
        val second = MutableStateFlow('x')
        val third = MutableStateFlow(true)
        val result = mutableListOf<String>()

        trigger
            .withLatestFrom(first, second, third) { value, a, b, c -> "$value$a$b$c" }
            .onEach { result += it }
            .launchIn(backgroundScope)
        runCurrent()

        trigger.tryEmit("a")
        runCurrent()

        first.value = 2
        second.value = 'y'
        third.value = false
        runCurrent()

        trigger.tryEmit("b")
        runCurrent()

        assertEquals(listOf("a1xtrue", "b2yfalse"), result)
    }
}
