package com.tonapps.test

import com.tonapps.async.Async
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * Puts every dispatcher a feature can reach on one [TestCoroutineScheduler], so a test drives time
 * instead of waiting for it: debounces, TTL timers and countdowns pass in `advanceTimeBy`.
 *
 * [main] is unconfined so eagerly cached nodes start at construction, the way `Main.immediate` does
 * on device. The [Async] pools are standard, so `withContext(Async.Io)` really suspends — that is
 * what makes "the second tap arrives while the first is still in flight" reproducible.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TestDispatchersRule(
    val scheduler: TestCoroutineScheduler = TestCoroutineScheduler(),
) : TestWatcher() {

    val main: TestDispatcher = UnconfinedTestDispatcher(scheduler)
    val io: TestDispatcher = StandardTestDispatcher(scheduler)

    override fun starting(description: Description) {
        Dispatchers.setMain(main)
        Async.setIoDispatcher(io)
        Async.setDefaultDispatcher(io)
        Async.setStateDispatcher(io)
        Async.setStateDiffDispatcher(io)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
        Async.setIoDispatcher(null)
        Async.setDefaultDispatcher(null)
        Async.setStateDispatcher(null)
        Async.setStateDiffDispatcher(null)
    }
}
