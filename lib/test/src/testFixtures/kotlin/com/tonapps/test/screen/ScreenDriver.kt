package com.tonapps.test.screen

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlin.time.Duration

/**
 * One screen under test: actions go in, [ScreenSnapshot]s come out.
 *
 * A test says what the user did and reads what the screen then showed — the scheduler stays in
 * here. There are four ways to move:
 *
 *  - [after] — do this, then let the screen stop changing. The one to reach for.
 *  - [pass] — let exactly this much time pass, for the states that only exist mid-flight.
 *  - [send] — do this and let no time pass, for the moment right after a tap.
 *  - [now] — what is on screen this instant.
 *
 * Everything [after] and [pass] return has been through [ScreenSnapshot.assertCoherent], so a test
 * only asserts what it came to assert. [send] and [now] do not check: inside a cascade the nodes
 * are half-applied on purpose, and that is exactly what those two are for.
 *
 * Subclass it to add what a particular screen's tests keep reaching for — the events it emitted,
 * the requests it sent.
 */
@OptIn(ExperimentalCoroutinesApi::class)
open class ScreenDriver<F : Any, S : ScreenSnapshot>(
    protected val scope: TestScope,
    val feature: F,
    private val timing: SettleTiming = SettleTiming(),
    watch: F.() -> List<Flow<*>> = { emptyList() },
    private val snapshot: F.() -> S,
) {

    /**
     * Every state the screen settled on, including the ones between two actions, where a
     * half-applied combination would hide. Empty unless the screen named the flows to watch.
     *
     * Settled is the whole point: a change takes a cascade of node updates to land, and the
     * combinations that exist only inside one are not sampled — see [SettleTiming.quiet]. Nothing
     * here is checked on its own; a test that cares runs [ScreenSnapshot.assertCoherent] over it.
     */
    val rendered: List<S> = recordSettledStates(feature.watch())

    /** Sends [act], waits until the screen stops changing, and returns what it settled on. */
    fun after(act: F.() -> Unit = {}): S {
        feature.act()
        settle()
        return checked()
    }

    /**
     * Sends [act] and lets no virtual time pass: the graph runs on an immediate main dispatcher, so
     * what comes back is the screen with everything but the suspending work already applied — the
     * moment a tap reaches a loader that has not answered yet.
     */
    fun send(act: F.() -> Unit): S {
        feature.act()
        return now()
    }

    /**
     * Lets exactly [duration] pass and no more: whatever comes due at the far end of it has not
     * run yet. The screen may well still be busy afterwards — that is the point.
     */
    fun pass(duration: Duration): S {
        scope.advanceTimeBy(duration)
        return checked()
    }

    /** What the screen shows this instant. */
    fun now(): S = feature.snapshot()

    /**
     * Every value [flow] emits from here on, in order — unfiltered, unlike [rendered], for the
     * assertions that are about a value never reaching the screen at all rather than about a state
     * it settled on.
     *
     * Subscribed where it is called and not at the next advance: a screen that emits on open would
     * otherwise fire into a flow with no replay and no collector yet, and the test would read that
     * silence as nothing having happened. What the feature does inside its own constructor stays
     * out of reach either way — its flows do not exist until it does.
     */
    fun <T> record(flow: Flow<T>): List<T> {
        val values = mutableListOf<T>()
        scope.backgroundScope.launch(UnconfinedTestDispatcher(scope.testScheduler)) {
            flow.collect { values += it }
        }
        return values
    }

    /**
     * Cancels whatever the screen keeps running; called however the test ends. A feature that keeps
     * a timer cycling has to be stopped, or `runTest` never sees the scheduler go quiet and the run
     * hangs instead of failing.
     */
    open fun close() = Unit

    /**
     * Advances until two frames in a row are the same and the screen is no longer busy.
     *
     * Bounded on purpose: a screen that restarts a timer of its own never lets the scheduler idle,
     * so `advanceUntilIdle` would be an uninterruptible busy loop — not even a test timeout can
     * break it. This one runs out of budget and says which states it gave up on.
     */
    private fun settle() {
        var previous = now().stable
        repeat(timing.steps) {
            scope.advanceTimeBy(timing.step)
            scope.runCurrent()

            val next = now()
            // A request in flight changes nothing until it answers, so "the same twice" is not
            // enough on its own — a spinner is not a settled screen.
            if (next.stable == previous && (!next.isBusy || next.isStuck)) {
                return
            }
            previous = next.stable
        }
        // An AssertionError and not an ISE: the screen ran, it just never met the expectation, and
        // JUnit files the two differently.
        throw AssertionError("the screen never went quiet in ${timing.budget}:$trail")
    }

    /**
     * The last frames the screen rendered, for a settle that ran out of budget. One frame cannot
     * tell a screen frozen on a single value apart from one flapping between two, and those are
     * different bugs. Empty unless the screen named the flows to watch, so [now] closes the list.
     */
    private val trail: String
        get() = (rendered.takeLast(TRAIL_FRAMES) + now())
            .joinToString(separator = "\n  ", prefix = "\n  ")

    private fun recordSettledStates(watched: List<Flow<*>>): List<S> {
        val states = mutableListOf<S>()
        if (watched.isEmpty()) {
            return states
        }

        scope.backgroundScope.launch {
            merge(*watched.toTypedArray())
                .transformLatest {
                    delay(timing.quiet)
                    emit(now())
                }
                .distinctUntilChangedBy { it.stable }
                .collect { states += it }
        }
        return states
    }

    private fun checked() = now().also { it.assertCoherent() }

    private companion object {
        const val TRAIL_FRAMES = 5
    }
}
