package com.tonapps.deposit.multicoin.screens.confirm

import androidx.lifecycle.ViewModelStore
import com.ionspin.kotlin.bignum.integer.BigInteger
import com.tonapps.blockchain.model.CommonTransactionData
import com.tonapps.blockchain.model.ConfirmAction
import com.tonapps.blockchain.model.ConfirmContext
import com.tonapps.blockchain.model.ConfirmOperations
import com.tonapps.blockchain.model.ConfirmRequest
import com.tonapps.blockchain.model.ConfirmType
import com.tonapps.bus.core.AnalyticsHelper
import com.tonapps.bus.core.contract.EventExecutor
import com.tonapps.bus.generated.DefaultEvents
import com.tonapps.chainkit.core.chain.api.models.ChainRes
import com.tonapps.chainkit.core.chain.api.models.NodeError
import com.tonapps.chainkit.core.chain.api.models.NodeRes
import com.tonapps.chainkit.core.chain.api.models.SignRes
import com.tonapps.chainkit.core.chain.model.account.Chain
import com.tonapps.chainkit.core.chain.model.account.MnemonicWallet
import com.tonapps.chainkit.core.chain.model.transaction.Fee
import com.tonapps.chainkit.core.chain.model.transaction.Transaction
import com.tonapps.chainkit.core.op.Payload
import com.tonapps.chainkit.core.op.PayloadMeta
import com.tonapps.chainkit.core.op.SignedPayload
import com.tonapps.chainkit.core.op.ValidationRes
import com.tonapps.deposit.multicoin.screens.confirm.base.BaseConfirmFeature
import com.tonapps.deposit.multicoin.screens.confirm.base.ConfirmEvent
import com.tonapps.deposit.multicoin.screens.confirm.base.ConfirmationError
import com.tonapps.deposit.multicoin.screens.confirm.base.SUCCESS_HOLD_MS
import com.tonapps.deposit.multicoin.screens.confirm.base.SendingState
import com.tonapps.deposit.multicoin.screens.confirm.base.SignatureGate
import com.tonapps.deposit.multicoin.screens.confirm.base.operation.OperationConfirmAction
import com.tonapps.deposit.multicoin.screens.confirm.dapp.DappTxConfirmFeature
import com.tonapps.wallet.ChainKitProvider
import com.tonapps.wallet.data.multichain.account.McAccountRepository
import com.tonapps.wallet.data.multichain.tx.PostTransactionRefreshSchedule
import com.tonapps.wallet.data.passcode.WalletUnlocker
import com.tonapps.wallet.testing.BaseUnitTest
import com.tonapps.wallet.testing.FakeTimeProvider
import com.tonapps.wallet.testing.TestAssets
import com.tonapps.wallet.testing.TestWallet
import com.tonapps.wallet.testing.testAccount
import com.tonapps.wallet.testing.testWallet
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

/**
 * A dapp's EVM transaction, confirmed as the operation the dapp sent: signed as it reads, and the
 * dapp answered exactly once. The chain is a mocked [ChainKitProvider].
 */
class DappTxConfirmFeatureTest : BaseUnitTest() {

    private val accountRepo = mockk<McAccountRepository>()
    private val walletUnlocker = mockk<WalletUnlocker>()
    private val provider = mockk<ChainKitProvider>()
    private val refreshSchedule = mockk<PostTransactionRefreshSchedule>(relaxed = true)
    private val confirmResults = mockk<ConfirmResultListener>(relaxed = true)
    private val store = ViewModelStore()

    private val accounts = mapOf(
        TestAssets.usdtEth.id to testAccount(TestAssets.usdtEth, balance = "100000000"),
        TestAssets.eth.id to testAccount(TestAssets.eth, balance = ONE_ETH, price = "2000"),
    )
    private val signed = mutableListOf<Payload>()
    private val broadcast = mutableListOf<String>()
    private val tracked = CopyOnWriteArrayList<Pair<String, Map<String, Any>>>()

    private var broadcastError: Throwable? = null
    private var broadcastGate: CompletableDeferred<Unit>? = null
    private val requestGone = MutableStateFlow(false)

    private val scheduler get() = dispatchers.scheduler

