package com.tonapps.perps.screens.withdraw

import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.perps.data.PerpsPortfolioStore
import com.tonapps.perps.funding.PerpsDepositSource
import com.tonapps.perps.funding.PerpsFundingAssetId
import com.tonapps.perps.funding.PerpsFundingTestEnv
import com.tonapps.perps.funding.PerpsWithdrawDestination
import com.tonapps.perps.funding.PerpsWithdrawLimits
import com.tonapps.perps.funding.PerpsWithdrawService
import com.tonapps.wallet.data.multichain.account.UnifiedAccountRepository
import com.tonapps.wallet.data.settings.SettingsRepository
import com.tonapps.wallet.testing.BaseUnitTest
import com.tonapps.wallet.testing.TestAssets
import com.tonapps.wallet.testing.TestWallet
import com.tonapps.wallet.testing.testAccount
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.math.BigInteger

class PerpsWithdrawFeatureTest : BaseUnitTest() {

    private val service = mockk<PerpsWithdrawService>()
    private val portfolioStore = mockk<PerpsPortfolioStore> {
        every { snapshot } returns MutableStateFlow(null)
    }
    private val unifiedAccountRepository = mockk<UnifiedAccountRepository>()
    private val settingsRepository = mockk<SettingsRepository> {
        every { hiddenBalances } returns false
        every { hiddenBalancesFlow } returns MutableStateFlow(false)
    }
    private val eth = PerpsWithdrawDestination(
        assetId = TestAssets.eth.id,
        recipient = "0x0000000000000000000000000000000000000001",
        source = PerpsDepositSource(
            account = testAccount(TestAssets.eth, balance = "1000000000000000000", price = "2000"),
            fundingAsset = checkNotNull(PerpsFundingAssetId.parseDestination(TestAssets.eth.id)),
        ),
        transferFeeUnits = null,
    )
    private val usdcEthereum = PerpsWithdrawDestination(
        assetId = PerpsFundingAssetId.USDC_ETHEREUM,
        recipient = "0x0000000000000000000000000000000000000002",
        source = PerpsDepositSource(
            account = testAccount(PerpsFundingTestEnv.USDC, balance = "0", price = "1"),
            fundingAsset = checkNotNull(PerpsFundingAssetId.parseDestination(PerpsFundingAssetId.USDC_ETHEREUM)),
        ),
        transferFeeUnits = null,
    )

    private val scheduler get() = dispatchers.scheduler

    @Before
    fun setUp() {
        val wallet = mockk<WalletEntity>()
        every { wallet.supportsPerps } returns true
        every { wallet.id } returns TestWallet.ID
        every { unifiedAccountRepository.selectedTonWalletFlow } returns flowOf(wallet)
        coEvery { service.destinations(TestWallet.ID) } returns listOf(eth, usdcEthereum)
        coEvery { service.available(TestWallet.ID) } returns BigDecimal.TEN
        coEvery { service.limits() } returns PerpsWithdrawLimits(minTransferUnits = BigInteger.ONE)
        coEvery { service.pendingWithdraw(TestWallet.ID) } returns null
    }

    @Test
    fun theScreenOpensOnUsdcEthereumWhenAnotherDestinationIsWorthMore() {
        val feature = open()

        assertEquals(listOf(eth, usdcEthereum), feature.destinations.value.state?.map { it.destination })
        assertEquals(usdcEthereum, feature.selected.value?.destination)
    }

    @Test
    fun withoutUsdcEthereumTheScreenDoesNotOpenOnAnotherAsset() {
        coEvery { service.destinations(TestWallet.ID) } returns listOf(eth)

        val feature = open()

        assertEquals(null, feature.selected.value)
    }

    @Test
    fun aPickedDestinationStaysSelected() {
        val feature = open()

        feature.sendAction(PerpsWithdrawAction.SelectDestination(eth.assetId))
        scheduler.runCurrent()

        assertEquals(eth, feature.selected.value?.destination)
    }

    @Test
    fun typedDollarsShowWhatTheDestinationTokenIsWorth() {
        val feature = open()
        select(feature, eth)

        feature.sendAction(PerpsWithdrawAction.SetAmount("100"))
        scheduler.runCurrent()

        assertEquals(BigInteger.valueOf(100_000_000), feature.summary.value?.amountUnits)
        assertEquals(BigDecimal("0.05"), feature.summary.value?.receiveAmount?.stripTrailingZeros())
    }

    @Test
    fun theUsdcEthereumWithdrawalIsPricedAtTheUsdcRate() {
        val feature = open()

        feature.sendAction(PerpsWithdrawAction.SetAmount("7.5"))
        scheduler.runCurrent()

        assertEquals(BigDecimal("7.5"), feature.summary.value?.receiveAmount?.stripTrailingZeros())
    }

    @Test
    fun switchingToTheTokenKeepsTheDollarsBeingWithdrawn() {
        val feature = open()
        select(feature, eth)
        feature.sendAction(PerpsWithdrawAction.SetAmount("100"))
        scheduler.runCurrent()

        feature.sendAction(PerpsWithdrawAction.ToggleInput)
        scheduler.runCurrent()

        assertTrue(feature.inputInToken.value)
        assertEquals("0.05", feature.amountInput.value)
        assertEquals(BigInteger.valueOf(100_000_000), feature.summary.value?.amountUnits)

        feature.sendAction(PerpsWithdrawAction.ToggleInput)
        scheduler.runCurrent()

        assertFalse(feature.inputInToken.value)
        assertEquals("100", feature.amountInput.value)
    }

