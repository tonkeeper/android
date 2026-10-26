package com.tonapps.swap.screens.swap

import com.tonapps.swap.SwapRoutes
import com.tonapps.test.screen.ScreenDriver
import com.tonapps.wallet.testing.BaseUnitTest
import com.tonapps.wallet.testing.screenTest
import io.exchangeapi.models.CreateCrossSwapQuoteRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.TestScope
import org.junit.Assert.assertEquals

/**
 * The swap screen under test. Everything about driving it lives in [ScreenDriver] — this adds the
 * three things swap tests keep reaching for, and the nodes worth watching between actions.
 */
class SwapScreen(
    scope: TestScope,
    val env: SwapTestEnv,
    feature: SwapFeature,
) : ScreenDriver<SwapFeature, SwapStateSnapshot>(
    scope = scope,
    feature = feature,
    watch = {
        listOf<Flow<*>>(
            initState,
            amountInput,
            sellAccount,
            buyAccount,
            isLoading,
            quote,
            quoteErrorMessageRes,
            insufficientFunds,
            continueLoading,
            continueEnabled,
        )
    },
    snapshot = { viewState() },
) {

    /** Every one-shot event the screen emitted, in order. */
    val events = record(feature.events)

    /** Every quote request that went out, in order. */
    val quoteRequests: List<CreateCrossSwapQuoteRequest> get() = env.quoteRequests

    /** The screen's own timings, for the tests that are about them. */
    val timings: SwapTimings get() = env.timings

    override fun close() {
        env.clear()
    }
}

/**
 * Opens the swap screen on [env] and runs [body] against it. Pass `awaitReady = false` for the
 * tests that are about a screen which never becomes ready.
 */
fun BaseUnitTest.swapTest(
    env: SwapTestEnv,
    route: SwapRoutes.Swap = SwapRoutes.Swap(),
    awaitReady: Boolean = true,
    body: suspend SwapScreen.() -> Unit,
): TestResult = screenTest(
    open = { SwapScreen(this, env, env.feature(route)) },
) {
    if (awaitReady) {
        val opened = now()
        assertEquals("the screen never opened: $opened", SwapInitState.Ready, opened.initState)
    }
    body()
}
