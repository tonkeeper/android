package com.tonapps.deposit.multicoin.screens.confirm.battery

import com.ionspin.kotlin.bignum.integer.BigInteger
import com.tonapps.blockchain.model.CommonTransactionData
import com.tonapps.blockchain.model.ConfirmRequest
import com.tonapps.blockchain.model.ConfirmType
import com.tonapps.chainkit.core.chain.model.account.MnemonicWallet
import com.tonapps.chainkit.core.op.Operation
import com.tonapps.deposit.multicoin.screens.confirm.fee.RelayerOutcomeUnknownException
import com.tonapps.deposit.multicoin.screens.confirm.fee.RelayerQuoteExpiredException
import com.tonapps.wallet.data.battery.entity.BatteryPurchaseQuote
import com.tonapps.wallet.data.multichain.account.McAccountRepository
import com.tonapps.wallet.data.passcode.WalletUnlocker
import com.tonapps.wallet.testing.BaseUnitTest
import com.tonapps.wallet.testing.FakeTimeProvider
import com.tonapps.wallet.testing.TestAssets
import com.tonapps.wallet.testing.TestWallet
import com.tonapps.wallet.testing.testAccount
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The confirm screen's side of a battery purchase on another chain: quote on open, quote again ahead of
 * expiry, and on the swipe quote once more after the passcode and sign that quote.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BatteryPurchaseConfirmFeatureTest : BaseUnitTest() {

    private val purchaser = mockk<BatteryPurchaser>()
    private val accountRepo = mockk<McAccountRepository>()
    private val walletUnlocker = mockk<WalletUnlocker>()
    private val cryptoWallet = mockk<MnemonicWallet>()
    private val operation = mockk<Operation>()
    private val serverTime = FakeTimeProvider()
    private val account by lazy { testAccount(TestAssets.usdtTron, balance = "50000000") }

    private val scheduler get() = dispatchers.scheduler

    private val quotes = mutableListOf<BatteryPurchaseReview>()
    private var nextCharges = 3_100L
    private var expiresInMs = 60_000L

    @Before
    fun setUp() {
        coEvery { accountRepo.findAccount(TestWallet.ID, TestAssets.usdtTron.id, any(), any()) } returns account
        coEvery { walletUnlocker.unlockWallet(any(), any()) } returns cryptoWallet
        coEvery { purchaser.quote(any()) } answers { review(nextCharges).also { quotes += it } }
        coEvery { purchaser.send(any(), any(), any(), any()) } returns "hash"
    }

    @Test
    fun quotesOnOpen() {
        val feature = open()
        assertTrue(feature.loading.value)

        scheduler.runCurrent()

        assertFalse(feature.loading.value)
        assertSame(quotes.single(), feature.review.value)
        assertSame(account, feature.account.value)
        assertNull(feature.loadError.value)
    }

    @Test
    fun aFailedQuoteShowsTheErrorAndRetryQuotesAgain() {
        coEvery { purchaser.quote(any()) } throws IllegalStateException("boom")
        val feature = open()
        scheduler.runCurrent()

        assertEquals(BatteryPurchaseError.Failed, feature.loadError.value)

        coEvery { purchaser.quote(any()) } answers { review(nextCharges).also { quotes += it } }
        feature.retry()
        scheduler.runCurrent()

        assertNull(feature.loadError.value)
        assertSame(quotes.single(), feature.review.value)
    }

    @Test
    fun quotesAgainShortlyBeforeTheQuoteExpires() {
        val feature = open()
        scheduler.runCurrent()
        assertEquals(1, quotes.size)

        scheduler.advanceTimeBy(45_000L)
        scheduler.runCurrent()
        assertEquals(1, quotes.size)

        scheduler.advanceTimeBy(10_000L)
        scheduler.runCurrent()
        assertEquals(2, quotes.size)
        assertSame(quotes.last(), feature.review.value)
    }

    @Test
    fun theSwipeSignsAQuoteMadeAfterThePasscodeAndLeaves() {
        val feature = open()
        val events = collectEvents(feature)
        scheduler.runCurrent()

        feature.confirm()
        scheduler.runCurrent()

        assertEquals(BatteryPurchaseSendingState.Done, feature.sending.value)
        assertEquals(2, quotes.size)
        coVerify(exactly = 1) { purchaser.send(TestWallet.ID, quotes.last(), account, cryptoWallet) }

        scheduler.advanceTimeBy(500L)
        scheduler.runCurrent()
        assertEquals(listOf(BatteryPurchaseEvent.Sent), events)
    }

    @Test
    fun aValidQuoteIsQuotedAgainAfterThePasscodeBeforeSigning() {
        val feature = open()
        scheduler.runCurrent()
        val shown = quotes.single()
        // Still before the quote's deadline, but past the life of a Solana blockhash in it.
        serverTime.offsetMs = 39_000L

        feature.confirm()
        scheduler.runCurrent()

        assertEquals(2, quotes.size)
        assertSame(quotes.last(), feature.review.value)
        coVerify(exactly = 0) { purchaser.send(any(), shown, any(), any()) }
        coVerify(exactly = 1) { purchaser.send(TestWallet.ID, quotes.last(), account, cryptoWallet) }
        assertEquals(BatteryPurchaseSendingState.Done, feature.sending.value)
    }

    @Test
    fun aFailedRequoteAfterThePasscodeShowsTheErrorSendsNothingAndMayBeRetried() {
        val feature = open()
        scheduler.runCurrent()
        val shown = quotes.single()
        coEvery { purchaser.quote(any()) } throws IllegalStateException("blip")

        feature.confirm()
        scheduler.runCurrent()

        val error = feature.submitError.value
        assertEquals(BatteryPurchaseError.Failed, error)
        assertTrue(error!!.isRetryable)
        assertEquals(BatteryPurchaseSendingState.None, feature.sending.value)
        assertSame(shown, feature.review.value)
        coVerify(exactly = 0) { purchaser.send(any(), any(), any(), any()) }

        // Try again asks for the passcode and quotes once more.
        coEvery { purchaser.quote(any()) } answers { review(nextCharges).also { quotes += it } }
        feature.retry()
        scheduler.runCurrent()

        assertNull(feature.submitError.value)
        assertEquals(BatteryPurchaseSendingState.Done, feature.sending.value)
        coVerify(exactly = 2) { walletUnlocker.unlockWallet(any(), any()) }
        coVerify(exactly = 0) { purchaser.send(any(), shown, any(), any()) }
        coVerify(exactly = 1) { purchaser.send(TestWallet.ID, quotes.last(), account, cryptoWallet) }
    }

    @Test
    fun aQuoteThePasscodeOutlastedIsQuotedAgainBeforeSigning() {
        expiresInMs = 1_000L
        val feature = open()
        scheduler.runCurrent()
        val shown = quotes.single()
        // The quote ran out while the passcode prompt was up.
        serverTime.offsetMs = 10_000L
        expiresInMs = 60_000L

        feature.confirm()
        scheduler.runCurrent()

        assertEquals(2, quotes.size)
        coVerify(exactly = 0) { purchaser.send(any(), shown, any(), any()) }
        coVerify(exactly = 1) { purchaser.send(TestWallet.ID, quotes.last(), account, cryptoWallet) }
        assertEquals(BatteryPurchaseSendingState.Done, feature.sending.value)
    }

    @Test
    fun aFreshQuoteThatAddsFewerChargesIsShownAndNotSigned() {
        val feature = open()
        scheduler.runCurrent()
        nextCharges = 3_000L

        feature.confirm()
        scheduler.runCurrent()

        coVerify(exactly = 0) { purchaser.send(any(), any(), any(), any()) }
        assertEquals(BatteryPurchaseError.ChargesChanged, feature.submitError.value)
        assertEquals(3_000L, feature.review.value?.quote?.charges)
        assertEquals(BatteryPurchaseSendingState.None, feature.sending.value)
    }

    @Test
    fun aClosedPasscodePromptBringsTheSwipeBack() {
        coEvery { walletUnlocker.unlockWallet(any(), any()) } throws CancellationException("closed")
        val feature = open()
        scheduler.runCurrent()

        feature.confirm()
        scheduler.runCurrent()

        assertEquals(BatteryPurchaseSendingState.None, feature.sending.value)
        assertNull(feature.submitError.value)
        coVerify(exactly = 0) { purchaser.send(any(), any(), any(), any()) }
    }

    @Test
    fun aSendThatMayHavePaidIsNotOfferedAgain() {
        coEvery { purchaser.send(any(), any(), any(), any()) } throws RelayerOutcomeUnknownException(IllegalStateException("timeout"))
        val feature = open()
        scheduler.runCurrent()

        feature.confirm()
        scheduler.runCurrent()

        val error = feature.submitError.value
        assertEquals(BatteryPurchaseError.OutcomeUnknown, error)
        assertFalse(error!!.isRetryable)
    }

    @Test
    fun tryAgainAfterASendThatMayHavePaidSendsNothing() {
        coEvery { purchaser.send(any(), any(), any(), any()) } throws RelayerOutcomeUnknownException(IllegalStateException("timeout"))
        val feature = open()
        scheduler.runCurrent()
        feature.confirm()
        scheduler.runCurrent()

        feature.retry()
        feature.confirm()
        scheduler.runCurrent()

        coVerify(exactly = 1) { purchaser.send(any(), any(), any(), any()) }
        assertEquals(BatteryPurchaseError.OutcomeUnknown, feature.submitError.value)
    }

    @Test
    fun tryAgainAfterChargesChangedBringsTheSwipeBackWithoutSigning() {
        val feature = open()
        scheduler.runCurrent()
        nextCharges = 3_000L
        feature.confirm()
        scheduler.runCurrent()

        feature.retry()
        scheduler.runCurrent()

        assertNull(feature.submitError.value)
        assertEquals(BatteryPurchaseSendingState.None, feature.sending.value)
        coVerify(exactly = 0) { purchaser.send(any(), any(), any(), any()) }
    }

    @Test
    fun aQuoteThatAddsNoChargesCannotBeSwiped() {
        nextCharges = 0L
        val feature = open()
        scheduler.runCurrent()

        assertEquals(BatteryPurchaseError.NoCharges, feature.loadError.value)
        assertFalse(feature.loadError.value!!.isRetryable)

        feature.confirm()
        scheduler.runCurrent()
        coVerify(exactly = 0) { purchaser.send(any(), any(), any(), any()) }
    }

    @Test
    fun aFailedBackgroundRequoteKeepsTheValidQuoteAndTriesAgain() {
        val feature = open()
        scheduler.runCurrent()
        val shown = quotes.single()
        coEvery { purchaser.quote(any()) } throws IllegalStateException("blip")

        // The requote 10 s before expiry fails: the quote shown is still valid.
        scheduler.advanceTimeBy(50_000L)
        scheduler.runCurrent()

        assertNull(feature.loadError.value)
        assertFalse(feature.loading.value)
        assertSame(shown, feature.review.value)

        coEvery { purchaser.quote(any()) } answers { review(nextCharges).also { quotes += it } }
        scheduler.advanceTimeBy(3_000L)
        scheduler.runCurrent()

        assertEquals(2, quotes.size)
        assertSame(quotes.last(), feature.review.value)
    }

    @Test
    fun aZeroChargeRequoteAtSwipeIsALoadErrorThatALaterQuoteClears() {
        val feature = open()
        scheduler.runCurrent()
        nextCharges = 0L

        feature.confirm()
        scheduler.runCurrent()

        assertEquals(BatteryPurchaseError.NoCharges, feature.loadError.value)
        assertNull(feature.submitError.value)
        coVerify(exactly = 0) { purchaser.send(any(), any(), any(), any()) }

        // The next requote adds charges again: the slider is back.
        nextCharges = 3_100L
        scheduler.advanceTimeBy(50_000L)
        scheduler.runCurrent()
        assertNull(feature.loadError.value)
    }

    @Test
    fun aCancellationFromInsideTheSendLeavesTheOutcomeUnknown() {
        coEvery { purchaser.send(any(), any(), any(), any()) } throws CancellationException("timeout below")
        val feature = open()
        scheduler.runCurrent()

        feature.confirm()
        scheduler.runCurrent()

        assertEquals(BatteryPurchaseError.OutcomeUnknown, feature.submitError.value)
        assertEquals(BatteryPurchaseSendingState.None, feature.sending.value)
    }

    @Test
    fun aQuoteShownAfterAnExpiredSendClearsTheExpiredError() {
        coEvery { purchaser.send(any(), any(), any(), any()) } throws RelayerQuoteExpiredException(IllegalStateException("expired"))
        val feature = open()
        scheduler.runCurrent()
        feature.confirm()
        scheduler.runCurrent()
        assertEquals(BatteryPurchaseError.Expired, feature.submitError.value)

        // The background requote brings a fresh quote: it can be swiped as it is.
        scheduler.advanceTimeBy(50_000L)
        scheduler.runCurrent()

        assertNull(feature.submitError.value)
        assertSame(quotes.last(), feature.review.value)
    }

    private fun open(): BatteryPurchaseConfirmFeature {
        val request = ConfirmRequest(
            data = CommonTransactionData(assetId = TestAssets.usdtTron.id, walletId = TestWallet.ID),
            type = ConfirmType.BatteryPurchase(
                chainId = "tron/mainnet",
                batteryAssetId = TestAssets.usdtTron.id,
                senderAddress = SENDER,
                amount = BigInteger.parseString(AMOUNT),
                symbol = "USDT",
                decimals = 6,
                estimatedCharges = 3_113,
            ),
        )
        return BatteryPurchaseConfirmFeature(request, purchaser, accountRepo, walletUnlocker, serverTime)
    }

    private fun collectEvents(feature: BatteryPurchaseConfirmFeature): List<BatteryPurchaseEvent> {
        val events = mutableListOf<BatteryPurchaseEvent>()
        CoroutineScope(dispatchers.main).launch { feature.events.collect { events += it } }
        return events
    }

    private fun review(charges: Long) = BatteryPurchaseReview(
        quote = BatteryPurchaseQuote(
            chainId = "tron/mainnet",
            assetId = TestAssets.usdtTron.id,
            senderAddress = SENDER,
            amount = java.math.BigInteger(AMOUNT),
            grossCharges = charges + 13,
            charges = charges,
            fundsReceiver = "TFundsReceiver",
            promoBonusPercent = null,
        ),
        operation = operation,
        expiresAt = serverTime.currentTimeMillis() + expiresInMs,
    )

    private companion object {
        const val AMOUNT = "10000000"
        const val SENDER = "TSender"
    }
}
