package com.tonapps.deposit.multicoin.screens.confirm

import androidx.lifecycle.ViewModelStore
import com.tonapps.blockchain.model.CommonTransactionData
import com.tonapps.blockchain.model.ConfirmAction
import com.tonapps.blockchain.model.ConfirmContext
import com.tonapps.blockchain.model.ConfirmRequest
import com.tonapps.blockchain.model.ConfirmType
import com.tonapps.chainkit.core.chain.api.models.NodeError
import com.tonapps.chainkit.core.chain.api.models.NodeRes
import com.tonapps.chainkit.core.chain.api.models.SignRes
import com.tonapps.deposit.multicoin.screens.confirm.base.ConfirmEvent
import com.tonapps.deposit.multicoin.screens.confirm.base.ConfirmationError
import com.tonapps.deposit.multicoin.screens.confirm.base.PendingTransaction
import com.tonapps.deposit.multicoin.screens.confirm.base.SUCCESS_HOLD_MS
import com.tonapps.deposit.multicoin.screens.confirm.base.SendingState
import com.tonapps.deposit.multicoin.screens.confirm.base.SignatureGate
import com.tonapps.wallet.ChainKitProvider
import com.tonapps.wallet.data.multichain.account.McAccountRepository
import com.tonapps.wallet.data.passcode.WalletUnlocker
import com.tonapps.wallet.testing.BaseUnitTest
import com.tonapps.wallet.testing.TestAssets
import com.tonapps.wallet.testing.TestWallet
import com.tonapps.wallet.testing.testAccount
import com.tonapps.wallet.testing.testWallet
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

class TxConfirmMessageSendTest : BaseUnitTest() {

    private val scheduler get() = dispatchers.scheduler

    private val accountRepo = mockk<McAccountRepository>()
    private val provider = mockk<ChainKitProvider>()
    private val walletUnlocker = mockk<WalletUnlocker>()
    private val confirmResults = mockk<ConfirmResultListener>(relaxed = true)
    private val store = ViewModelStore()
    private val collection by lazy { CoroutineScope(dispatchers.main) }

    private val requestGone = MutableStateFlow(false)

    @Before
    fun setUp() {
        coEvery { accountRepo.getWallet(any()) } returns testWallet()
        coEvery { accountRepo.findAccount(any(), any(), any(), any()) } returns testAccount(TestAssets.eth)
        coEvery { walletUnlocker.unlockWallet(any(), any()) } returns mockk()
        coEvery { provider.signMessage(any(), any(), any(), any()) } returns SignRes.ok(SIGNED_MESSAGE)
        every { confirmResults.gone(DAPP) } returns requestGone.filter { it }.map { }
        every { confirmResults.isGone(DAPP) } answers { requestGone.value }
    }

    @After
    fun tearDown() {
        collection.cancel()
        store.clear()
    }

