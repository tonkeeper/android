package com.tonapps.perps.screens.deposit

import androidx.lifecycle.SavedStateHandle
import com.tonapps.blockchain.model.ConfirmRequest
import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.blockchain.model.legacy.WalletType
import com.tonapps.perps.funding.PerpsDepositMinimum
import com.tonapps.perps.funding.PerpsDepositQuoteError
import com.tonapps.perps.funding.PerpsDepositException
import com.tonapps.perps.funding.PerpsDepositService
import com.tonapps.perps.funding.PerpsDepositSource
import com.tonapps.perps.funding.PerpsFundingAssetId
import com.tonapps.wallet.data.multichain.account.McAccountRepository
import com.tonapps.wallet.data.multichain.account.UnifiedAccountRepository
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.math.BigInteger

/**
 * The amount screen's last step: the deposit is quoted here, before the confirm screen opens, because
 * that screen signs the quote's operation as it is. The service is a mock; time is the rule's
 * virtual clock, driven by hand.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PerpsDepositFeatureTest : BaseUnitTest() {

    private val service = mockk<PerpsDepositService>()
    private val unifiedAccountRepository = mockk<UnifiedAccountRepository>()
    private val mcAccountRepository = mockk<McAccountRepository>()
    private val settingsRepository = mockk<SettingsRepository> {
        every { hiddenBalances } returns false
        every { hiddenBalancesFlow } returns MutableStateFlow(false)
    }
    private val request = mockk<ConfirmRequest>()
    private val source = PerpsDepositSource(
        account = testAccount(TestAssets.usdtEth, balance = "100000000"),
        fundingAsset = checkNotNull(PerpsFundingAssetId.parse(TestAssets.usdtEth.id)),
    )

    private val scheduler get() = dispatchers.scheduler

    @Before
    fun setUp() {
        val wallet = mockk<WalletEntity>()
        every { wallet.type } returns WalletType.Multichain
        every { wallet.id } returns TestWallet.ID
        every { unifiedAccountRepository.selectedTonWalletFlow } returns flowOf(wallet)
        every { mcAccountRepository.refreshTrigger } returns MutableSharedFlow<Unit>(replay = 1).apply { tryEmit(Unit) }
        coEvery { service.sources(TestWallet.ID) } returns listOf(source)
        coEvery { service.pendingPayment(TestWallet.ID) } returns null
        coEvery { service.minimum(TestWallet.ID, any()) } returns null
        coEvery { service.confirmRequest(TestWallet.ID, source, any()) } coAnswers {
            delay(QUOTE_MS)
            request
        }
    }

    @Test
    fun reviewQuotesTheDepositAndThenOpensTheConfirmScreen() {
        val feature = open()
        val events = collectEvents(feature)
        enter(feature, "5")

        review(feature)

        // The button spins while the backend prices the deposit; nothing opens yet.
        assertTrue(feature.quoting.value)
        assertTrue(events.isEmpty())

        scheduler.advanceTimeBy(QUOTE_MS)
        scheduler.runCurrent()

        assertEquals(listOf<PerpsDepositEvent>(PerpsDepositEvent.Reviewed(request)), events)
        assertFalse(feature.quoting.value)
        assertNull(feature.quoteError.value)
        coVerify(exactly = 1) { service.confirmRequest(TestWallet.ID, source, FIVE_USDT) }
    }

    @Test
    fun aFailedQuoteSaysWhyAndStaysOnTheScreenUntilTheAmountChanges() {
        coEvery { service.confirmRequest(any(), any(), any()) } throws PerpsDepositException.InsufficientBalance()
        val feature = open()
        val events = collectEvents(feature)
        enter(feature, "5")

        review(feature)

        assertEquals(PerpsDepositQuoteError.InsufficientBalance, feature.quoteError.value)
        assertFalse(feature.quoting.value)
        assertTrue(events.isEmpty())

        enter(feature, "6")

        assertNull(feature.quoteError.value)
    }

    @Test
    fun editingTheAmountDropsTheQuoteInFlight() {
        val feature = open()
        val events = collectEvents(feature)
        enter(feature, "5")
        review(feature)
        assertTrue(feature.quoting.value)

        enter(feature, "6")
        assertFalse(feature.quoting.value)
        scheduler.advanceTimeBy(QUOTE_MS)
        scheduler.runCurrent()

        // The quote was for an amount no longer on screen: it opens nothing.
        assertTrue(events.isEmpty())

        review(feature)
        scheduler.advanceTimeBy(QUOTE_MS)
        scheduler.runCurrent()

        assertEquals(listOf<PerpsDepositEvent>(PerpsDepositEvent.Reviewed(request)), events)
        coVerify(exactly = 1) { service.confirmRequest(TestWallet.ID, source, BigInteger.valueOf(6_000_000L)) }
    }

    @Test
    fun aSecondTapWhileQuotingIsIgnored() {
        val feature = open()
        val events = collectEvents(feature)
        enter(feature, "5")

        review(feature)
        review(feature)
        scheduler.advanceTimeBy(QUOTE_MS)
        scheduler.runCurrent()

        assertEquals(1, events.size)
        coVerify(exactly = 1) { service.confirmRequest(any(), any(), any()) }
    }

    @Test
    fun theSourceWorthMostInFiatIsSelectedFirst() {
        coEvery { service.sources(TestWallet.ID) } returns listOf(usdc(balance = "2000000"), source)

        val feature = open()

        assertEquals(source, feature.selected.value?.source)
    }

    @Test
    fun usdcAtZeroDoesNotTakeTheSelectionFromAnUnpricedBalance() {
        val unpriced = PerpsDepositSource(
            account = testAccount(TestAssets.usdtEth, balance = "5000000", price = null),
            fundingAsset = source.fundingAsset,
        )
        coEvery { service.sources(TestWallet.ID) } returns listOf(usdc(balance = "0"), unpriced)

        val feature = open()

        assertEquals(unpriced, feature.selected.value?.source)
    }

    @Test
    fun aReloadKeepsTheShownSourceWhileItIsStillListed() {
        coEvery { service.sources(TestWallet.ID) } returnsMany listOf(
            listOf(usdc(balance = "2000000"), source),
            listOf(usdc(balance = "500000000"), source),
        )
        val feature = open()
        enter(feature, "5")

        reload(feature)

        assertEquals(source, feature.selected.value?.source)
        assertEquals("5", feature.amountInput.value)
    }

    @Test
    fun theLargestSourceTakesOverFromAnEmptyShownOne() {
        val usdcAtZero = usdc(balance = "0")
        val received = usdc(balance = "3000000")
        coEvery { service.sources(TestWallet.ID) } returnsMany listOf(
            listOf(usdcAtZero),
            listOf(usdcAtZero, source),
        )
        val feature = open()
        assertEquals(usdcAtZero, feature.selected.value?.source)

        reload(feature)

        assertEquals(source, feature.selected.value?.source)

        coEvery { service.sources(TestWallet.ID) } returns listOf(received, source)
        feature.sendAction(PerpsDepositAction.SelectSource(received.assetId))
        reload(feature)

        assertEquals(received, feature.selected.value?.source)
    }

    @Test
    fun anotherWalletOpensOnItsOwnLargestSource() {
        val selectedWallet = MutableStateFlow<WalletEntity?>(multichainWallet(TestWallet.ID))
        every { unifiedAccountRepository.selectedTonWalletFlow } returns selectedWallet
        val usdtOfOther = PerpsDepositSource(
            account = testAccount(TestAssets.usdtEth, balance = "1000000", walletId = OTHER_WALLET_ID),
            fundingAsset = source.fundingAsset,
        )
        val usdcOfOther = usdc(balance = "50000000", walletId = OTHER_WALLET_ID)
        coEvery { service.sources(OTHER_WALLET_ID) } returns listOf(usdcOfOther, usdtOfOther)
        coEvery { service.pendingPayment(OTHER_WALLET_ID) } returns null
        val feature = open()
        assertEquals(source, feature.selected.value?.source)

        selectedWallet.value = multichainWallet(OTHER_WALLET_ID)
        scheduler.runCurrent()

        assertEquals(usdcOfOther, feature.selected.value?.source)
    }

    @Test
    fun aSourcePickedInOneWalletDoesNotCarryOverToAnother() {
        val selectedWallet = MutableStateFlow<WalletEntity?>(multichainWallet(TestWallet.ID))
        every { unifiedAccountRepository.selectedTonWalletFlow } returns selectedWallet
        val usdcOfThis = usdc(balance = "2000000")
        coEvery { service.sources(TestWallet.ID) } returns listOf(usdcOfThis, source)
        val usdcOfOther = usdc(balance = "10000", walletId = OTHER_WALLET_ID)
        val usdtOfOther = PerpsDepositSource(
            account = testAccount(TestAssets.usdtEth, balance = "50000000", walletId = OTHER_WALLET_ID),
            fundingAsset = source.fundingAsset,
        )
        coEvery { service.sources(OTHER_WALLET_ID) } returns listOf(usdcOfOther, usdtOfOther)
        coEvery { service.pendingPayment(OTHER_WALLET_ID) } returns null
        val feature = open()
        feature.sendAction(PerpsDepositAction.SelectSource(usdcOfThis.assetId))
        scheduler.runCurrent()
        assertEquals(usdcOfThis, feature.selected.value?.source)

        selectedWallet.value = multichainWallet(OTHER_WALLET_ID)
        scheduler.runCurrent()

        assertEquals(usdtOfOther, feature.selected.value?.source)
    }

    @Test
    fun anAmountTheSourceCannotCoverIsNotQuoted() {
        val feature = open()
        enter(feature, "500")

        review(feature)

        assertFalse(feature.quoting.value)
        coVerify(exactly = 0) { service.confirmRequest(any(), any(), any()) }
    }

    @Test
    fun anAmountBelowTheSourcesMinimumIsFlaggedAndNotQuoted() {
        coEvery { service.minimum(TestWallet.ID, source) } returns
            PerpsDepositMinimum(usd = BigDecimal("10"), units = BigInteger.valueOf(10_000_000L))
        val feature = open()
        enter(feature, "5")

        review(feature)

        assertEquals(true, feature.summary.value?.belowMinimum)
        assertFalse(feature.quoting.value)
        coVerify(exactly = 0) { service.confirmRequest(any(), any(), any()) }

        enter(feature, "10")

        assertEquals(false, feature.summary.value?.belowMinimum)
        assertEquals(true, feature.summary.value?.canReview)
    }

    @Test
    fun withoutAKnownMinimumAnyAmountGoesToReview() {
        val feature = open()
        enter(feature, "0.5")

        assertEquals(false, feature.summary.value?.belowMinimum)
        assertEquals(true, feature.summary.value?.canReview)
    }

    private fun open(): PerpsDepositFeature {
        val feature = PerpsDepositFeature(
            service = service,
            unifiedAccountRepository = unifiedAccountRepository,
            mcAccountRepository = mcAccountRepository,
            settingsRepository = settingsRepository,
            savedStateHandle = SavedStateHandle(),
        )
        scheduler.runCurrent()
        return feature
    }

    private fun enter(feature: PerpsDepositFeature, amount: String) {
        feature.sendAction(PerpsDepositAction.SetAmount(amount))
        scheduler.runCurrent()
    }

    private fun review(feature: PerpsDepositFeature) {
        feature.sendAction(PerpsDepositAction.Review)
        scheduler.runCurrent()
    }

    private fun multichainWallet(id: String): WalletEntity {
        val wallet = mockk<WalletEntity>()
        every { wallet.type } returns WalletType.Multichain
        every { wallet.id } returns id
        return wallet
    }

    private fun reload(feature: PerpsDepositFeature) {
        feature.sendAction(PerpsDepositAction.Reload)
        scheduler.runCurrent()
    }

    private fun usdc(balance: String, walletId: String = TestWallet.ID): PerpsDepositSource {
        return PerpsDepositSource(
            account = testAccount(USDC, balance = balance, walletId = walletId),
            fundingAsset = checkNotNull(PerpsFundingAssetId.parse(PerpsFundingAssetId.USDC_ETHEREUM)),
        )
    }

    private fun collectEvents(feature: PerpsDepositFeature): List<PerpsDepositEvent> {
        val events = mutableListOf<PerpsDepositEvent>()
        CoroutineScope(dispatchers.main).launch { feature.events.collect { events += it } }
        return events
    }

    private companion object {
        const val QUOTE_MS = 1_000L
        val FIVE_USDT: BigInteger = BigInteger.valueOf(5_000_000L)
        const val OTHER_WALLET_ID = "other-wallet"
        val USDC = testAsset(PerpsFundingAssetId.USDC_ETHEREUM, symbol = "USDC", name = "USD Coin", decimals = 6)
    }
}
