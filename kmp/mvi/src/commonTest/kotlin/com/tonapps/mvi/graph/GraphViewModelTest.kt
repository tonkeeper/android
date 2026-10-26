package com.tonapps.mvi.graph

import com.tonapps.async.Async
import com.tonapps.mvi.contract.MviAction
import com.tonapps.mvi.contract.MviViewState
import com.tonapps.mvi.flow.withLatestFrom
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext

/**
 * What a graph node promises, independent of any screen: when a loader starts, when it is cancelled,
 * and what the node shows in between.
 *
 * Main uses UnconfinedTestDispatcher for eager subscriptions; this is not a proof of Android
 * Main.immediate scheduling. Every [Async] pool is standard, so
 * `withContext(Async.Io)` really suspends, and all of them share one virtual clock.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GraphViewModelTest {

    @Test
    fun latestEmitsLoadingThenData() = graphTest { graph ->
        val states = record(graph.latest)

        graph.sendAction(TestAction.Latest(1))
        advanceUntilIdle()

        assertEquals(listOf(KState.Loading(), KState.Data("v1")), states)
    }

    /** The next input cancels the load in flight — its answer is never built, let alone shown. */
    @Test
    fun theNextInputCancelsTheLoadInFlight() {
        val log = LoadLog()

        graphTest(load = log::load) { graph ->
            val states = record(graph.latest)

            graph.sendAction(TestAction.Latest(1))
            advanceTimeBy(LOAD / 2)
            graph.sendAction(TestAction.Latest(2))
            advanceUntilIdle()

            assertEquals(listOf(1, 2), log.started)
            assertEquals(listOf(1), log.cancelled)
            assertEquals(listOf(2), log.finished)
            assertEquals(KState.Data("v2"), graph.latest.value)
            assertTrue(states.none { it.state == "v1" }, "the cancelled load reached the node: $states")
        }
    }

    /**
     * Cancellation that the loader swallows must still not emit: `runCatching` catches
     * [CancellationException] like any other, so `ensureActive` after it is the only thing between
     * a stale answer and the screen.
     */
    @Test
    fun aLoaderThatSwallowsItsCancellationStillNeverEmits() {
        val swallowing: suspend (Int) -> String = { value ->
            runCatching { delay(LOAD) }
            "v$value"
        }

        graphTest(load = swallowing) { graph ->
            val states = record(graph.latest)

            graph.sendAction(TestAction.Latest(1))
            advanceTimeBy(LOAD / 2)
            graph.sendAction(TestAction.Latest(2))
            advanceUntilIdle()

            assertEquals(KState.Data("v2"), graph.latest.value)
            assertTrue(states.none { it.state == "v1" }, "a swallowed cancellation emitted: $states")
        }
    }

    /** Loading carries the data it is replacing, so a screen can keep showing it while it reloads. */
    @Test
    fun loadingCarriesTheDataItReplaces() = graphTest { graph ->
        val states = record(graph.latest)

        graph.sendAction(TestAction.Latest(1))
        advanceUntilIdle()
        graph.sendAction(TestAction.Latest(2))
        runCurrent()

        assertEquals(KState.Loading("v1"), graph.latest.value)
        assertEquals(
            listOf(KState.Loading(), KState.Data("v1"), KState.Loading("v1")),
            states,
        )
    }

    /** First is not re-entrant: what arrives while it is busy is dropped, not queued. */
    @Test
    fun firstDropsWhatArrivesWhileItIsBusy() {
        val log = LoadLog()

        graphTest(load = log::load) { graph ->
            graph.sendAction(TestAction.First(1))
            advanceTimeBy(LOAD / 2)
            graph.sendAction(TestAction.First(2))
            advanceUntilIdle()

            assertEquals(listOf(1), log.started)
            assertEquals(KState.Data("v1"), graph.first.value)
        }
    }

    /** Dropped is not deaf: once the loader is done, the node takes the next input. */
    @Test
    fun firstTakesTheNextInputOnceItIsDone() {
        val log = LoadLog()

        graphTest(load = log::load) { graph ->
            graph.sendAction(TestAction.First(1))
            advanceUntilIdle()
            graph.sendAction(TestAction.First(2))
            advanceUntilIdle()

            assertEquals(listOf(1, 2), log.started)
            assertEquals(KState.Data("v2"), graph.first.value)
        }
    }

    /**
     * A loader that throws emits nothing at all: the node keeps whatever it had and stays Loading.
     * Nothing in [KState] says "failed", so a screen that must recover needs its own retry.
     */
    @Test
    fun aFailedLoadLeavesTheNodeLoading() {
        var fail = true
        val failing: suspend (Int) -> String = { value ->
            delay(LOAD)
            if (fail) {
                throw IllegalStateException("boom")
            }
            "v$value"
        }

        graphTest(load = failing) { graph ->
            graph.sendAction(TestAction.Latest(1))
            advanceUntilIdle()

            assertEquals(KState.Loading(), graph.latest.value)

            // The failure is not fatal to the node: the next input is loaded as usual.
            fail = false
            graph.sendAction(TestAction.Latest(2))
            advanceUntilIdle()

            assertEquals(KState.Data("v2"), graph.latest.value)
        }
    }

    /** Every node is started with the graph, before anything collects it. */
    @Test
    fun nodesRunBeforeAnyoneCollectsThem() = graphTest { graph ->
        assertEquals(KState.Loading(), graph.once.value)

        advanceUntilIdle()

        assertEquals(KState.Data("once"), graph.once.value)
    }

    /** An action reaches the node that asked for its type, and no other. */
    @Test
    fun anActionReachesOnlyTheNodeThatAskedForIt() {
        val log = LoadLog()

        graphTest(load = log::load) { graph ->
            graph.sendAction(TestAction.Latest(1))
            advanceUntilIdle()

            assertEquals(listOf(1), log.started)
            assertEquals(KState.Loading(), graph.first.value)
        }
    }

    /** Clearing the ViewModel cancels what is in flight instead of letting it land later. */
    @Test
    fun clearCancelsTheLoadInFlight() {
        val log = LoadLog()

        graphTest(load = log::load) { graph ->
            graph.sendAction(TestAction.Latest(1))
            advanceTimeBy(LOAD / 2)

            graph.close()
            advanceUntilIdle()

            assertEquals(listOf(1), log.cancelled)
            assertEquals(emptyList(), log.finished)
            assertEquals(KState.Loading(), graph.latest.value)
        }
    }

    /** No scheduler drain between the edit and the command that samples its derived state. */
    @Test
    fun nextActionSamplesBothUpdatedBranches() = graphTest { graph ->
        graph.sendAction(TestAction.Edit(1))
        graph.sendAction(TestAction.Sample)
        graph.sendAction(TestAction.Edit(2))
        graph.sendAction(TestAction.Sample)

        assertEquals(listOf(1 to 10, 2 to 20), graph.samples)
    }

    /** A back edge is emitted during propagation, not from the test after the graph settles. */
    @Test
    fun backEdgeSamplesThePublishedJoinedState() = graphTest { graph ->
        graph.sendAction(TestAction.Edit(3, sampleBackEdge = true))

        assertEquals(listOf(3 to 30), graph.samples)
    }

    @Test
    fun nextActionCannotSampleDataFromBeforeReload() = graphTest { graph ->
        graph.sendAction(TestAction.Latest(1))
        advanceUntilIdle()
        graph.sendAction(TestAction.Latest(2))
        graph.sendAction(TestAction.SampleLatest)

        assertEquals(listOf<String?>(null), graph.latestSamples)
        advanceUntilIdle()
        graph.sendAction(TestAction.SampleLatest)
        assertEquals(listOf(null, "v2"), graph.latestSamples)
    }

    /** The old completion and the edit have the same timestamp; test both explicit orderings. */
    @Test
    fun newInputAtTheCompletionDeadlineCancelsTheOldAnswer() = graphTest { graph ->
        val states = record(graph.latest)
        graph.sendAction(TestAction.Latest(1))
        advanceTimeBy(LOAD)
        graph.sendAction(TestAction.Latest(2))
        runCurrent()
        advanceUntilIdle()

        assertTrue(states.none { it == KState.Data("v1") }, states.toString())
        assertEquals(KState.Data("v2"), graph.latest.value)
    }

    @Test
    fun completionBeforeNewInputIsInvalidatedBeforeSampling() = graphTest { graph ->
        graph.sendAction(TestAction.Latest(1))
        advanceTimeBy(LOAD)
        runCurrent()
        assertEquals(KState.Data("v1"), graph.latest.value)

        graph.sendAction(TestAction.Latest(2))
        graph.sendAction(TestAction.SampleLatest)
        assertEquals(listOf<String?>(null), graph.latestSamples)
    }

    @Test
    fun slowCancellationCleanupNeverPublishesTheCancelledAnswer() {
        val enteredCleanup = CompletableDeferred<Unit>()
        val releaseCleanup = CompletableDeferred<Unit>()
        val started = mutableListOf<Int>()
        graphTest(load = { value ->
            started += value
            if (value == 1) {
                try {
                    delay(LOAD)
                } finally {
                    withContext(NonCancellable) {
                        enteredCleanup.complete(Unit)
                        releaseCleanup.await()
                    }
                }
            }
            "v$value"
        }) { graph ->
            val states = record(graph.latest)
            graph.sendAction(TestAction.Latest(1))
            graph.sendAction(TestAction.Latest(2))
            runCurrent()
            try {
                assertTrue(enteredCleanup.isCompleted)
                assertEquals(listOf(1), started, "replacement must wait for cancellation cleanup")
            } finally {
                releaseCleanup.complete(Unit)
            }
            advanceUntilIdle()

            assertEquals(listOf(1, 2), started)
            assertTrue(states.none { it == KState.Data("v1") }, states.toString())
            assertEquals(KState.Data("v2"), graph.latest.value)
        }
    }

    @Test
    fun firstRecoversAfterFailure() = graphTest(load = { value ->
        delay(LOAD)
        check(value != 1) { "boom" }
        "v$value"
    }) { graph ->
        graph.sendAction(TestAction.First(1))
        advanceUntilIdle()
        assertEquals(KState.Loading(), graph.first.value)
        graph.sendAction(TestAction.First(2))
        advanceUntilIdle()
        assertEquals(KState.Data("v2"), graph.first.value)
    }

    @Test
    fun firstDropsBackToBackActionsBeforeAnySchedulerAdvance() {
        val log = LoadLog()
        graphTest(load = log::load) { graph ->
            graph.sendAction(TestAction.First(1))
            graph.sendAction(TestAction.First(2))
            advanceUntilIdle()
            assertEquals(listOf(1), log.started)
            assertEquals(listOf(1), log.finished)
        }
    }

    /** First only excludes overlapping work; synchronous completions must not leave it busy. */
    @Test
    fun synchronousLoadersAcceptConsecutiveInputs() = graphTest(load = { "v$it" }) { graph ->
        graph.sendAction(TestAction.First(1))
        assertEquals(KState.Data("v1"), graph.first.value)
        graph.sendAction(TestAction.First(2))
        assertEquals(KState.Data("v2"), graph.first.value)
        graph.sendAction(TestAction.Latest(1))
        assertEquals(KState.Data("v1"), graph.latest.value)
        graph.sendAction(TestAction.Latest(2))
        assertEquals(KState.Data("v2"), graph.latest.value)
    }

    @Test
    fun clearSuppressesFirstResultEvenWhenLoaderSwallowsCancellation() = graphTest(load = {
        runCatching { delay(LOAD) }
        "late"
    }) { graph ->
        val states = record(graph.first)
        graph.sendAction(TestAction.First(1))
        graph.close()
        advanceUntilIdle()
        graph.sendAction(TestAction.First(2))
        advanceUntilIdle()

        assertEquals(listOf(KState.Loading()), states)
    }

    /**
     * Boundary of latest's guarantee: cancellation cannot retract an answer already emitted into
     * its output buffer. A suspending downstream operator must not treat that answer as current.
     */
    @Test
    fun latestDoesNotRetractDataAlreadyBufferedBeforeReplacement() = graphTest { graph ->
        val input = MutableSharedFlow<Int>(extraBufferCapacity = 8)
        val releaseConsumer = CompletableDeferred<Unit>()
        val completed = mutableListOf<Int>()
        val delivered = mutableListOf<KState<String>>()
        var blockFirstEmission = true
        val node = with(graph) {
            input.transformStateLatest { value ->
                completed += value
                "v$value"
            }.onEach {
                if (blockFirstEmission) {
                    blockFirstEmission = false
                    releaseConsumer.await()
                }
                // Observe before stateIn: StateFlow may conflate the buffered burst when released.
                delivered += it
            }.cacheStateWithLoading()
        }

        input.tryEmit(1)
        input.tryEmit(2)
        assertEquals(listOf(1, 2), completed)
        assertEquals(KState.Loading(), node.value)
        releaseConsumer.complete(Unit)
        runCurrent()

        assertEquals(
            listOf(KState.Loading(), KState.Data("v1"), KState.Loading("v1"), KState.Data("v2")),
            delivered,
        )
        assertEquals(KState.Data("v2"), node.value)
    }

    /** The action bus promises DROP_OLDEST, not lossless delivery to a suspended subscriber. */
    @Test
    fun actionOverflowKeepsTheNewestActionsForASlowSubscriber() = graphTest { graph ->
        val releaseConsumer = CompletableDeferred<Unit>()
        val received = mutableListOf<Int>()
        graph.observeEdits { value ->
            received += value
            if (value == 0) releaseConsumer.await()
        }
        graph.sendAction(TestAction.Edit(0))
        assertEquals(listOf(0), received)

        for (value in 1..100) graph.sendAction(TestAction.Edit(value))
        releaseConsumer.complete(Unit)
        runCurrent()

        assertEquals(listOf(0) + (37..100), received)
        // Other, non-suspending subscribers must still have reached the newest input.
        graph.sendAction(TestAction.Sample)
        assertEquals(listOf(100 to 1000), graph.samples)
    }

    /** Starts collecting immediately; a StateFlow can still conflate updates in a queued burst. */
    private fun <T> TestScope.record(flow: Flow<T>): List<T> {
        val values = mutableListOf<T>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            flow.collect { values += it }
        }
        return values
    }

    /**
     * Runs [body] against a graph built on the test's clock. The dispatchers are set before the
     * graph is built on purpose: [com.tonapps.mvi.AsyncViewModel] samples them in its constructor.
     */
    private fun graphTest(
        load: suspend (Int) -> String = { value ->
            delay(LOAD)
            "v$value"
        },
        body: suspend TestScope.(TestGraph) -> Unit,
    ): TestResult = runTest {
        val io: CoroutineDispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        Async.setIoDispatcher(io)
        Async.setDefaultDispatcher(io)
        Async.setStateDispatcher(io)
        Async.setStateDiffDispatcher(io)

        val graph = TestGraph(load)
        try {
            body(graph)
        } finally {
            graph.close()
            runCurrent()
            Dispatchers.resetMain()
            Async.setIoDispatcher(null)
            Async.setDefaultDispatcher(null)
            Async.setStateDispatcher(null)
            Async.setStateDiffDispatcher(null)
        }
    }

    private companion object {
        val LOAD = 100.milliseconds
    }
}