    @Test
    fun dollarsSurviveARoundTripThroughATokenTheyDoNotDivideEvenly() {
        val feature = open()
        select(feature, eth)
        feature.sendAction(PerpsWithdrawAction.SetAmount("5"))
        scheduler.runCurrent()
        coEvery { service.destinations(TestWallet.ID) } returns listOf(eth.withPrice("3"), usdcEthereum)
        feature.sendAction(PerpsWithdrawAction.Reload)
        scheduler.runCurrent()

        feature.sendAction(PerpsWithdrawAction.ToggleInput)
        scheduler.runCurrent()
        feature.sendAction(PerpsWithdrawAction.ToggleInput)
        scheduler.runCurrent()

        assertEquals("5", feature.amountInput.value)
    }

    @Test
    fun maxInTheTokenTakesTheWholeBalanceWithoutGoingOverIt() {
        val feature = open()
        select(feature, eth)
        feature.sendAction(PerpsWithdrawAction.ToggleInput)
        scheduler.runCurrent()

        feature.sendAction(PerpsWithdrawAction.Max)
        scheduler.runCurrent()

        assertEquals("0.005", feature.amountInput.value)
        assertEquals(BigInteger.valueOf(10_000_000), feature.summary.value?.amountUnits)
        assertFalse(feature.summary.value?.insufficient == true)
    }

    @Test
    fun anotherDestinationRecountsTheTokenAmountFromItsDollars() {
        val feature = open()
        select(feature, eth)
        feature.sendAction(PerpsWithdrawAction.ToggleInput)
        feature.sendAction(PerpsWithdrawAction.SetAmount("0.05"))
        scheduler.runCurrent()

        select(feature, usdcEthereum)

        assertTrue(feature.inputInToken.value)
        assertEquals("100", feature.amountInput.value)
        assertEquals(BigInteger.valueOf(100_000_000), feature.summary.value?.amountUnits)
    }

    @Test
    fun maxInAnEighteenDecimalTokenFillsAtMostEightDecimals() {
        val ethAt3000 = eth.withPrice("3000")
        coEvery { service.destinations(TestWallet.ID) } returns listOf(ethAt3000, usdcEthereum)
        val feature = open()
        select(feature, ethAt3000)
        feature.sendAction(PerpsWithdrawAction.ToggleInput)
        scheduler.runCurrent()

        feature.sendAction(PerpsWithdrawAction.Max)
        scheduler.runCurrent()

        assertEquals("0.00333333", feature.amountInput.value)
        assertEquals(BigInteger.valueOf(9_999_990), feature.summary.value?.amountUnits)
        assertFalse(feature.summary.value?.insufficient == true)
    }

    @Test
    fun dollarsSwitchedToAnEighteenDecimalTokenKeepEightDecimals() {
        val ethAt3000 = eth.withPrice("3000")
        coEvery { service.destinations(TestWallet.ID) } returns listOf(ethAt3000, usdcEthereum)
        val feature = open()
        select(feature, ethAt3000)
        feature.sendAction(PerpsWithdrawAction.SetAmount("5"))
        scheduler.runCurrent()

        feature.sendAction(PerpsWithdrawAction.ToggleInput)
        scheduler.runCurrent()

        assertTrue(feature.inputInToken.value)
        assertEquals("0.00166666", feature.amountInput.value)
    }

    @Test
    fun anEighteenDecimalDestinationPickedInTheTokenKeepsEightDecimals() {
        val ethAt3000 = eth.withPrice("3000")
        coEvery { service.destinations(TestWallet.ID) } returns listOf(ethAt3000, usdcEthereum)
        val feature = open()
        feature.sendAction(PerpsWithdrawAction.ToggleInput)
        feature.sendAction(PerpsWithdrawAction.SetAmount("5"))
        scheduler.runCurrent()

        select(feature, ethAt3000)

        assertTrue(feature.inputInToken.value)
        assertEquals("0.00166666", feature.amountInput.value)
    }

    private fun PerpsWithdrawDestination.withPrice(price: String) = copy(
        source = PerpsDepositSource(
            account = testAccount(TestAssets.eth, balance = "1000000000000000000", price = price),
            fundingAsset = checkNotNull(PerpsFundingAssetId.parseDestination(TestAssets.eth.id)),
        ),
    )

    private fun select(feature: PerpsWithdrawFeature, destination: PerpsWithdrawDestination) {
        feature.sendAction(PerpsWithdrawAction.SelectDestination(destination.assetId))
        scheduler.runCurrent()
    }

    private fun open(): PerpsWithdrawFeature {
        val feature = PerpsWithdrawFeature(
            service = service,
            portfolioStore = portfolioStore,
            unifiedAccountRepository = unifiedAccountRepository,
            settingsRepository = settingsRepository,
        )
        scheduler.runCurrent()
        return feature
    }
}
