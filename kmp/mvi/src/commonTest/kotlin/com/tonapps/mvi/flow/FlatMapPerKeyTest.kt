package com.tonapps.mvi.flow

import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class FlatMapPerKeyTest {

    private val started = mutableListOf<String>()
    private val cancelled = mutableListOf<String>()

    /** Emits "<key>:1" right away and never completes, recording when it starts and stops. */
    private fun itemFlow(item: String): Flow<String> = flow {
        started += item
        emit("$item:1")
        awaitCancellation()
    }.onCompletion { cancelled += item }

    @Test
    fun startsOneFlowPerKey() = runTest {
        val items = MutableStateFlow(listOf("a", "b"))
        val result = mutableListOf<Map<String, String>>()

        items.flatMapPerKey(key = { it }, transform = ::itemFlow)
            .onEach { result += it }
            .launchIn(backgroundScope)
        runCurrent()

        assertEquals(listOf("a", "b"), started)
        assertEquals(mapOf("a" to "a:1", "b" to "b:1"), result.last())
    }

    @Test
    fun reorderingTheKeysRestartsNothing() = runTest {
        val items = MutableStateFlow(listOf("a", "b"))
        val result = mutableListOf<Map<String, String>>()

        items.flatMapPerKey(key = { it }, transform = ::itemFlow)
            .onEach { result += it }
            .launchIn(backgroundScope)
        runCurrent()
        val emitted = result.size

        items.value = listOf("b", "a")
        runCurrent()

        assertEquals(listOf("a", "b"), started)
        assertEquals(emptyList(), cancelled)
        assertEquals(emitted, result.size)
    }

    @Test
    fun keyThatLeavesIsCancelledAndDropped() = runTest {
        val items = MutableStateFlow(listOf("a", "b"))
        val result = mutableListOf<Map<String, String>>()

        items.flatMapPerKey(key = { it }, transform = ::itemFlow)
            .onEach { result += it }
            .launchIn(backgroundScope)
        runCurrent()

        items.value = listOf("a")
        runCurrent()

        assertEquals(listOf("b"), cancelled)
        assertEquals(mapOf("a" to "a:1"), result.last())
    }

    @Test
    fun keyThatJoinsStartsWithoutTouchingTheOthers() = runTest {
        val items = MutableStateFlow(listOf("a", "b"))
        val result = mutableListOf<Map<String, String>>()

        items.flatMapPerKey(key = { it }, transform = ::itemFlow)
            .onEach { result += it }
            .launchIn(backgroundScope)
        runCurrent()

        items.value = listOf("a", "c")
        runCurrent()

        assertEquals(listOf("a", "b", "c"), started)
        assertEquals(listOf("b"), cancelled)
        assertEquals(mapOf("a" to "a:1", "c" to "c:1"), result.last())
    }

    @Test
    fun keyThatReturnsStartsAFreshFlow() = runTest {
        val items = MutableStateFlow(listOf("a"))

        items.flatMapPerKey(key = { it }, transform = ::itemFlow)
            .launchIn(backgroundScope)
        runCurrent()

        items.value = emptyList()
        runCurrent()
        items.value = listOf("a")
        runCurrent()

        assertEquals(listOf("a", "a"), started)
        assertEquals(listOf("a"), cancelled)
    }

    @Test
    fun lateValueOfAKeyThatLeftNeverLands() = runTest {
        val items = MutableStateFlow(listOf("a", "slow"))
        val result = mutableListOf<Map<String, String>>()

        items
            .flatMapPerKey(key = { it }) { item ->
                flow {
                    if (item == "slow") {
                        delay(100)
                    }
                    emit("$item:1")
                    awaitCancellation()
                }
            }
            .onEach { result += it }
            .launchIn(backgroundScope)
        runCurrent()

        items.value = listOf("a")
        advanceTimeBy(200)
        runCurrent()

        assertEquals(mapOf("a" to "a:1"), result.last())
        assertEquals(false, result.any { "slow" in it })
    }

    @Test
    fun everyValueOfAKeyReplacesItsPrevious() = runTest {
        val items = MutableStateFlow(listOf("a", "b"))
        val result = mutableListOf<Map<String, String>>()

        items
            .flatMapPerKey(key = { it }) { item ->
                flow {
                    emit("$item:1")
                    if (item == "a") {
                        delay(100)
                        emit("$item:2")
                    }
                    awaitCancellation()
                }
            }
            .onEach { result += it }
            .launchIn(backgroundScope)
        runCurrent()
        advanceTimeBy(200)
        runCurrent()

        assertEquals(mapOf("a" to "a:2", "b" to "b:1"), result.last())
    }

    @Test
    fun itemIsTheOneItsKeyArrivedWith() = runTest {
        val items = MutableStateFlow(listOf("a" to 1))
        val result = mutableListOf<Map<String, Int>>()

        items
            .flatMapPerKey(key = { it.first }) { (_, version) ->
                flow {
                    emit(version)
                    awaitCancellation()
                }
            }
            .onEach { result += it }
            .launchIn(backgroundScope)
        runCurrent()

        items.value = listOf("a" to 2)
        runCurrent()

        assertEquals(mapOf("a" to 1), result.last())
    }
}
