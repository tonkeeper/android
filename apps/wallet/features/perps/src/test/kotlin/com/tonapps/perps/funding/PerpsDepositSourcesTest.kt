package com.tonapps.perps.funding

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.tonapps.wallet.data.multichain.account.AccountWithDetails
import com.tonapps.wallet.data.multichain.account.AccountsWithTotal
import com.tonapps.wallet.data.multichain.account.McAccountRepository
import com.tonapps.wallet.data.multichain.account.WalletPerpsTotal
import com.tonapps.wallet.data.settings.SettingsRepository
import com.tonapps.wallet.testing.BaseUnitTest
import com.tonapps.wallet.testing.TestAssets
import com.tonapps.wallet.testing.TestWallet
import com.tonapps.wallet.testing.testAccount
import com.tonapps.wallet.testing.testAsset
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

class PerpsDepositSourcesTest : BaseUnitTest() {

    private val mcAccountRepository = mockk<McAccountRepository>()
    private val settingsRepository = mockk<SettingsRepository>()
    private val service = PerpsDepositService(
        repository = mockk(),
        journal = mockk(),
        mcAccountRepository = mcAccountRepository,
        settingsRepository = settingsRepository,
        accountService = mockk(),
        provider = mockk(),
        recovery = mockk(),
        perpetualRepository = mockk(),
    )
    private val usdcAtZero = testAccount(USDC, balance = "0")

    @Before
    fun setUp() {
        every { settingsRepository.currency.code } returns CURRENCY
        coEvery {
            mcAccountRepository.findAccount(TestWallet.ID, PerpsFundingAssetId.USDC_ETHEREUM, CURRENCY, true)
        } returns usdcAtZero
    }

    @Test
    fun aWalletWithNothingToDepositFromIsOfferedUsdcOnEthereumAtZero() = runTest {
        listAccounts()

        val sources = service.sources(TestWallet.ID)

        assertEquals(listOf(PerpsDepositSource(usdcAtZero, usdcFundingAsset)), sources)
    }

    @Test
    fun aWalletWithOnlyUnsupportedAssetsIsOfferedUsdcOnEthereumAtZero() = runTest {
        listAccounts(testAccount(TestAssets.usdtTon, balance = "1000"))

        val sources = service.sources(TestWallet.ID)

        assertEquals(listOf(PerpsDepositSource(usdcAtZero, usdcFundingAsset)), sources)
    }

    @Test
    fun aWalletWithoutUsdcOnEthereumIsOfferedItAtZeroFirst() = runTest {
        val usdt = testAccount(TestAssets.usdtEth, balance = "5000000")
        listAccounts(usdt)

        val sources = service.sources(TestWallet.ID)

        assertEquals(listOf(usdcAtZero, usdt), sources.map { it.account })
    }

    @Test
    fun aWalletHoldingUsdcOnEthereumIsOfferedWhatItHolds() = runTest {
        val usdt = testAccount(TestAssets.usdtEth, balance = "5000000")
        val usdc = testAccount(USDC, balance = "1000000")
        listAccounts(usdt, usdc)

        val sources = service.sources(TestWallet.ID)

        assertEquals(listOf(usdc, usdt), sources.map { it.account })
        coVerify(exactly = 0) { mcAccountRepository.findAccount(any(), any(), any(), any()) }
    }

    @Test
    fun aWalletWithoutAnEthereumAccountIsOfferedNothing() = runTest {
        listAccounts()
        coEvery { mcAccountRepository.findAccount(TestWallet.ID, PerpsFundingAssetId.USDC_ETHEREUM, CURRENCY, true) } returns null

        assertTrue(service.sources(TestWallet.ID).isEmpty())
    }

    @Test
    fun anOfflineWalletIsOfferedWhatItHadCachedWithoutDust() = runTest {
        val usdt = testAccount(TestAssets.usdtEth, balance = "5000000")
        coEvery {
            mcAccountRepository.fetchAccounts(
                walletId = TestWallet.ID,
                currency = CURRENCY,
                availableOnly = true,
                showHidden = true,
                cursor = null,
                capabilities = any(),
                hideDust = true,
            )
        } throws IOException("offline")
        coEvery {
            mcAccountRepository.getCachedAccounts(
                walletId = TestWallet.ID,
                currency = CURRENCY,
                availableOnly = true,
                showHidden = true,
                capabilities = any(),
                hideDust = true,
            )
        } returns withTotal(listOf(usdt))
        coEvery {
            mcAccountRepository.findAccount(TestWallet.ID, PerpsFundingAssetId.USDC_ETHEREUM, CURRENCY, true)
        } throws IOException("offline")

        val sources = service.sources(TestWallet.ID)

        assertEquals(listOf(usdt), sources.map { it.account })
    }

    @Test
    fun usdcOnEthereumThatCannotBeReadLeavesTheListEmpty() = runTest {
        listAccounts()
        coEvery {
            mcAccountRepository.findAccount(TestWallet.ID, PerpsFundingAssetId.USDC_ETHEREUM, CURRENCY, true)
        } throws IOException("offline")

        assertTrue(service.sources(TestWallet.ID).isEmpty())
    }

    private fun listAccounts(vararg accounts: AccountWithDetails) {
        coEvery {
            mcAccountRepository.fetchAccounts(
                walletId = TestWallet.ID,
                currency = CURRENCY,
                availableOnly = true,
                showHidden = true,
                cursor = null,
                capabilities = any(),
                hideDust = true,
            )
        } returns withTotal(accounts.toList())
    }

    private fun withTotal(accounts: List<AccountWithDetails>): AccountsWithTotal {
        return AccountsWithTotal(accounts = accounts, total = BigDecimal.ZERO, perpsTotal = WalletPerpsTotal.Empty)
    }

    private companion object {
        const val CURRENCY = "USD"
        val USDC = testAsset(PerpsFundingAssetId.USDC_ETHEREUM, symbol = "USDC", name = "USD Coin", decimals = 6)
        val usdcFundingAsset = checkNotNull(PerpsFundingAssetId.parse(PerpsFundingAssetId.USDC_ETHEREUM))
    }
}
