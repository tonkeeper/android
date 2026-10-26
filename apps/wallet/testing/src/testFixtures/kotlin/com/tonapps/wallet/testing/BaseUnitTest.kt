package com.tonapps.wallet.testing

import android.content.Context
import com.tonapps.test.TestDispatchersRule
import org.junit.Rule

/** Everything a graph feature needs from Android and from the clock, in one place. */
abstract class BaseUnitTest {

    // Lower order is the outer rule: the dispatchers are in place before a global is initialized on
    // top of them, and are only reset once that global is gone.
    @get:Rule(order = 0)
    val dispatchers = TestDispatchersRule()

    @get:Rule(order = 1)
    val globals = AndroidGlobalsRule()

    protected val context: Context get() = globals.context

    protected val features: TestRemoteConfig get() = globals.features

    protected val timeSource get() = dispatchers.scheduler.timeSource
}