    @Before
    fun setUp() {
        coEvery { accountRepo.getWallet(TestWallet.ID) } returns testWallet()
        coEvery { accountRepo.findAccount(TestWallet.ID, any(), any(), any()) } answers { accounts[secondArg<String>()] }
        coEvery { walletUnlocker.unlockWallet(any(), any()) } returns mockk()
        coEvery { provider.estimateFee(any<Payload>(), any(), any()) } returns ChainRes.ok(PAYLOAD_FEE)
        coEvery { provider.getNonce(any()) } returns NodeRes.ok(BigInteger.fromInt(7))
        coEvery { provider.validateOperation(any(), any(), any()) } returns ValidationRes.ok(Unit)
        coEvery { provider.signPayload(any(), any(), any(), any(), any()) } answers {
            val payload = firstArg<Payload>()
            signed += payload
            SignRes.ok(SignedPayload.signed(payload, "0xsigned${payload.id}"))
        }
        coEvery { provider.broadcastPayload(any(), any()) } coAnswers {
            broadcastGate?.await()
            broadcastError?.let { throw it }
            broadcast += (firstArg<SignedPayload>().data as JsonPrimitive).content
            NodeRes.ok("0xhash${broadcast.size}")
        }
        coEvery { provider.reportPayloadSent(any(), any(), any(), any(), any()) } returns Unit

        val recording = object : EventExecutor {
            override fun trackEvent(eventName: String, props: Map<String, Any>) {
                tracked += eventName to props
            }
        }
        val helper = mockk<AnalyticsHelper> {
            every { eventsTaggedWith(any()) } returns DefaultEvents(recording)
            every { events } returns DefaultEvents(recording)
        }
        mockkObject(AnalyticsHelper.Companion)
        every { AnalyticsHelper.Default } returns helper
    }

    @After
    fun tearDown() {
        store.clear()
        unmockkObject(AnalyticsHelper.Companion)
    }

    @Test
    fun aSentTransactionIsAnsweredWithItsHash() {
        val feature = open(call())
        scheduler.runCurrent()

        feature.sendAction(OperationConfirmAction.Confirm)
        scheduler.runCurrent()

        assertEquals(listOf("0xsigned0"), broadcast)
        coVerify(exactly = 1) { confirmResults.approved(DAPP, "0xhash1") }
        verify(exactly = 1) { refreshSchedule.notifyTransactionSent(TestWallet.ID, Chain.Ethereum.Mainnet) }
        assertEquals(SendingState.Done, feature.sendingState.value)
    }

    @Test
    fun aRequestToSignOnlyIsAnsweredWithTheSignatureAndSendsNothing() {
        val feature = open(call(), action = ConfirmAction.Sign)
        scheduler.runCurrent()

        feature.sendAction(OperationConfirmAction.Confirm)
        scheduler.runCurrent()

        coVerify(exactly = 1) { confirmResults.approved(DAPP, "0xsigned0") }
        assertTrue(broadcast.isEmpty())
        verify(exactly = 0) { refreshSchedule.notifyTransactionSent(any(), any()) }
        assertTrue(tracked.none { (name, _) -> name == "transaction_sent" })
        assertEquals(SendingState.Done, feature.sendingState.value)
    }

    @Test
    fun aTokenTransferIsSignedAsTheTokenContractsCall() {
        val feature = open(tokenTransfer(), assetId = TestAssets.usdtEth.id)
        scheduler.runCurrent()

        val shown = feature.transaction.value as Transaction.Transfer
        assertEquals(TestAssets.usdtEth.id, shown.account.asset.id)
        assertEquals(TOKEN_AMOUNT, shown.amount)
        assertEquals(RECIPIENT.lowercase(), shown.to.display.lowercase())

        feature.sendAction(OperationConfirmAction.Confirm)
        scheduler.runCurrent()

        // Never a transfer of the coin to the recipient: the contract's call, coin value zero.
        val meta = signed.single().meta as PayloadMeta.Evm.Main
        assertEquals(USDT_CONTRACT.lowercase(), meta.to.display.lowercase())
        assertEquals(BigInteger.ZERO, meta.value)
        assertEquals(TRANSFER_CALLDATA, meta.data)

        val sent = tracked.single { (name, _) -> name == "transaction_sent" }.second
        assertEquals("transfer", sent["category"])
        assertEquals("token", sent["category_detail"])
        assertEquals(TestAssets.usdtEth.id, sent["asset"])
        assertEquals("walletconnect", sent["initiated_by"])
    }

    @Test
    fun aSheetClosedBeforeThePasscodeRejectsTheRequest() {
        open(call())
        scheduler.runCurrent()

        store.clear()
        scheduler.runCurrent()

        coVerify(exactly = 1) { confirmResults.rejected(DAPP) }
        coVerify(exactly = 0) { confirmResults.failed(any()) }
    }

    @Test
    fun aSendThatFailedAfterThePasscodeIsAnsweredAsFailed() {
        broadcastError = IllegalStateException("node is down")
        val feature = open(call())
        scheduler.runCurrent()

        feature.sendAction(OperationConfirmAction.Confirm)
        scheduler.runCurrent()
        feature.reject()
        scheduler.runCurrent()

        coVerify(exactly = 1) { confirmResults.failed(DAPP) }
        coVerify(exactly = 0) { confirmResults.rejected(any()) }
        coVerify(exactly = 0) { confirmResults.approved(any(), any()) }
    }

