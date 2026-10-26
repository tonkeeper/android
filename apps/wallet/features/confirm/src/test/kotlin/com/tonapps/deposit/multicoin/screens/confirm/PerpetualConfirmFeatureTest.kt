package com.tonapps.deposit.multicoin.screens.confirm

import com.ionspin.kotlin.bignum.integer.BigInteger
import com.tonapps.blockchain.model.CommonTransactionData
import com.tonapps.blockchain.model.ConfirmRequest
import com.tonapps.blockchain.model.ConfirmType
import com.tonapps.chainkit.core.chain.model.account.CryptoWallet
import com.tonapps.deposit.multicoin.screens.confirm.perpetual.PerpetualConfirmDelegate
import com.tonapps.deposit.multicoin.screens.confirm.perpetual.PerpetualConfirmError
import com.tonapps.deposit.multicoin.screens.confirm.perpetual.PerpetualConfirmEvent
import com.tonapps.deposit.multicoin.screens.confirm.perpetual.PerpetualConfirmException
import com.tonapps.deposit.multicoin.screens.confirm.perpetual.PerpetualConfirmFeature
import com.tonapps.deposit.multicoin.screens.confirm.perpetual.PerpetualReview
import com.tonapps.deposit.multicoin.screens.confirm.perpetual.PerpetualSendingState
import com.tonapps.wallet.data.multichain.account.McAccountRepository
import com.tonapps.wallet.data.passcode.PasscodeManager
import com.tonapps.wallet.testing.BaseUnitTest
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The confirm screen's side of a perps withdrawal: quote on open, re-quote ahead of expiry, submit
 * on swipe. The perps side is a [FakeDelegate]; time is the rule's virtual clock, driven by hand.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PerpetualConfirmFeatureTest : BaseUnitTest() {

    private val cryptoWallet = mockk<CryptoWallet>()
    private val accountRepo = mockk<McAccountRepository>()
    private val passcodeManager = mockk<PasscodeManager>()
    private val delegate = FakeDelegate()

    private val scheduler get() = dispatchers.scheduler

    @Before
    fun setUp() {
        coEvery { passcodeManager.unlockMultichainVault<CryptoWallet>(any(), any()) } returns cryptoWallet
    }

    @Test
    fun quotesOnOpen() {
        val feature = open()
        assertTrue(feature.loading.value)
        assertNull(feature.review.value)

        scheduler.runCurrent()

        assertFalse(feature.loading.value)
        assertEquals(1, delegate.reviews.size)
        val (request, withdraw) = delegate.reviews.single()
        assertEquals(WALLET_ID, request.data.walletId)
        assertEquals(AMOUNT, withdraw.amount)
        assertSame(delegate.lastReview, feature.review.value)
        assertNull(feature.loadError.value)
    }

    @Test
    fun failedPrepareShowsTheErrorAndRetryPreparesAgain() {
        delegate.reviewError = PerpetualConfirmException(PerpetualConfirmError.PrepareFailed)
        val feature = open()
        scheduler.runCurrent()

        assertEquals(PerpetualConfirmError.PrepareFailed, feature.loadError.value)
        assertNull(feature.review.value)
        assertFalse(feature.loading.value)

        delegate.reviewError = null
        feature.retry(context)
        scheduler.runCurrent()

        assertEquals(2, delegate.reviews.size)
        assertNull(feature.loadError.value)
        assertSame(delegate.lastReview, feature.review.value)
        assertTrue(delegate.submits.isEmpty())
    }

    @Test
    fun anUnexpectedFailureReadsAsFailed() {
        delegate.reviewError = IllegalStateException("boom")
        val feature = open()
        scheduler.runCurrent()

        assertEquals(PerpetualConfirmError.Failed, feature.loadError.value)
    }

    @Test
    fun requotesShortlyBeforeTheQuoteExpires() {
        delegate.expiresInMs = 60_000L
        val feature = open()
        scheduler.runCurrent()
        assertEquals(1, delegate.reviews.size)

        // The re-quote is due two seconds ahead of expiry, not at it.
        scheduler.advanceTimeBy(55_000L)
        scheduler.runCurrent()
        assertEquals(1, delegate.reviews.size)

        scheduler.advanceTimeBy(4_000L)
        scheduler.runCurrent()
        assertEquals(2, delegate.reviews.size)
        assertSame(delegate.lastReview, feature.review.value)
    }

    @Test
    fun anUnquotedMoveIsNeverRequoted() {
        open()
        scheduler.runCurrent()

        scheduler.advanceTimeBy(10 * 60_000L)
        scheduler.runCurrent()

        assertEquals(1, delegate.reviews.size)
    }

    @Test
    fun theLastReviewStaysOnScreenWhileARequoteFails() {
        delegate.expiresInMs = 10_000L
        val feature = open()
        scheduler.runCurrent()
        val first = feature.review.value

        delegate.reviewError = PerpetualConfirmException(PerpetualConfirmError.ServiceUnavailable)
        scheduler.advanceTimeBy(9_000L)
        scheduler.runCurrent()

        assertEquals(2, delegate.reviews.size)
        assertSame(first, feature.review.value)
        assertEquals(PerpetualConfirmError.ServiceUnavailable, feature.loadError.value)
    }

    @Test
    fun confirmSubmitsTheReviewWithTheUnlockedWallet() {
        val feature = open()
        val events = collectEvents(feature)
        scheduler.runCurrent()

        feature.confirm(context)
        assertEquals(PerpetualSendingState.Loading, feature.sending.value)
        scheduler.runCurrent()

        assertEquals(listOf(delegate.lastReview to cryptoWallet), delegate.submits)
        assertEquals(PerpetualSendingState.Done, feature.sending.value)
        assertTrue(events.isEmpty())

        // The done state holds for a beat before the screen is told to leave.
        scheduler.advanceTimeBy(500L)
        scheduler.runCurrent()
        assertEquals(listOf(PerpetualConfirmEvent.Submitted), events)
        coVerify(exactly = 1) { passcodeManager.unlockMultichainVault<CryptoWallet>(any(), any()) }
    }

    @Test
    fun confirmIsIgnoredUntilTheQuoteIsIn() {
        val feature = open()

        feature.confirm(context)
        scheduler.runCurrent()

        assertTrue(delegate.submits.isEmpty())
        assertEquals(PerpetualSendingState.None, feature.sending.value)
    }

    @Test
    fun confirmIsIgnoredWhileThePrepareHasFailed() {
        delegate.reviewError = PerpetualConfirmException(PerpetualConfirmError.PrepareFailed)
        val feature = open()
        scheduler.runCurrent()

        feature.confirm(context)
        scheduler.runCurrent()

        assertTrue(delegate.submits.isEmpty())
    }

    @Test
    fun aSecondSwipeWhileSendingIsIgnored() {
        delegate.submitDelayMs = 1_000L
        val feature = open()
        scheduler.runCurrent()

        feature.confirm(context)
        feature.confirm(context)
        scheduler.advanceTimeBy(1_000L)
        scheduler.runCurrent()

        assertEquals(1, delegate.submits.size)
    }

    @Test
    fun aFailedSubmitResetsTheSwipeAndRetryResubmits() {
        delegate.submitError = PerpetualConfirmException(PerpetualConfirmError.ServiceUnavailable)
        val feature = open()
        scheduler.runCurrent()

        feature.confirm(context)
        scheduler.runCurrent()

        assertEquals(PerpetualSendingState.None, feature.sending.value)
        assertEquals(PerpetualConfirmError.ServiceUnavailable, feature.submitError.value)
        // Not a re-quote: the same review is still the one to send.
        assertEquals(1, delegate.reviews.size)

        delegate.submitError = null
        feature.retry(context)
        scheduler.runCurrent()

        assertEquals(2, delegate.submits.size)
        assertEquals(PerpetualSendingState.Done, feature.sending.value)
        assertNull(feature.submitError.value)
    }

    @Test
    fun aSubmitThatAsksForARequoteQuotesAgain() {
        delegate.submitError =
            PerpetualConfirmException(PerpetualConfirmError.Failed, requote = true)
        val feature = open()
        scheduler.runCurrent()

        feature.confirm(context)
        scheduler.runCurrent()

        assertEquals(2, delegate.reviews.size)
        assertEquals(PerpetualConfirmError.Failed, feature.submitError.value)
        assertSame(delegate.lastReview, feature.review.value)
    }

    @Test
    fun noRequoteWhileSending() {
        delegate.expiresInMs = 10_000L
        delegate.submitDelayMs = 20_000L
        val feature = open()
        scheduler.runCurrent()

        feature.confirm(context)
        scheduler.advanceTimeBy(15_000L)
        scheduler.runCurrent()

        assertEquals(1, delegate.reviews.size)
        assertEquals(PerpetualSendingState.Loading, feature.sending.value)
    }

    @Test
    fun aCancelledPasscodeLeavesTheSwipeReady() {
        coEvery { passcodeManager.unlockMultichainVault<CryptoWallet>(any(), any()) } throws CancellationException()
        val feature = open()
        scheduler.runCurrent()

        feature.confirm(context)
        scheduler.runCurrent()

        assertEquals(PerpetualSendingState.None, feature.sending.value)
        assertNull(feature.submitError.value)
        assertTrue(delegate.submits.isEmpty())
    }

    private fun open(): PerpetualConfirmFeature {
        val request = ConfirmRequest(
            data = CommonTransactionData(assetId = ASSET_ID, walletId = WALLET_ID),
            type = ConfirmType.PerpetualWithdraw(amount = AMOUNT),
        )
        return PerpetualConfirmFeature(request, delegate, accountRepo, passcodeManager)
    }

    private fun collectEvents(feature: PerpetualConfirmFeature): List<PerpetualConfirmEvent> {
        val events = mutableListOf<PerpetualConfirmEvent>()
        CoroutineScope(dispatchers.main).launch { feature.events.collect { events += it } }
        return events
    }

    private class FakeDelegate : PerpetualConfirmDelegate {

        val reviews = mutableListOf<Pair<ConfirmRequest, ConfirmType.PerpetualWithdraw>>()
        val submits = mutableListOf<Pair<PerpetualReview, CryptoWallet>>()
        var lastReview: PerpetualReview? = null

        var reviewError: Throwable? = null
        var submitError: Throwable? = null
        var submitDelayMs = 0L

        /** Relative to the wall clock, which is what the feature compares the expiry against. */
        var expiresInMs: Long? = null

        override suspend fun review(request: ConfirmRequest, withdraw: ConfirmType.PerpetualWithdraw): PerpetualReview {
            reviews += request to withdraw
            reviewError?.let { throw it }
            return PerpetualReview(
                walletId = request.data.walletId,
                expiresAtMs = expiresInMs?.let { System.currentTimeMillis() + it },
                display = PerpetualReview.Withdraw(
                    symbol = "USDC",
                    network = "Ethereum",
                    image = null,
                    amount = "$10",
                    receive = "10 USDC",
                    fee = null,
                    feeIsEstimate = false,
                    arrivalSeconds = 60L,
                ),
                plan = "plan-${reviews.size}",
            ).also { lastReview = it }
        }

        override suspend fun submit(review: PerpetualReview, wallet: CryptoWallet) {
            if (submitDelayMs > 0) {
                delay(submitDelayMs)
            }
            submits += review to wallet
            submitError?.let { throw it }
        }
    }

    private companion object {
        const val WALLET_ID = "wallet-1"
        const val ASSET_ID = "lighter/mainnet/usdc"
        val AMOUNT: BigInteger = BigInteger.parseString("10000000")
    }
}
