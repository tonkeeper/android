package com.tonapps.perps.trade

import com.tonapps.chainkit.core.chain.model.account.MnemonicWallet
import com.tonapps.wallet.data.multichain.lighter.PerpetualRepository
import com.tonapps.wallet.data.passcode.PasscodeReason
import com.tonapps.wallet.data.passcode.WalletUnlocker
import com.tonapps.wallet.testing.BaseUnitTest
import com.tonapps.wallet.testing.FakeTimeProvider
import com.tonapps.wallet.testing.TestWallet
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PerpsTradingKeyTest : BaseUnitTest() {

    private val perpetualRepository = mockk<PerpetualRepository>()
    private val walletUnlocker = mockk<WalletUnlocker>()
    private val wallet = mockk<MnemonicWallet>()

    @Test
    fun aKeptKeyIsUsedWithoutThePasscode() = runTest {
        coEvery { perpetualRepository.walletsWithoutTradingKey(listOf(WALLET_ID)) } returns emptyList()

        assertTrue(service().keepTradingKey(WALLET_ID))

        coVerify(exactly = 0) { walletUnlocker.unlockWallet(any(), any()) }
    }

    @Test
    fun aMissingKeyIsKeptBehindThePasscode() = runTest {
        coEvery { perpetualRepository.walletsWithoutTradingKey(listOf(WALLET_ID)) } returns listOf(WALLET_ID)
        coEvery { walletUnlocker.unlockWallet(WALLET_ID, PasscodeReason.PERPS) } returns wallet
        coEvery { perpetualRepository.saveTradingKey(WALLET_ID, wallet) } returns Unit

        assertTrue(service().keepTradingKey(WALLET_ID))

        coVerify(exactly = 1) { perpetualRepository.saveTradingKey(WALLET_ID, wallet) }
    }

    @Test
    fun aClosedPasscodePromptKeepsNoKey() = runTest {
        coEvery { perpetualRepository.walletsWithoutTradingKey(listOf(WALLET_ID)) } returns listOf(WALLET_ID)
        coEvery { walletUnlocker.unlockWallet(WALLET_ID, PasscodeReason.PERPS) } throws CancellationException("closed")

        assertFalse(service().keepTradingKey(WALLET_ID))

        coVerify(exactly = 0) { perpetualRepository.saveTradingKey(any(), any()) }
    }

    private fun TestScope.service() = PerpsAccountService(
        repository = mockk(),
        perpetualRepository = perpetualRepository,
        client = mockk(),
        mcAccountRepository = mockk(),
        walletUnlocker = walletUnlocker,
        tierUpgrader = mockk(relaxed = true),
        timeProvider = FakeTimeProvider(),
        scope = backgroundScope,
    )

    private companion object {
        val WALLET_ID = TestWallet.ID
    }
}
