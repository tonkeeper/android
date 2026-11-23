package com.tonapps.wallet.data.passcode

import androidx.fragment.app.FragmentActivity
import com.tonapps.chainkit.core.chain.model.account.MnemonicWallet
import com.tonapps.security.multichain.MnemonicCoder
import com.tonapps.wallet.data.multichain.account.McAccountRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ForegroundWalletUnlockerTest {

    private val passcodeManager = mockk<PasscodeManager>()
    private val accountRepository = mockk<McAccountRepository>()
    private val tracker = ForegroundActivityTracker()
    private val activity = mockk<FragmentActivity>(relaxed = true)
    private val coder = mockk<MnemonicCoder>()
    private val unlocker = ForegroundWalletUnlocker(passcodeManager, accountRepository, tracker)

    init {
        coEvery { passcodeManager.unlockMultichainVault<Any?>(activity, REASON, any()) } coAnswers {
            thirdArg<suspend (MnemonicCoder) -> Any?>().invoke(coder)
        }
    }

    @Test
    fun noForegroundScreenReadsAsAClosedPrompt() = runTest {
        val error = unlockFailure { unlocker.unlockVault(REASON) { } }

        assertTrue(error is CancellationException)
        assertEquals(ForegroundWalletUnlocker.FOREGROUND_WAIT_MS, testScheduler.currentTime)
        coVerify(exactly = 0) { passcodeManager.unlockMultichainVault<Any?>(any(), any(), any()) }
    }

    @Test
    fun aPausedScreenIsNotAskedOn() = runTest {
        tracker.onActivityResumed(activity)
        tracker.onActivityPaused(activity)

        val error = unlockFailure { unlocker.unlockVault(REASON) { } }

        assertTrue(error is CancellationException)
        coVerify(exactly = 0) { passcodeManager.unlockMultichainVault<Any?>(any(), any(), any()) }
    }

    @Test
    fun aScreenResumingWithinTheWaitGetsThePrompt() = runTest {
        val unlocked = async { unlocker.unlockVault(REASON) { "opened" } }
        advanceTimeBy(ForegroundWalletUnlocker.FOREGROUND_WAIT_MS / 2)
        runCurrent()

        // A rotation: the new screen resumes after the old one went away.
        tracker.onActivityResumed(activity)

        assertEquals("opened", unlocked.await())
        coVerify(exactly = 1) { passcodeManager.unlockMultichainVault<Any?>(activity, REASON, any()) }
    }

    @Test
    fun unlockWalletReturnsTheWalletOfTheOpenVault() = runTest {
        val wallet = mockk<MnemonicWallet>()
        coEvery { accountRepository.getCryptoWallet("wallet", coder) } returns wallet
        tracker.onActivityResumed(activity)

        assertSame(wallet, unlocker.unlockWallet("wallet", REASON))
    }

    @Test
    fun unlockWalletFailsForAMissingWallet() = runTest {
        coEvery { accountRepository.getCryptoWallet("wallet", coder) } returns null
        tracker.onActivityResumed(activity)

        val error = unlockFailure { unlocker.unlockWallet("wallet", REASON) }

        // CancellationException is an IllegalStateException too, and would read as a closed prompt.
        assertTrue(error is IllegalStateException && error !is CancellationException)
    }

    @Test
    fun confirmAsksOnTheForegroundScreen() = runTest {
        coEvery { passcodeManager.confirmation(activity, REASON) } returns true
        tracker.onActivityResumed(activity)

        assertTrue(unlocker.confirm(REASON))
    }

    @Test
    fun confirmWithoutAForegroundScreenIsNotConfirmed() = runTest {
        assertFalse(unlocker.confirm(REASON))
        coVerify(exactly = 0) { passcodeManager.confirmation(any(), any()) }
    }

    private suspend fun unlockFailure(unlock: suspend () -> Unit): Throwable {
        try {
            unlock()
        } catch (e: Throwable) {
            return e
        }
        throw AssertionError("the unlock went through")
    }

    private companion object {
        val REASON = PasscodeReason.CONFIRM_TRANSFER
    }
}