    @Test
    fun anApprovedRequestIsNotAnsweredAgainOnClose() {
        val feature = open(call())
        scheduler.runCurrent()
        feature.sendAction(OperationConfirmAction.Confirm)
        scheduler.runCurrent()

        feature.reject()
        store.clear()
        scheduler.runCurrent()

        coVerify(exactly = 1) { confirmResults.approved(DAPP, "0xhash1") }
        coVerify(exactly = 0) { confirmResults.rejected(any()) }
        coVerify(exactly = 0) { confirmResults.failed(any()) }
    }

    @Test
    fun aRequestGoneBeforeTheUserConfirmsClosesTheSheetUnanswered() {
        stubRequestGone()
        val feature = open(call())
        val events = collectEvents(feature)
        scheduler.runCurrent()

        requestGone.value = true
        scheduler.runCurrent()

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
    fun aRequestGoneWhileThePasscodeIsShownIsNeitherSignedNorSent() {
        stubRequestGone()
        coEvery { walletUnlocker.unlockWallet(any(), any()) } coAnswers {
            requestGone.value = true
            mockk<MnemonicWallet>()
        }
        val feature = open(call())
        val events = collectEvents(feature)
        scheduler.runCurrent()

        feature.sendAction(OperationConfirmAction.Confirm)
        scheduler.runCurrent()

        assertTrue(signed.isEmpty())
        assertTrue(broadcast.isEmpty())
        assertEquals(listOf<ConfirmEvent>(ConfirmEvent.Done), events)
        store.clear()
        scheduler.runCurrent()
        assertTrue(signed.isEmpty())
        assertTrue(broadcast.isEmpty())
        coVerify(exactly = 0) { confirmResults.approved(any(), any()) }
        coVerify(exactly = 0) { confirmResults.rejected(any()) }
        coVerify(exactly = 0) { confirmResults.failed(any()) }
    }

    @Test
    fun aRequestGoneWhileTheOperationIsSignedIsNotSent() {
        stubRequestGone()
        coEvery { provider.signPayload(any(), any(), any(), any(), any()) } answers {
            val payload = firstArg<Payload>()
            signed += payload
            requestGone.value = true
            SignRes.ok(SignedPayload.signed(payload, "0xsigned${payload.id}"))
        }
        val feature = open(call())
        val events = collectEvents(feature)
        scheduler.runCurrent()

        feature.sendAction(OperationConfirmAction.Confirm)
        scheduler.runCurrent()
        scheduler.advanceTimeBy(SUCCESS_HOLD_MS)
        scheduler.runCurrent()

        assertTrue(broadcast.isEmpty())
        assertEquals(listOf<ConfirmEvent>(ConfirmEvent.Done), events)
        coVerify(exactly = 0) { confirmResults.closedAsGone(any()) }
        store.clear()
        scheduler.runCurrent()
        coVerify(exactly = 1) { confirmResults.closedAsGone(DAPP) }
        assertTrue(broadcast.isEmpty())
        coVerify(exactly = 0) { confirmResults.approved(any(), any()) }
        coVerify(exactly = 0) { confirmResults.rejected(any()) }
        coVerify(exactly = 0) { confirmResults.failed(any()) }
    }

    @Test
    fun aRequestGoneAfterTheSheetsOwnApprovalKeepsTheApproval() {
        stubRequestGone()
        val feature = open(call())
        val events = collectEvents(feature)
        scheduler.runCurrent()
        feature.sendAction(OperationConfirmAction.Confirm)
        scheduler.runCurrent()

        requestGone.value = true
        scheduler.runCurrent()
        scheduler.advanceTimeBy(SUCCESS_HOLD_MS)
        scheduler.runCurrent()
        store.clear()
        scheduler.runCurrent()

        assertEquals(listOf<ConfirmEvent>(ConfirmEvent.Done), events)
        coVerify(exactly = 1) { confirmResults.approved(DAPP, "0xhash1") }
        coVerify(exactly = 0) { confirmResults.rejected(any()) }
        coVerify(exactly = 0) { confirmResults.failed(any()) }
        coVerify(exactly = 0) { confirmResults.closedAsGone(any()) }
    }

    @Test
    fun aRequestGoneWhileTheSendIsInFlightClosesTheSheetOnlyOnceTheSendEnds() {
        stubRequestGone()
        val gate = CompletableDeferred<Unit>()
        broadcastGate = gate
        val feature = open(call())
        val events = collectEvents(feature)
        scheduler.runCurrent()
        feature.sendAction(OperationConfirmAction.Confirm)
        scheduler.runCurrent()
        assertEquals(SendingState.Loading, feature.sendingState.value)

        requestGone.value = true
        scheduler.runCurrent()
        assertTrue(events.isEmpty())

        gate.complete(Unit)
        scheduler.runCurrent()
        coVerify(exactly = 1) { confirmResults.approved(DAPP, "0xhash1") }
        scheduler.advanceTimeBy(SUCCESS_HOLD_MS)
        scheduler.runCurrent()

        assertEquals(listOf<ConfirmEvent>(ConfirmEvent.Done), events)
        assertEquals(listOf("0xsigned0"), broadcast)
        coVerify(exactly = 0) { confirmResults.rejected(any()) }
        coVerify(exactly = 0) { confirmResults.failed(any()) }
    }

    @Test
    fun aRequestGoneWhileTheSendIsInFlightKeepsTheSheetAndItsErrorWhenTheOutcomeIsUnknown() {
        stubRequestGone()
        val gate = CompletableDeferred<Unit>()
        broadcastGate = gate
        broadcastError = NodeError.NetworkWrap(IllegalStateException("timeout"))
        val feature = open(call())
        val events = collectEvents(feature)
        scheduler.runCurrent()
        feature.sendAction(OperationConfirmAction.Confirm)
        scheduler.runCurrent()
        assertEquals(SendingState.Loading, feature.sendingState.value)

        requestGone.value = true
        scheduler.runCurrent()
        gate.complete(Unit)
        scheduler.runCurrent()
        scheduler.advanceTimeBy(SUCCESS_HOLD_MS)
        scheduler.runCurrent()

        assertTrue(events.isEmpty())
        assertEquals(SendingState.None, feature.sendingState.value)
        assertEquals(ConfirmationError.NetworkError, feature.confirmationError.value)
        coVerify(exactly = 0) { confirmResults.approved(any(), any()) }
        coVerify(exactly = 0) { confirmResults.rejected(any()) }
        coVerify(exactly = 0) { confirmResults.failed(any()) }
        coVerify(exactly = 0) { confirmResults.closedAsGone(any()) }
    }

    private fun stubRequestGone() {
        every { confirmResults.gone(DAPP) } returns requestGone.filter { it }.map { }
        every { confirmResults.isGone(DAPP) } answers { requestGone.value }
    }

    private fun collectEvents(feature: BaseConfirmFeature<*, *>): List<ConfirmEvent> {
        val events = mutableListOf<ConfirmEvent>()
        CoroutineScope(dispatchers.main).launch { feature.events.collect { events += it } }
        return events
    }

    private fun open(
        type: ConfirmType,
        assetId: String = TestAssets.eth.id,
        action: ConfirmAction = ConfirmAction.SignAndSend,
    ): DappTxConfirmFeature {
        val feature = DappTxConfirmFeature(
            request = ConfirmRequest(
                data = CommonTransactionData(assetId = assetId, walletId = TestWallet.ID),
                type = type,
                action = action,
                context = DAPP,
            ),
            provider = provider,
            accountRepo = accountRepo,
            walletUnlocker = walletUnlocker,
            signatureGate = SignatureGate { false },
            postTransactionRefreshSchedule = refreshSchedule,
            timeProvider = FakeTimeProvider(),
            confirmResults = confirmResults,
        )
        store.put("dapp-tx-confirm", feature)
        return feature
    }

    private fun call() = ConfirmType.Call(
        operation = ConfirmOperations.call(
            chain = Chain.Ethereum.Mainnet,
            contract = RECIPIENT,
            data = "0xd0e30db0",
            amount = BigInteger.ZERO,
        ),
    )

    // As the parser makes it: the calldata `transfer(RECIPIENT, TOKEN_AMOUNT)` sent to the token contract.
    private fun tokenTransfer() = ConfirmType.Transfer(
        operation = ConfirmOperations.call(
            chain = Chain.Ethereum.Mainnet,
            contract = USDT_CONTRACT,
            data = TRANSFER_CALLDATA,
            amount = BigInteger.ZERO,
        ),
        tokenTransfer = ConfirmType.Transfer.TokenTransfer(to = RECIPIENT, amount = TOKEN_AMOUNT),
    )

    private companion object {
        const val ONE_ETH = "1000000000000000000"
        const val USDT_CONTRACT = "0xdAC17F958D2ee523a2206206994597C13D831ec7"
        const val RECIPIENT = "0x3B4D794a66304F130a4Db8F2551B0070dfCf5ca7"
        const val TRANSFER_CALLDATA = "0xa9059cbb" +
            "0000000000000000000000003b4d794a66304f130a4db8f2551b0070dfcf5ca7" +
            "00000000000000000000000000000000000000000000000000000000000f4240"
        val TOKEN_AMOUNT: BigInteger = BigInteger.fromInt(1_000_000)
        val PAYLOAD_FEE = Fee.Value(Chain.Ethereum.Mainnet.toAsset(), BigInteger.parseString("300000000000000"))
        val DAPP = ConfirmContext.Dapp(
            requestId = "request-1",
            topic = "topic-1",
            name = "Uniswap",
            url = "app.uniswap.org",
        )
    }
}
