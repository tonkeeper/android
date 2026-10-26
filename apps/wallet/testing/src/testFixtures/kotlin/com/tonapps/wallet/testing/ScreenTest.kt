package com.tonapps.wallet.testing

import com.tonapps.test.screen.ScreenDriver
import com.tonapps.test.screen.ScreenSnapshot
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/**
 * Opens one screen on virtual time and runs [body] against it.
 *
 * [open] builds the driver — a [ScreenDriver] or a subclass of it carrying whatever that screen's
 * tests keep reaching for. The screen is given time to open before [body] runs, and
 * [ScreenDriver.close] is called whatever happens.
 */
fun <F : Any, S : ScreenSnapshot, D : ScreenDriver<F, S>> BaseUnitTest.screenTest(
    open: TestScope.() -> D,
    body: suspend D.() -> Unit,
): TestResult = runTest(dispatchers.scheduler) {
    val screen = open()
    var failed = false
    try {
        screen.after()
        screen.body()
    } catch (throwable: Throwable) {
        failed = true
        throw throwable
    } finally {
        val closed = runCatching {
            screen.close()
            runCurrent()
        }
        // A screen that fails to close says nothing about the test; it must not stand in for the
        // failure that got it into that state.
        if (!failed) {
            closed.getOrThrow()
        }
    }
}
