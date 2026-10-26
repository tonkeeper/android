package com.tonapps.wallet.data.passcode

import android.content.Context
import android.security.keystore.UserNotAuthenticatedException
import com.tonapps.security.DeviceCredentialRequiredException
import com.tonapps.security.multichain.McPasscodeStore
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.multichain.account.McAccountRepository
import com.tonapps.wallet.data.multichain.lighter.PerpetualRepository
import com.tonapps.wallet.data.rn.RNLegacy
import com.tonapps.wallet.data.settings.SettingsRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PasscodeDeviceCredentialTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    private var passcodeStoreLocked = false
    private var vaultLocked = false

    private val settingsRepository = mockk<SettingsRepository>(relaxed = true) {
        every { isMigratedFlow } returns emptyFlow()
        every { biometric } returns false
    }

    private val helper = mockk<PasscodeHelper>(relaxed = true) {
        every { hasPinCode } answers {
            if (passcodeStoreLocked) {
                throw deviceCredentialRequired()
            }
            true
        }
        coEvery { isValid(PIN) } returns true
    }

    private val accountRepository = mockk<AccountRepository>(relaxed = true) {
        every { openVault() } answers {
            if (vaultLocked) {
                throw deviceCredentialRequired()
            }
        }
    }

    private val rnLegacy = mockk<RNLegacy>(relaxed = true) {
        coEvery { hasPinCode() } returns false
    }

    private val multichainVault = mockk<McPasscodeStore>(relaxed = true) {
        every { isSessionUnlocked } returns true
    }

    private val context = mockk<Context>(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        mockkObject(DeviceCredential)
    }

    @After
    fun tearDown() {
        unmockkObject(DeviceCredential)
        Dispatchers.resetMain()
        scope.cancel()
    }

    @Test
    fun `open passcode store skips the device credential`() = runBlocking {
        val manager = passcodeManager()

        assertTrue(manager.isValid(context, PIN))
        coVerify(exactly = 0) { DeviceCredential.confirm(any(), any()) }
    }

    @Test
    fun `locked passcode store is checked after the device credential`() = runBlocking {
        passcodeStoreLocked = true
        coEvery { DeviceCredential.confirm(context, any()) } answers {
            passcodeStoreLocked = false
            true
        }
        val manager = passcodeManager()

        assertTrue(manager.isValid(context, PIN))
        coVerify(exactly = 1) { DeviceCredential.confirm(context, any()) }
        coVerify(exactly = 1) { helper.isValid(PIN) }
    }

    @Test
    fun `declined device credential fails the passcode check`() = runBlocking {
        passcodeStoreLocked = true
        coEvery { DeviceCredential.confirm(context, any()) } returns false
        val manager = passcodeManager()

        assertFalse(manager.isValid(context, PIN))
        coVerify(exactly = 0) { helper.isValid(any()) }
    }

    @Test
    fun `passcode store still locked after the device credential fails the passcode check`() = runBlocking {
        passcodeStoreLocked = true
        coEvery { DeviceCredential.confirm(context, any()) } returns true
        val manager = passcodeManager()

        assertFalse(manager.isValid(context, PIN))
        coVerify(exactly = 0) { helper.isValid(any()) }
    }

    @Test
    fun `declined device credential for a locked vault cancels the confirmation`() = runBlocking {
        vaultLocked = true
        coEvery { DeviceCredential.confirm(context, any()) } returns false
        val manager = passcodeManager()

        assertFalse(manager.confirmation(context, TITLE))
        coVerify(exactly = 1) { DeviceCredential.confirm(context, any()) }
    }

    @Test(expected = CancellationException::class)
    fun `declined device credential cancels the passcode request`() {
        runBlocking {
            passcodeStoreLocked = true
            coEvery { DeviceCredential.confirm(context, any()) } returns false
            val manager = passcodeManager()

            manager.requestValidPasscode(context)
        }
    }

    private fun deviceCredentialRequired(): DeviceCredentialRequiredException {
        return DeviceCredentialRequiredException(UserNotAuthenticatedException())
    }

    private fun passcodeManager() = PasscodeManager(
        accountRepository = accountRepository,
        settingsRepository = settingsRepository,
        helper = helper,
        rnLegacy = rnLegacy,
        multichainVault = multichainVault,
        mcAccountRepository = mockk<McAccountRepository>(relaxed = true),
        perpetualRepository = mockk<PerpetualRepository>(relaxed = true),
        scope = scope
    )

    private companion object {
        const val PIN = "1234"
        const val TITLE = "Tonkeeper"
    }
}
