package com.tonapps.perps.funding

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.tonapps.wallet.data.multichain.account.AccountWithDetails
import com.tonapps.wallet.data.multichain.account.AccountsWithTotal
import com.tonapps.wallet.data.multichain.account.McAccountRepository
import com.tonapps.wallet.data.multichain.account.WalletPerpsTotal
import com.tonapps.wallet.data.settings.SettingsRepository
import com.tonapps.wallet.testing.BaseUnitTest
import com.tonapps.wallet.testing.FakeTimeProvider
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
import org.junit.Assert.assertFalse
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
        feeMethods = mockk(),
        timeProvider = FakeTimeProvider(),
        networkFees = mockk(),
        securityConfig = mockk(),
    )
    private val usdcAtZero = testAccount(USDC, balance = "0")

    @Before
    fun setUp() {
        every { settingsRepository.currency.code } returns CURRENCY
        coEvery {
            mcAccountRepository.findAccount(TestWallet.ID, PerpsFundingAssetId.USDC_ETHEREUM, CURRENCY, true)
        } returns usdcAtZero
        coEvery {
            mcAccountRepository.findAccount(TestWallet.ID, PerpsFundingAssetId.USDT_TON, CURRENCY, true)
        } returns null
    }

    @Test
    fun aWalletWithNothingToDepositFromIsOfferedUsdcOnEthereumAtZero() = runTest {
        listAccounts()

        val sources = service.sources(TestWallet.ID).items

        assertEquals(listOf(PerpsDepositSource(usdcAtZero, usdcFundingAsset)), sources)
    }

    @Test
    fun aWalletWithOnlyUnsupportedAssetsIsOfferedUsdcOnEthereumAtZero() = runTest {
        listAccounts(testAccount(NOT_A_SOURCE, balance = "1000"))

        val sources = service.sources(TestWallet.ID).items

        assertEquals(listOf(PerpsDepositSource(usdcAtZero, usdcFundingAsset)), sources)
    }

    @Test
    fun aWalletWithoutUsdcOnEthereumIsOfferedItAtZeroBelowWhatItHolds() = runTest {
        val usdt = testAccount(TestAssets.usdtEth, balance = "5000000")
        listAccounts(usdt)

        val sources = service.sources(TestWallet.ID).items

        assertEquals(listOf(usdt, usdcAtZero), sources.map { it.account })
    }

    @Test
    fun aWalletHoldingUsdcOnEthereumIsOfferedWhatItHoldsByValue() = runTest {
        val usdt = testAccount(TestAssets.usdtEth, balance = "5000000")
        val usdc = testAccount(USDC, balance = "1000000")
        listAccounts(usdc, usdt)

        val sources = service.sources(TestWallet.ID).items

        assertEquals(listOf(usdt, usdc), sources.map { it.account })
        coVerify(exactly = 0) { mcAccountRepository.findAccount(any(), PerpsFundingAssetId.USDC_ETHEREUM, any(), any()) }
    }

    @Test
    fun usdtOnTonTheWalletHoldsIsOfferedThoughTheListingLeavesItOut() = runTest {
        val usdtEth = testAccount(TestAssets.usdtEth, balance = "2000000")
        val usdtTon = testAccount(TestAssets.usdtTon, balance = "5000000")
        listAccounts(usdtEth)
        coEvery { mcAccountRepository.findAccount(TestWallet.ID, PerpsFundingAssetId.USDT_TON, CURRENCY, true) } returns usdtTon

        val sources = service.sources(TestWallet.ID).items

        assertEquals(listOf(usdtTon, usdtEth, usdcAtZero), sources.map { it.account })
        assertTrue(sources.first().isUsdtTon)
    }

    @Test
    fun usdtOnTonAtZeroIsNotOffered() = runTest {
        listAccounts()
        coEvery {
            mcAccountRepository.findAccount(TestWallet.ID, PerpsFundingAssetId.USDT_TON, CURRENCY, true)
        } returns testAccount(TestAssets.usdtTon, balance = "0")

        val sources = service.sources(TestWallet.ID).items

        assertEquals(listOf(usdcAtZero), sources.map { it.account })
    }

    @Test
    fun usdtOnTonTheListingNamesIsOfferedOnceWithoutAskingForIt() = runTest {
        val usdtTon = testAccount(TestAssets.usdtTon, balance = "5000000")
        listAccounts(usdtTon)

        val sources = service.sources(TestWallet.ID).items

        assertEquals(listOf(usdtTon, usdcAtZero), sources.map { it.account })
        coVerify(exactly = 0) { mcAccountRepository.findAccount(any(), PerpsFundingAssetId.USDT_TON, any(), any()) }
    }

    @Test
    fun usdtOnTonThatCannotBeReadLeavesTheRestOfTheList() = runTest {
        val usdtEth = testAccount(TestAssets.usdtEth, balance = "2000000")
        listAccounts(usdtEth)
        coEvery {
            mcAccountRepository.findAccount(TestWallet.ID, PerpsFundingAssetId.USDT_TON, CURRENCY, true)
        } throws IOException("offline")

        val sources = service.sources(TestWallet.ID).items

        assertEquals(listOf(usdtEth, usdcAtZero), sources.map { it.account })
    }

    @Test
    fun aWalletWithoutAnEthereumAccountIsOfferedNothing() = runTest {
        listAccounts()
        coEvery { mcAccountRepository.findAccount(TestWallet.ID, PerpsFundingAssetId.USDC_ETHEREUM, CURRENCY, true) } returns null

        assertTrue(service.sources(TestWallet.ID).items.isEmpty())
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

        val listing = service.sources(TestWallet.ID)

        assertEquals(listOf(usdt), listing.items.map { it.account })
        assertFalse(listing.fresh)
    }

    @Test
    fun usdcOnEthereumThatCannotBeReadLeavesTheListEmpty() = runTest {
        listAccounts()
        coEvery {
            mcAccountRepository.findAccount(TestWallet.ID, PerpsFundingAssetId.USDC_ETHEREUM, CURRENCY, true)
        } throws IOException("offline")

        assertTrue(service.sources(TestWallet.ID).items.isEmpty())
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
        val NOT_A_SOURCE = testAsset(
            "ton/mainnet/jetton/0:9f8e7d6c5b4a39281706f5e4d3c2b1a09f8e7d6c5b4a39281706f5e4d3c2b1a0",
            symbol = "NOT",
            name = "Notcoin",
            decimals = 9,
        )
    }
}
