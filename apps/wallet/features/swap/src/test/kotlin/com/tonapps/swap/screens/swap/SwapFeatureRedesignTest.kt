package com.tonapps.swap.screens.swap

import android.os.SystemClock
import com.tonapps.blockchain.model.ConfirmType
import com.tonapps.core.flags.BootFlags
import com.tonapps.core.flags.WalletFeatureKey
import com.tonapps.deposit.multicoin.screens.confirm.SwapFeePlan
import com.tonapps.deposit.multicoin.screens.confirm.SwapFeeSummary
import com.tonapps.wallet.testing.BaseUnitTest
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SwapFeatureRedesignTest : BaseUnitTest() {

    private lateinit var env: SwapTestEnv

    private var clock = 0L

    @Before
    fun setUp() {
        env = SwapTestEnv(context, timeSource, features)
        features.set(WalletFeatureKey.IS_SWAP_REDESIGN_ENABLED, true)
        BootFlags.update(BootFlags.values.copy(oneClickSwap = true))
        mockkStatic(SystemClock::class)
        every { SystemClock.elapsedRealtime() } answers { ++clock }
    }

    @After
    fun tearDown() {
        unmockkStatic(SystemClock::class)
    }

    @Test
    fun withTheRolloutFlagAndTheBootFlagTheScreenSendsInPlace() {
        assertTrue(env.feature().sendsInPlace)
    }

    // The boot config can turn one-click off where the rollout flag has it on.
    @Test
    fun theRolloutFlagAloneDoesNotSendInPlace() {
        BootFlags.update(BootFlags.values.copy(oneClickSwap = false))

        assertFalse(env.feature().sendsInPlace)
    }

    // A boot config that allows one-click does not turn it on before the rollout flag does.
    @Test
    fun theBootFlagAloneDoesNotSendInPlace() {
        features.set(WalletFeatureKey.IS_SWAP_REDESIGN_ENABLED, false)

        assertFalse(env.feature().sendsInPlace)
    }

    @Test
    fun percentSellsAShareOfTheBalanceAboveTheReserve() = swapTest(env) {
        val quarter = after { sendAction(SwapAction.Amount.Percent(25)) }

        assertEquals("1", quarter.amountInput.text)
        assertFalse(quarter.amountInput.isMax)
        assertEquals("1000000000", quoteRequests.single().sourceAmount)
    }

    @Test
    fun swapIsDisabledWhileTheQuoteRefreshes() = swapTest(env) {
        after { sendAction(SwapAction.Amount.SetSell("1")) }
        planned()
        assertEquals(SwapButton.Swap(enabled = true), feature.swapButton.value)

        send { sendAction(SwapAction.Requote) }
        pass(timings.quoteDebounce / 2)
        assertEquals(SwapButton.Swap(enabled = false), feature.swapButton.value)

        after()
        assertEquals(2, quoteRequests.size)
        planned()
        assertEquals(SwapButton.Swap(enabled = true), feature.swapButton.value)
    }

    @Test
    fun amountBelowTheReserveAsksForTheMinimum() = swapTest(env) {
        after { sendAction(SwapAction.Amount.SetSell("0.5")) }

        assertTrue(feature.swapButton.value.toString(), feature.swapButton.value is SwapButton.Minimum)
    }

    // The screen decided one-click when it was built: the request says so even once the boot flag has turned off.
    @Test
    fun theSendRequestCarriesTheScreensOneClickDecision() = swapTest(env) {
        after { sendAction(SwapAction.Amount.SetSell("1")) }
        planned()
        BootFlags.update(BootFlags.values.copy(oneClickSwap = false))

        after { sendAction(SwapAction.Continue) }

        val swap = checkNotNull(feature.send.value).request.type as ConfirmType.Swap
        assertTrue(swap.oneClick)
    }

    @Test
    fun aNewQuoteDoesNotTouchASendingAttempt() = swapTest(env) {
        after { sendAction(SwapAction.Amount.SetSell("1")) }
        planned()
        after { sendAction(SwapAction.Continue) }
        val sending = feature.send.value
        assertEquals(SwapSendPhase.Working, sending?.phase)

        after { sendAction(SwapAction.Requote) }

        assertEquals(2, quoteRequests.size)
        assertSame(sending, feature.send.value)
    }

    private fun SwapScreen.planned() {
        val attempt = checkNotNull(feature.send.value)
        after {
            sendAction(SwapAction.FeePlanned(attempt.id, SwapFeePlan.Ready(SwapFeeSummary.Charges(7), attempt.quoteReceivedAt)))
            sendAction(SwapAction.Prepared(attempt.id, true))
        }
    }
}
