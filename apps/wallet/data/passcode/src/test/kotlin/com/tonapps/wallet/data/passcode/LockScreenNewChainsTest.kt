package com.tonapps.wallet.data.passcode

import android.content.Context
import com.tonapps.chainkit.core.chain.model.account.Chain
import com.tonapps.security.multichain.McPasscodeStore
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.multichain.account.McAccountRepository
import com.tonapps.wallet.data.multichain.lighter.PerpetualRepository
import com.tonapps.wallet.data.rn.RNLegacy
import com.tonapps.wallet.data.settings.SettingsRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// A user with a PIN and the lock screen off: the wallets missing chains must still go through the
// PIN entry, since only it can derive them and show the new networks screen.
class LockScreenNewChainsTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    private val settingsRepository = mockk<SettingsRepository>(relaxed = true) {
        every { isMigratedFlow } returns flowOf(true)
        every { lockScreenDisabled } returns true
        every { biometric } returns false
    }

    private val helper = mockk<PasscodeHelper>(relaxed = true) {
        every { hasPinCode } returns true
        coEvery { isValid(PIN) } returns true
        coEvery { getPinCode() } returns PIN
    }

    private val rnLegacy = mockk<RNLegacy>(relaxed = true) {
        coEvery { hasPinCode() } returns false
    }

    private val multichainVault = mockk<McPasscodeStore>(relaxed = true) {
        coEvery { restoreSession() } returns true
        every { isSessionUnlocked } returns true
    }

    private val mcAccountRepository = mockk<McAccountRepository>(relaxed = true) {
        coEvery { getWallets() } returns emptyList()
        coEvery { hasMissingChains() } returns false
    }

    private val perpetualRepository = mockk<PerpetualRepository>(relaxed = true) {
        coEvery { walletsWithoutTradingKey(any()) } returns emptyList()
    }

    private val context = mockk<Context>(relaxed = true)

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun `lock screen off with missing chains asks for the PIN`() = runBlocking {
        coEvery { mcAccountRepository.missingChains() } returns listOf(Chain.Solana.Mainnet)

        val manager = passcodeManager()

        assertEquals(LockScreen.State.Input, manager.awaitLockState())
        assertFalse(manager.lockscreenRetryWithoutPasscode())
    }

    @Test
    fun `lock screen off with missing chains and biometrics asks for biometrics`() = runBlocking {
        every { settingsRepository.biometric } returns true
        coEvery { mcAccountRepository.missingChains() } returns listOf(Chain.Solana.Mainnet)

        val manager = passcodeManager()

        assertEquals(LockScreen.State.Biometric, manager.awaitLockState())
    }

    @Test
    fun `lock screen off with missing chains shows new networks after the PIN`() = runBlocking {
        coEvery { mcAccountRepository.missingChains() } returns listOf(Chain.Solana.Mainnet)
        val manager = passcodeManager()
        manager.awaitLockState()

        manager.lockscreenCheck(context, PIN)

        val state = withTimeout(TIMEOUT_MS) {
            manager.lockscreenFlow.first { it == LockScreen.State.NewChains }
        }
        assertEquals(LockScreen.State.NewChains, state)
        assertEquals(listOf(Chain.Solana.Mainnet), manager.newChainsFlow.value?.chains)
    }

    @Test
    fun `lock screen off without missing chains opens the wallet without the PIN`() = runBlocking {
        coEvery { mcAccountRepository.missingChains() } returns emptyList()

        val manager = passcodeManager()

        assertEquals(LockScreen.State.None, manager.awaitLockState())
        assertTrue(manager.newChainsFlow.value == null)
    }

    @Test
    fun `lock screen on with missing chains asks for the PIN`() = runBlocking {
        every { settingsRepository.lockScreenDisabled } returns false
        coEvery { mcAccountRepository.missingChains() } returns listOf(Chain.Solana.Mainnet)

        val manager = passcodeManager()

        assertEquals(LockScreen.State.Input, manager.awaitLockState())
    }

    private fun passcodeManager() = PasscodeManager(
        accountRepository = mockk<AccountRepository>(relaxed = true),
        settingsRepository = settingsRepository,
        helper = helper,
        rnLegacy = rnLegacy,
        multichainVault = multichainVault,
        mcAccountRepository = mcAccountRepository,
        perpetualRepository = perpetualRepository,
        scope = scope
    )

    private suspend fun PasscodeManager.awaitLockState(): LockScreen.State {
        return withTimeout(TIMEOUT_MS) { lockscreenFlow.first() }
    }

    private companion object {
        const val PIN = "1234"
        const val TIMEOUT_MS = 5_000L
    }
}