    @Test
    fun aRequestGoneWhileASignedMessageIsSentKeepsTheSheetAndARetrySendsTheSameMessageAgain() {
        val gate = CompletableDeferred<Unit>()
        var sendCount = 0
        coEvery { provider.sendRawTransaction(any(), any()) } coAnswers {
            sendCount++
            if (sendCount == 1) {
                gate.await()
                NodeRes.err(NodeError.NetworkWrap(IllegalStateException("timeout")))
            } else {
                NodeRes.ok("5sent")
            }
        }
        val (feature, shown) = open()
        val events = CopyOnWriteArrayList<ConfirmEvent>()
        collection.launch { feature.events.collect { events += it } }
        scheduler.runCurrent()

        feature.sendTransaction(requireNotNull(shown.lastOrNull { it != null }) {
            "nothing was prepared; error: ${feature.confirmationError.value}"
        })
        scheduler.runCurrent()
        assertEquals(SendingState.Loading, feature.sendingState.value)

        requestGone.value = true
        scheduler.runCurrent()
        gate.complete(Unit)
        scheduler.runCurrent()
        scheduler.advanceTimeBy(SUCCESS_HOLD_MS)
        scheduler.runCurrent()

        coVerify(exactly = 1) { provider.sendRawTransaction(any(), SIGNED_TRANSACTION) }
        assertTrue(events.isEmpty())
        assertEquals(SendingState.None, feature.sendingState.value)
        assertEquals(ConfirmationError.NetworkError, feature.confirmationError.value)
        coVerify(exactly = 0) { confirmResults.approved(any(), any()) }
        coVerify(exactly = 0) { confirmResults.rejected(any()) }
        coVerify(exactly = 0) { confirmResults.failed(any()) }
        coVerify(exactly = 0) { confirmResults.closedAsGone(any()) }

        feature.retry()
        scheduler.runCurrent()
        feature.sendTransaction(requireNotNull(shown.lastOrNull { it != null }) {
            "nothing was prepared for the retry; error: ${feature.confirmationError.value}"
        })
        scheduler.runCurrent()
        scheduler.advanceTimeBy(SUCCESS_HOLD_MS)
        scheduler.runCurrent()

        coVerify(exactly = 2) { provider.sendRawTransaction(any(), SIGNED_TRANSACTION) }
        coVerify(exactly = 0) { confirmResults.closedAsGone(any()) }
        assertEquals(SendingState.Done, feature.sendingState.value)
        assertEquals(listOf<ConfirmEvent>(ConfirmEvent.Done), events)
    }

    @Test
    fun aRequestGoneWhileTheMessageIsSignedIsNotSent() {
        coEvery { provider.signMessage(any(), any(), any(), any()) } coAnswers {
            requestGone.value = true
            SignRes.ok(SIGNED_MESSAGE)
        }
        coEvery { provider.sendRawTransaction(any(), any()) } returns NodeRes.ok("5sent")
        val (feature, shown) = open()
        val events = CopyOnWriteArrayList<ConfirmEvent>()
        collection.launch { feature.events.collect { events += it } }
        scheduler.runCurrent()

        feature.sendTransaction(requireNotNull(shown.lastOrNull { it != null }) {
            "nothing was prepared; error: ${feature.confirmationError.value}"
        })
        scheduler.runCurrent()
        scheduler.advanceTimeBy(SUCCESS_HOLD_MS)
        scheduler.runCurrent()

        coVerify(exactly = 0) { provider.sendRawTransaction(any(), any()) }
        assertEquals(listOf<ConfirmEvent>(ConfirmEvent.Done), events)
        coVerify(exactly = 0) { confirmResults.closedAsGone(any()) }
        store.clear()
        scheduler.runCurrent()
        coVerify(exactly = 1) { confirmResults.closedAsGone(DAPP) }
        coVerify(exactly = 0) { confirmResults.approved(any(), any()) }
        coVerify(exactly = 0) { confirmResults.rejected(any()) }
        coVerify(exactly = 0) { confirmResults.failed(any()) }
    }

    @Test
    fun aRequestGoneWhileAMessageIsSignedOnlyIsNotApproved() {
        coEvery { provider.signMessage(any(), any(), any(), any()) } coAnswers {
            requestGone.value = true
            SignRes.ok(PERSONAL_SIGNATURE)
        }
        val (feature, shown) = open(
            type = ConfirmType.Message(data = PERSONAL_MESSAGE, type = ConfirmType.Message.Type.Personal),
            action = ConfirmAction.Sign,
        )
        val events = CopyOnWriteArrayList<ConfirmEvent>()
        collection.launch { feature.events.collect { events += it } }
        scheduler.runCurrent()

        feature.sendTransaction(requireNotNull(shown.lastOrNull { it != null }) {
            "nothing was prepared; error: ${feature.confirmationError.value}"
        })
        scheduler.runCurrent()
        scheduler.advanceTimeBy(SUCCESS_HOLD_MS)
        scheduler.runCurrent()

        assertEquals(listOf<ConfirmEvent>(ConfirmEvent.Done), events)
        coVerify(exactly = 0) { confirmResults.approved(any(), any()) }
        coVerify(exactly = 0) { confirmResults.closedAsGone(any()) }
        store.clear()
        scheduler.runCurrent()
        coVerify(exactly = 1) { confirmResults.closedAsGone(DAPP) }
        coVerify(exactly = 0) { confirmResults.approved(any(), any()) }
        coVerify(exactly = 0) { confirmResults.rejected(any()) }
        coVerify(exactly = 0) { confirmResults.failed(any()) }
    }

