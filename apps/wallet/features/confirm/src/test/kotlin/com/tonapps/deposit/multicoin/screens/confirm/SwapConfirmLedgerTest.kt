package com.tonapps.deposit.multicoin.screens.confirm

import com.ionspin.kotlin.bignum.integer.BigInteger
import com.tonapps.blockchain.model.ConfirmRequest
import com.tonapps.chainkit.core.chain.api.models.ChainRes
import com.tonapps.chainkit.core.chain.api.models.NodeError
import com.tonapps.chainkit.core.chain.api.models.NodeRes
import com.tonapps.chainkit.core.chain.model.transaction.Fee
import com.tonapps.chainkit.core.chain.model.transaction.Transaction
import com.tonapps.deposit.multicoin.screens.confirm.SwapConfirmTestEnv.Companion.TON_SPLIT
import com.tonapps.deposit.multicoin.screens.confirm.base.ConfirmationError
import com.tonapps.deposit.multicoin.screens.confirm.base.PendingTransaction
import com.tonapps.deposit.multicoin.screens.confirm.base.SendingState
import com.tonapps.ledgerkit.LedgerFailure
import com.tonapps.ledgerkit.LedgerFailureException
import com.tonapps.wallet.data.ledger.signing.LedgerTransactionRequest
import com.tonapps.wallet.data.multichain.wallet.CredentialIds
import com.tonapps.wallet.data.multichain.wallet.McWalletEntity
import com.tonapps.wallet.data.multichain.wallet.McWalletType
import com.tonapps.wallet.testing.BaseUnitTest
import com.tonapps.wallet.testing.TestAssets
import com.tonapps.wallet.testing.TestWallet
import com.tonapps.wallet.testing.testAccount
import io.mockk.coEvery
import io.mockk.coVerify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

class SwapConfirmLedgerTest : BaseUnitTest() {

    private lateinit var env: SwapConfirmTestEnv
    private lateinit var feature: SwapConfirmFeature
    private lateinit var shown: List<PendingTransaction?>

    private val scheduler get() = dispatchers.scheduler

    private val signed = CopyOnWriteArrayList<List<LedgerTransactionRequest>>()
    private val chain = FakeSeqnoChain(START_SEQNO)

    @Before
    fun setUp() {
        env = SwapConfirmTestEnv(dispatchers.main)
        env.wallet = McWalletEntity(
            credentialId = CredentialIds.hardwareDescriptor(TestWallet.ID),
            name = "Ledger",
            id = TestWallet.ID,
            type = McWalletType.Multicoin,
        )
        env.accounts[SwapConfirmFixtures.USDT_TON_ASSET_ID] =
            testAccount(TestAssets.usdtTon, balance = SPLIT_SOURCE_AMOUNT.toString(), price = "1.0")
        env.quoteAnswer = { SwapConfirmFixtures.quote(TON_SPLIT) }
        env.feeAnswer = { ChainRes.ok(Fee.Value(it.account.chain.toAsset(), GAS)) }
        env.nonceAnswer = { chain.poll() }
        env.sendAnswer = { chain.send() }
        coEvery { env.ledgerSigner.signTransactions(any(), any()) } answers {
            val requests = secondArg<List<LedgerTransactionRequest>>()
            signed += requests
            requests.indices.map { byteArrayOf(it.toByte()) }
        }
    }

    @After
    fun tearDown() {
        env.clear()
    }

    @Test
    fun aSplitRouteIsSignedOnLedgerAsOneTransactionPerMessage() {
        confirm(splitRequest())

        val requests = signed.single()
        val messages = splitMessages()
        assertEquals(messages.size, requests.size)
        requests.forEachIndexed { index, request ->
            val swap = request.transaction as Transaction.Swap
            assertEquals("seqno #$index", BigInteger.fromInt(START_SEQNO + index), request.nonce)
            assertEquals("to #$index", messages[index].to, swap.to.display)
            assertEquals("amount #$index", messages[index].amount, swap.amount)
            assertEquals("data #$index", messages[index].data, swap.data)
            assertEquals("max #$index", false, swap.isMax)
        }
    }

