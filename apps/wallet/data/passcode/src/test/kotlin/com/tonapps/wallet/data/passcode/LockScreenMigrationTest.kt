package com.tonapps.wallet.data.passcode

import android.content.Context
import com.tonapps.security.multichain.McPasscodeStore
import com.tonapps.security.multichain.MnemonicCoder
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.multichain.account.McAccountRepository
import com.tonapps.wallet.data.multichain.lighter.PerpetualRepository
import com.tonapps.wallet.data.rn.RNLegacy
import com.tonapps.wallet.data.settings.SettingsRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
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
import org.junit.Test

// Old-store BIP39 wallets move to the multichain store with the PIN entry: the lock screen asks for
// the PIN even with the lock off, names why, shows a loader while they move and never gets stuck.
class LockScreenMigrationTest {

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

    private val coder = mockk<MnemonicCoder>(relaxed = true)

    private val multichainVault = mockk<McPasscodeStore>(relaxed = true) {
        coEvery { restoreSession() } returns true
        every { isSessionUnlocked } returns true
        coEvery { unlock(any()) } returns coder
    }

    private val mcAccountRepository = mockk<McAccountRepository>(relaxed = true) {
        coEvery { getWallets() } returns emptyList()
        coEvery { hasMissingChains() } returns false
        coEvery { missingChains() } returns emptyList()
    }

    private val perpetualRepository = mockk<PerpetualRepository>(relaxed = true) {
        coEvery { walletsWithoutTradingKey(any()) } returns emptyList()
    }

    private val migrator = mockk<LegacyWalletMigrator> {
        coEvery { migratableWalletIds() } returns listOf(WALLET_ID)
        coEvery { migrate(any(), any()) } returns listOf(WALLET_ID)
    }

    private val context = mockk<Context>(relaxed = true)

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun `lock screen off with a wallet to move asks for the PIN`() = runBlocking {
        val manager = passcodeManager()

        assertEquals(LockScreen.State.Input, manager.awaitLockState())
        assertFalse(manager.lockscreenRetryWithoutPasscode())
        assertEquals(PasscodeReason.WALLET_MIGRATION, manager.lockscreenReason())
    }

    @Test
    fun `the wallets move after the PIN while the loader shows`() = runBlocking {
        val manager = passcodeManager()
        manager.awaitLockState()

        manager.lockscreenCheck(context, PIN)

        withTimeout(TIMEOUT_MS) {
            manager.lockscreenFlow.first { it == LockScreen.State.None }
        }
        coVerify(exactly = 1) { migrator.migrate(listOf(WALLET_ID), coder) }
    }

    @Test
    fun `a failed move still opens the wallet`() = runBlocking {
        coEvery { migrator.migrate(any(), any()) } throws IllegalStateException("backend is down")
        val manager = passcodeManager()
        manager.awaitLockState()

        manager.lockscreenCheck(context, PIN)

        val state = withTimeout(TIMEOUT_MS) {
            manager.lockscreenFlow.first { it == LockScreen.State.None }
        }
        assertEquals(LockScreen.State.None, state)
    }

    @Test
    fun `nothing to move opens the wallet without the PIN`() = runBlocking {
        coEvery { migrator.migratableWalletIds() } returns emptyList()

        val manager = passcodeManager()

        assertEquals(LockScreen.State.None, manager.awaitLockState())
        coVerify(exactly = 0) { migrator.migrate(any(), any()) }
    }

    @Test
    fun `a failing check of the old store does not block the lock screen`() = runBlocking {
        coEvery { migrator.migratableWalletIds() } throws IllegalStateException("vault is unreadable")

        val manager = passcodeManager()

        assertEquals(LockScreen.State.None, manager.awaitLockState())
    }

    @Test
    fun `a fingerprint accepted during a wrong PIN check still opens the wallet`() = runBlocking {
        val wrongPinChecked = CompletableDeferred<Boolean>()
        coEvery { helper.isValid(WRONG_PIN) } coAnswers { wrongPinChecked.await() }
        val manager = passcodeManager()
        manager.awaitLockState()

        manager.lockscreenCheck(context, WRONG_PIN)
        manager.lockscreenBiometric()
        wrongPinChecked.complete(false)

        withTimeout(TIMEOUT_MS) {
            manager.lockscreenFlow.first { it == LockScreen.State.None }
        }
        coVerify(exactly = 1) { migrator.migrate(listOf(WALLET_ID), coder) }
    }

    @Test
    fun `a fingerprint accepted during a right PIN check does not move the wallets twice`() = runBlocking {
        val pinChecked = CompletableDeferred<Boolean>()
        coEvery { helper.isValid(PIN) } coAnswers { pinChecked.await() }
        val manager = passcodeManager()
        manager.awaitLockState()

        manager.lockscreenCheck(context, PIN)
        manager.lockscreenBiometric()
        pinChecked.complete(true)

        withTimeout(TIMEOUT_MS) {
            manager.lockscreenFlow.first { it == LockScreen.State.None }
        }
        coVerify(exactly = 1) { migrator.migrate(listOf(WALLET_ID), coder) }
    }

    private fun passcodeManager() = PasscodeManager(
        accountRepository = mockk<AccountRepository>(relaxed = true),
        settingsRepository = settingsRepository,
        helper = helper,
        rnLegacy = rnLegacy,
        multichainVault = multichainVault,
        mcAccountRepository = mcAccountRepository,
        perpetualRepository = perpetualRepository,
        scope = scope,
        legacyWalletMigrator = migrator,
    )

    private suspend fun PasscodeManager.awaitLockState(): LockScreen.State {
        return withTimeout(TIMEOUT_MS) { lockscreenFlow.first() }
    }

    private companion object {
        const val PIN = "1234"
        const val WRONG_PIN = "0000"
        const val WALLET_ID = "legacy-wallet"
        const val TIMEOUT_MS = 5_000L
    }
}
