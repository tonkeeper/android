package com.tonapps.test.screen

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * The value form of one screen's state: every node it renders, sampled at one instant.
 *
 * A [ScreenDriver] hands these back instead of letting a test read a dozen flows one at a time.
 * What the driver needs from the type is only how to tell a busy screen from a settled one; the
 * rest is the screen's own business.
 */
interface ScreenSnapshot {

    /** The screen is working on something, so a settle must not stop here. */
    val isBusy: Boolean get() = false

    /**
     * Busy for good: nothing more will happen however long the test waits — a screen that failed
     * to open and left a node loading forever. Without this a settle would spend its whole budget
     * and then fail on a screen that was as finished as it will ever be.
     */
    val isStuck: Boolean get() = false

    /**
     * This state without the parts that never stop changing, for the "did anything move?"
     * comparison — a countdown ticks for as long as the screen is up and says nothing about
     * whether it settled. Defaults to the whole state.
     */
    val stable: Any get() = this

    /** What this screen may never render, whatever route it took to get here. */
    fun assertCoherent() = Unit
}

/**
 * How a settle is spent: [step] at a time, at most [steps] of them.
 *
 * The product is the budget — it must outlast the slowest thing one action can start, and stay
 * well inside any timer the screen restarts on its own, or a settle would trip it. A smaller
 * [step] costs iterations and buys precision: the settle always overshoots the moment the screen
 * went quiet by less than one step.
 *
 * [quiet] is how long nothing may emit before a state counts as rendered — a change propagates
 * through the nodes within one frame, so combinations that exist only inside that cascade are
 * never seen by a screen and must not be sampled either.
 */
data class SettleTiming(
    val step: Duration = 100.milliseconds,
    val steps: Int = 30,
    val quiet: Duration = 1.milliseconds,
) {
    val budget: Duration get() = step * steps
}