    @Test
    fun eachNextMessageIsSentOnlyOnceTheWalletSeqnoReachesIt() {
        confirm(splitRequest())

        assertEquals(listOf(START_SEQNO, START_SEQNO + 1, START_SEQNO + 2), chain.seqnoAtSend)
        env.sent.forEachIndexed { index, sent ->
            assertArrayEquals("signature #$index", byteArrayOf(index.toByte()), sent.data)
        }
        assertNull(feature.confirmationError.value)
        assertEquals(SendingState.Done, feature.sendingState.value)
    }

    @Test
    fun theSwapIsReportedWithTheFirstMessageOnly() {
        confirm(splitRequest())

        val (first, rest) = env.sent.first() to env.sent.drop(1)
        assertEquals("omniston", first.service)
        assertEquals(SPLIT_ROUTE_ID, first.routeId)
        assertEquals(SPLIT_PROVIDER_ROUTE_ID, first.serviceTxId)
        assertEquals(SPLIT_SOURCE_AMOUNT, first.sellBaseAmount)
        assertEquals(2, rest.size)
        rest.forEach { sent ->
            assertNull(sent.service)
            assertNull(sent.routeId)
            assertNull(sent.serviceTxId)
            assertNull(sent.sellBaseAmount)
        }
    }

    @Test
    fun aMessageRejectedOnLedgerSendsNothing() {
        coEvery { env.ledgerSigner.signTransactions(any(), any()) } throws
            LedgerFailureException(LedgerFailure.UserRefused(statusWord = 0x6985))

        confirm(splitRequest())

        assertTrue(env.sent.isEmpty())
        assertNotNull(feature.confirmationError.value)
        assertEquals(SendingState.None, feature.sendingState.value)
    }

    @Test
    fun aFailedFirstMessageIsSentAgainWithoutSigningAgain() {
        var failNext = true
        env.sendAnswer = {
            if (env.sent.isEmpty() && failNext) {
                failNext = false
                NodeRes.err(NodeError.NetworkWrap(IllegalStateException("timeout")))
            } else {
                chain.send()
            }
        }

        confirm(splitRequest())

        assertTrue(env.sent.isEmpty())
        assertEquals(ConfirmationError.NetworkError, feature.confirmationError.value)

        feature.retry()
        settle()

        assertEquals(3, env.sent.size)
        assertEquals(listOf(START_SEQNO, START_SEQNO + 1, START_SEQNO + 2), chain.seqnoAtSend)
        assertEquals(SendingState.Done, feature.sendingState.value)
        coVerify(exactly = 1) { env.ledgerSigner.signTransactions(any(), any()) }
    }

    @Test
    fun aFailedLaterMessageEndsTheSwapAsPartlySent() {
        env.sendAnswer = {
            if (env.sent.size == 1) {
                NodeRes.err(NodeError.NetworkWrap(IllegalStateException("cannot apply external message to current state")))
            } else {
                chain.send()
            }
        }

        confirm(splitRequest())

        assertEquals(1, env.sent.size)
        assertEquals(ConfirmationError.PartiallySent, feature.confirmationError.value)
        assertEquals(SendingState.None, feature.sendingState.value)

        feature.retry()
        settle()

        assertEquals(1, env.sent.size)
        assertEquals(ConfirmationError.PartiallySent, feature.confirmationError.value)
        coVerify(exactly = 1) { env.ledgerSigner.signTransactions(any(), any()) }
    }

    @Test
    fun aPartlySentOneClickSwapBlocksItsQuoteInsteadOfEndingIt() {
        env.sendAnswer = {
            if (env.sent.size == 1) {
                NodeRes.err(NodeError.NetworkWrap(IllegalStateException("cannot apply external message to current state")))
            } else {
                chain.send()
            }
        }
        open(splitRequest())
        val scope = CoroutineScope(dispatchers.main)
        val headless = HeadlessSwapConfirm(feature, scope, review = false, feeId = null)

        headless.start()
        settle()

        assertEquals(HeadlessSwapPhase.Blocked(ConfirmationError.PartiallySent), headless.updates.value.phase)
        assertEquals(1, env.sent.size)
        scope.cancel()
    }