/** What each load did, in order, for the tests that are about cancellation. */
private class LoadLog {

    val started = mutableListOf<Int>()
    val finished = mutableListOf<Int>()
    val cancelled = mutableListOf<Int>()

    suspend fun load(value: Int): String {
        started += value
        try {
            delay(100.milliseconds)
        } catch (e: CancellationException) {
            cancelled += value
            throw e
        }
        finished += value

        return "v$value"
    }
}

private interface TestViewState : MviViewState

private sealed interface TestAction : MviAction {
    data class Latest(val value: Int) : TestAction
    data class First(val value: Int) : TestAction
    data class Edit(val value: Int, val sampleBackEdge: Boolean = false) : TestAction
    data object Sample : TestAction
    data object SampleLatest : TestAction
}

/** One node per producer the graph offers, all fed from the same action bus. */
private class TestGraph(
    private val load: suspend (Int) -> String,
) : GraphViewModel<TestViewState, TestAction>() {

    val latest = actions
        .on<TestAction.Latest>()
        .map { it.value }
        .transformStateLatest { load(it) }
        .cacheStateWithLoading()

    val first = actions
        .on<TestAction.First>()
        .map { it.value }
        .transformStateFirst { load(it) }
        .cacheStateWithLoading()

    val once = loadState {
        delay(100.milliseconds)
        "once"
    }
        .cacheStateWithLoading()

    // Independent paths from one input intentionally rejoin before a command samples them.
    private val input = actions.on<TestAction.Edit>()
        .cacheState(initialValue = TestAction.Edit(0))
    private val left = input.map { it.value }.cacheState(initialValue = 0)
    private val right = input.map { it.value * 10 }.cacheState(initialValue = 0)
    private val joined = combine(left, right) { a, b -> a to b }
        .cacheState(initialValue = 0 to 0)

    val samples = mutableListOf<Pair<Int, Int>>()
    private val sampleEffect = actions.on<TestAction.Sample>()
        .withLatestFrom(joined) { _, value -> samples.add(value) }
        .cacheState(initialValue = false)

    private val backEdge = joined.onEach { (a, b) ->
        if (input.value.sampleBackEdge && a == input.value.value && b == a * 10) {
            sendAction(TestAction.Sample)
        }
    }.cacheState(initialValue = 0 to 0)

    private val latestData = latest.mapStateDataValueOrNull().cacheState(initialValue = null)
    val latestSamples = mutableListOf<String?>()
    private val sampleLatestEffect = actions.on<TestAction.SampleLatest>()
        .withLatestFrom(latestData) { _, value -> latestSamples.add(value) }
        .cacheState(initialValue = false)

    fun observeEdits(consume: suspend (Int) -> Unit) = actions.on<TestAction.Edit>()
        .onEach { consume(it.value) }
        .cacheState(initialValue = null)

    // onCleared is protected on ViewModel, and cancelling the graph is what a test needs.
    fun close() = onCleared()
}
