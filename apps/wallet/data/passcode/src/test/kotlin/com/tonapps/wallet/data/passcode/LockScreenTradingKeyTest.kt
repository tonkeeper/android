package com.tonapps.wallet.data.passcode

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
import org.junit.Test

class LockScreenTradingKeyTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    private val settingsRepository = mockk<SettingsRepository>(relaxed = true) {
        every { isMigratedFlow } returns flowOf(true)
        every { lockScreenDisabled } returns true
        every { biometric } returns false
    }

    private val helper = mockk<PasscodeHelper>(relaxed = true) {
        every { hasPinCode } returns true
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
        coEvery { missingChains() } returns emptyList()
    }

    private val perpetualRepository = mockk<PerpetualRepository>(relaxed = true) {
        coEvery { walletsWithoutTradingKey(any()) } returns listOf(WALLET_ID)
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun `lock screen off with a trading key owed and perps off opens the wallet without the PIN`() = runBlocking {
        val manager = passcodeManager(isPerpsEnabled = false)

        assertEquals(LockScreen.State.None, manager.awaitLockState())
    }

    @Test
    fun `lock screen off with a trading key owed and perps on asks for the PIN to unlock the wallet`() = runBlocking {
        val manager = passcodeManager(isPerpsEnabled = true)

        assertEquals(LockScreen.State.Input, manager.awaitLockState())
        assertEquals(PasscodeReason.UNLOCK_WALLET, manager.lockscreenReason())
    }

    @Test
    fun `lock screen off with no trading key owed opens the wallet without the PIN`() = runBlocking {
        coEvery { perpetualRepository.walletsWithoutTradingKey(any()) } returns emptyList()

        val manager = passcodeManager(isPerpsEnabled = true)

        assertEquals(LockScreen.State.None, manager.awaitLockState())
    }

    @Test
    fun `lock screen on with perps on names the unlock`() = runBlocking {
        every { settingsRepository.lockScreenDisabled } returns false

        val manager = passcodeManager(isPerpsEnabled = true)

        assertEquals(LockScreen.State.Input, manager.awaitLockState())
        assertEquals(PasscodeReason.UNLOCK_WALLET, manager.lockscreenReason())
    }

    @Test
    fun `missing chains are named over the trading key`() = runBlocking {
        coEvery { mcAccountRepository.missingChains() } returns listOf(Chain.Solana.Mainnet)

        val manager = passcodeManager(isPerpsEnabled = true)

        assertEquals(LockScreen.State.Input, manager.awaitLockState())
        assertEquals(PasscodeReason.ADD_NETWORKS, manager.lockscreenReason())
    }

    private fun passcodeManager(isPerpsEnabled: Boolean) = PasscodeManager(
        accountRepository = mockk<AccountRepository>(relaxed = true),
        settingsRepository = settingsRepository,
        helper = helper,
        rnLegacy = rnLegacy,
        multichainVault = multichainVault,
        mcAccountRepository = mcAccountRepository,
        perpetualRepository = perpetualRepository,
        scope = scope,
        isPerpsEnabled = { isPerpsEnabled },
    )

    private suspend fun PasscodeManager.awaitLockState(): LockScreen.State {
        return withTimeout(TIMEOUT_MS) { lockscreenFlow.first() }
    }

    private companion object {
        const val WALLET_ID = "wallet"
        const val TIMEOUT_MS = 5_000L
    }
}