    @Test
    fun aSeqnoThatNeverMovesStillTriesTheNextMessageAfterTheWait() {
        chain.landing = false
        env.sendAnswer = {
            if (env.sent.isEmpty()) {
                chain.send()
            } else {
                NodeRes.err(NodeError.NetworkWrap(IllegalStateException("cannot apply external message to current state")))
            }
        }

        open(splitRequest())
        feature.sendTransaction(prepared())
        scheduler.advanceTimeBy(NONCE_WAIT_MS - 1)
        scheduler.runCurrent()
        assertNull(feature.confirmationError.value)

        scheduler.advanceTimeBy(1)
        scheduler.runCurrent()

        assertEquals(1, env.sent.size)
        assertEquals(ConfirmationError.PartiallySent, feature.confirmationError.value)
    }

    @Test
    fun aSingleMessageSwapIsSignedAndSentWithoutWaiting() {
        env.quoteAnswer = { SwapConfirmFixtures.quote(SwapConfirmTestEnv.EXACT_TON) }

        confirm(SwapConfirmFixtures.swapRequest())

        val request = signed.single().single()
        assertEquals(BigInteger.fromInt(START_SEQNO), request.nonce)
        assertEquals(1, env.sent.size)
        assertEquals(0, chain.polls)
        assertNotNull(env.sent.single().routeId)
        assertEquals(SendingState.Done, feature.sendingState.value)
    }

    private fun confirm(request: ConfirmRequest) {
        open(request)
        feature.sendTransaction(prepared())
        settle()
    }

    private fun open(request: ConfirmRequest) {
        feature = env.feature(request)
        shown = env.collectPending(feature)
        scheduler.runCurrent()
        chain.polls = 0
    }

    private fun prepared(): PendingTransaction = requireNotNull(shown.lastOrNull { it != null }) {
        "nothing was prepared; error: ${feature.confirmationError.value}"
    }

    private fun settle() {
        repeat(SETTLE_STEPS) {
            scheduler.advanceTimeBy(SETTLE_STEP_MS)
            scheduler.runCurrent()
        }
    }

    private fun splitRequest() = SwapConfirmFixtures.swapRequest(
        assetId = SwapConfirmFixtures.USDT_TON_ASSET_ID,
        destinationAssetId = SwapConfirmFixtures.TON_ASSET_ID,
        sourceAmount = SPLIT_SOURCE_AMOUNT,
    )

    private fun splitMessages(): List<RouteMessage> {
        val payload = SwapConfirmFixtures.quote(TON_SPLIT).routes.single().payloads.orEmpty().single().payload
        return Json.parseToJsonElement(payload).jsonArray.map { message ->
            val fields = message.jsonObject
            RouteMessage(
                to = fields.getValue("address").jsonPrimitive.content,
                amount = BigInteger.parseString(fields.getValue("amount").jsonPrimitive.content),
                data = fields.getValue("payload").jsonPrimitive.content,
            )
        }
    }

    private class RouteMessage(val to: String, val amount: BigInteger, val data: String)

    private class FakeSeqnoChain(private var seqno: Int) {

        var landing = true
        var polls = 0
        val seqnoAtSend = mutableListOf<Int>()
        private var pollsSinceSend = 0
        private var inFlight = 0

        fun poll(): NodeRes<BigInteger> {
            polls++
            pollsSinceSend++
            if (landing && pollsSinceSend > LANDING_POLLS) {
                seqno += inFlight
                inFlight = 0
            }
            return NodeRes.ok(BigInteger.fromInt(seqno))
        }

        fun send(): NodeRes<String> {
            seqnoAtSend += seqno
            inFlight++
            pollsSinceSend = 0
            return NodeRes.ok("hash-${seqnoAtSend.size}")
        }
    }

    private companion object {
        const val START_SEQNO = 7
        const val LANDING_POLLS = 2
        const val SETTLE_STEPS = 40
        const val SETTLE_STEP_MS = 1_000L
        const val NONCE_WAIT_MS = 120_000L

        val GAS: BigInteger = BigInteger.parseString("12000000")
        val SPLIT_SOURCE_AMOUNT: BigInteger = BigInteger.parseString("3000000000")
        const val SPLIT_ROUTE_ID = "b709085b-135d-403f-9c20-b174dcc4df4e"
        const val SPLIT_PROVIDER_ROUTE_ID = "a539b9d0215bceb2f8c60df437136af8b912d8c3385a402e1a6ef7d94ed5d98c"
    }
}