    @Test
    fun aRequestGoneAfterAnUnknownOutcomeClosesTheSheetOnceARetryIsRejected() {
        var sendCount = 0
        coEvery { provider.sendRawTransaction(any(), any()) } coAnswers {
            sendCount++
            if (sendCount == 1) {
                NodeRes.err(NodeError.NetworkWrap(IllegalStateException("timeout")))
            } else {
                NodeRes.err(NodeError.Rejected(code = -32002, message = "blockhash not found"))
            }
        }
        val (feature, shown) = open()
        val events = CopyOnWriteArrayList<ConfirmEvent>()
        collection.launch { feature.events.collect { events += it } }
        scheduler.runCurrent()

        feature.sendTransaction(requireNotNull(shown.lastOrNull { it != null }) {
            "nothing was prepared; error: ${feature.confirmationError.value}"
        })
        scheduler.runCurrent()
        assertEquals(ConfirmationError.NetworkError, feature.confirmationError.value)

        requestGone.value = true
        scheduler.runCurrent()
        assertTrue(events.isEmpty())
        coVerify(exactly = 0) { confirmResults.closedAsGone(any()) }

        feature.retry()
        scheduler.runCurrent()
        feature.sendTransaction(requireNotNull(shown.lastOrNull { it != null }) {
            "nothing was prepared for the retry; error: ${feature.confirmationError.value}"
        })
        scheduler.runCurrent()

        coVerify(exactly = 2) { provider.sendRawTransaction(any(), SIGNED_TRANSACTION) }
        assertEquals(listOf<ConfirmEvent>(ConfirmEvent.Done), events)
        coVerify(exactly = 0) { confirmResults.closedAsGone(any()) }
        store.clear()
        scheduler.runCurrent()
        coVerify(exactly = 1) { confirmResults.closedAsGone(DAPP) }
        coVerify(exactly = 0) { confirmResults.approved(any(), any()) }
    }

    private fun open(
        type: ConfirmType.Message = ConfirmType.Message(data = UNSIGNED_TRANSACTION, type = ConfirmType.Message.Type.Transaction),
        action: ConfirmAction = ConfirmAction.SignAndSend,
    ): Pair<TxConfirmFeature, List<PendingTransaction?>> {
        val feature = TxConfirmFeature(
            request = ConfirmRequest(
                data = CommonTransactionData(assetId = TestAssets.eth.id, walletId = TestWallet.ID),
                type = type,
                action = action,
                context = DAPP,
            ),
            provider = provider,
            accountRepo = accountRepo,
            postTransactionRefreshSchedule = mockk(relaxed = true),
            walletUnlocker = walletUnlocker,
            signatureGate = SignatureGate { false },
            confirmResults = confirmResults,
            feeBuilder = mockk(relaxed = true),
            gaslessSender = mockk(relaxed = true),
            settingsRepository = mockk(relaxed = true),
            ledgerSigner = mockk(relaxed = true),
        )
        store.put("tx-confirm", feature)

        val shown = CopyOnWriteArrayList<PendingTransaction?>()
        collection.launch { feature.pendingTx.collect { shown += it } }
        return feature to shown
    }

    private companion object {
        const val UNSIGNED_TRANSACTION = "AQID"
        const val SIGNED_TRANSACTION = "BAUG"
        const val SIGNED_MESSAGE = """{"signature":"5sig","transaction":"$SIGNED_TRANSACTION"}"""
        const val PERSONAL_MESSAGE = "0x48656c6c6f"
        const val PERSONAL_SIGNATURE = "0xpersonalsig"
        val DAPP = ConfirmContext.Dapp(
            requestId = "request-1",
            topic = "topic-1",
            name = "Jupiter",
            url = "jup.ag",
        )
    }
}
