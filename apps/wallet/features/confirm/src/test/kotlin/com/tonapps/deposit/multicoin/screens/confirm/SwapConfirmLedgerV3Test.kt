package com.tonapps.deposit.multicoin.screens.confirm

import com.ionspin.kotlin.bignum.integer.BigInteger
import com.tonapps.blockchain.model.ConfirmRequest
import com.tonapps.blockchain.model.ConfirmType
import com.tonapps.chainkit.core.chain.api.models.ChainRes
import com.tonapps.chainkit.core.chain.api.models.NodeError
import com.tonapps.chainkit.core.chain.api.models.NodeRes
import com.tonapps.chainkit.core.chain.model.transaction.Fee
import com.tonapps.chainkit.core.chain.model.transaction.Transaction
import com.tonapps.chainkit.core.op.Intent
import com.tonapps.chainkit.core.op.Payload
import com.tonapps.chainkit.core.op.PayloadMeta
import com.tonapps.chainkit.core.op.ValidationError
import com.tonapps.chainkit.core.op.ValidationRes
import com.tonapps.core.flags.BootFlags
import com.tonapps.core.flags.WalletFeatureKey
import com.tonapps.deposit.multicoin.screens.confirm.SwapConfirmTestEnv.Companion.V3_TON_USDT
import com.tonapps.deposit.multicoin.screens.confirm.SwapConfirmTestEnv.Companion.V3_USDT_ETH
import com.tonapps.deposit.multicoin.screens.confirm.base.ConfirmationError
import com.tonapps.deposit.multicoin.screens.confirm.base.PendingTransaction
import com.tonapps.deposit.multicoin.screens.confirm.base.SendingState
import com.tonapps.deposit.multicoin.screens.confirm.base.isRetryable
import com.tonapps.wallet.data.ledger.signing.LedgerTransactionRequest
import com.tonapps.wallet.data.multichain.exchange.PreparedSwapOperation
import com.tonapps.wallet.data.multichain.wallet.CredentialIds
import com.tonapps.wallet.data.multichain.wallet.McWalletEntity
import com.tonapps.wallet.data.multichain.wallet.McWalletType
import com.tonapps.wallet.testing.BaseUnitTest
import com.tonapps.wallet.testing.TestAssets
import com.tonapps.wallet.testing.TestWallet
import io.mockk.coEvery
import io.mockk.coVerify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.time.Duration.Companion.milliseconds

class SwapConfirmLedgerV3Test : BaseUnitTest() {

    private lateinit var env: SwapConfirmTestEnv
    private lateinit var feature: SwapConfirmFeature
    private lateinit var shown: List<PendingTransaction?>

    private val scheduler get() = dispatchers.scheduler

    private val signed = CopyOnWriteArrayList<List<LedgerTransactionRequest>>()

    @Before
    fun setUp() {
        env = SwapConfirmTestEnv(dispatchers.main)
        features.set(WalletFeatureKey.IS_SWAP_V3_ENABLED, true)
        BootFlags.update(BootFlags.values.copy(txValidation = true))
        env.wallet = McWalletEntity(
            credentialId = CredentialIds.hardwareDescriptor(TestWallet.ID),
            name = "Ledger",
            id = TestWallet.ID,
            type = McWalletType.Multicoin,
        )
        coEvery { env.ledgerSigner.signTransactions(any(), any()) } answers {
            val requests = secondArg<List<LedgerTransactionRequest>>()
            signed += requests
            requests.indices.map { byteArrayOf(it.toByte()) }
        }
    }

    @After
    fun tearDown() {
        env.clear()
        BootFlags.reset()
    }

    @Test
    fun aV3RouteIsPreparedOnV3AndSignedOnTheLedger() {
        confirm(tonUsdt())

        assertEquals(listOf(SwapConfirmFixtures.snapshotV3(V3_TON_USDT).routeId), env.prepareRequestsV3.toList())
        assertTrue(env.quoteRequests.isEmpty() && env.prepareRequests.isEmpty())
        val routed = prepared(V3_TON_USDT).operation.payloads.single().meta as PayloadMeta.Ton.Main
        val swap = signed.single().single().transaction as Transaction.Swap
        assertEquals(routed.to.display, swap.to.display)
        assertEquals(routed.value, swap.amount)
        assertEquals(routed.body, swap.data)
        assertEquals(1, env.sent.size)
        assertEquals(SendingState.Done, feature.sendingState.value)
        assertTrue("nothing goes through the operation signer", env.signedPayloads.isEmpty())
    }

    @Test
    fun theApproveIsSignedOnTheLedgerAheadOfTheSwap() {
        env.quoteAnswerV3 = { SwapConfirmFixtures.quoteV3(V3_USDT_ETH) }
        env.prepareAnswerV3 = { SwapConfirmFixtures.prepareV3(V3_USDT_ETH) }

        confirm(usdtEth())

        val (approveMeta, swapMeta) = prepared(V3_USDT_ETH).operation.payloads.map { it.meta as PayloadMeta.Evm.Main }
        val (approve, swap) = signed.single()
        assertTrue(approve.transaction.toString(), approve.transaction !is Transaction.Swap)
        assertEquals(swapMeta.to.display.lowercase(), (swap.transaction as Transaction.Swap).to.display.lowercase())
        assertEquals(swapMeta.data, (swap.transaction as Transaction.Swap).data)
        assertEquals(approve.nonce + BigInteger.ONE, swap.nonce)
        assertTrue(approveMeta.data.orEmpty().removePrefix("0x").startsWith("095ea7b3"))
        assertEquals(2, env.sent.size)
    }

    @Test
    fun theRouteIsHeldToTheSwapBeforeItIsSigned() {
        confirm(tonUsdt())

        val intent = env.validatedIntents.last()
        assertEquals(Intent.Spend(TestAssets.ton.value, SwapConfirmFixtures.TON_SOURCE_AMOUNT), intent.spends?.single())
        assertEquals(Intent.Source.Wallet, intent.source)
    }

    @Test
    fun aRouteTheValidatorFindsDangerousIsNotSigned() {
        env.validationAnswer = { ValidationRes.verdict(listOf(ValidationError.ForeignRecipient("0", "expected", "actual"))) }

        open(tonUsdt())

        assertNotNull(feature.confirmationError.value)
        assertNull(shown.lastOrNull { it != null })
        coVerify(exactly = 0) { env.ledgerSigner.signTransactions(any(), any()) }
    }

    @Test
    fun withoutValidationTheRouteIsSignedAsPrepared() {
        BootFlags.update(BootFlags.values.copy(txValidation = false))
        env.validationAnswer = { ValidationRes.verdict(listOf(ValidationError.ForeignRecipient("0", "expected", "actual"))) }

        confirm(tonUsdt())

        assertTrue(env.validatedIntents.isEmpty())
        assertTrue(env.prepareRequests.isEmpty())
        assertEquals(1, env.prepareRequestsV3.size)
        assertEquals(1, signed.single().size)
    }

    @Test
    fun aRouteThatAgedOutIsQuotedAgainBeforeTheLedgerIsAsked() {
        val aged = SwapConfirmFixtures.snapshotV3(V3_TON_USDT).copy(receivedAt = -ROUTE_AGE_MS, lifetimeMillis = ROUTE_LIFETIME_MS)

        confirm(tonUsdt().withQuote(aged))

        assertEquals(ConfirmationError.Expired, feature.confirmationError.value)
        coVerify(exactly = 0) { env.ledgerSigner.signTransactions(any(), any()) }
    }

    @Test
    fun theLedgerSignsOnlyWhileTheRouteIsUsable() {
        val fresh = SwapConfirmFixtures.snapshotV3(V3_TON_USDT).copy(receivedAt = 0L, lifetimeMillis = ROUTE_LIFETIME_MS)

        confirm(tonUsdt().withQuote(fresh))

        val deadline = requireNotNull(signed.single().single().signableUntil)
        assertTrue(!deadline.hasPassedNow())
        assertTrue(deadline.elapsedNow() > -(ROUTE_LIFETIME_MS + 1).milliseconds)
    }

    @Test
    fun aSendTheNodeRejectedIsPreparedAndSignedAgainOnRetry() {
        var rejections = 1
        env.sendAnswer = {
            if (rejections-- > 0) {
                NodeRes.err(NodeError.Rejected(BLOCKHASH_NOT_FOUND, "Transaction simulation failed"))
            } else {
                NodeRes.ok("hash-${env.sent.size}")
            }
        }

        confirm(tonUsdt())
        assertTrue(feature.confirmationError.value is ConfirmationError.NodeRejected)

        feature.retry()
        scheduler.runCurrent()
        confirmShown()

        assertEquals(2, env.prepareRequestsV3.size)
        assertEquals(2, signed.size)
        assertEquals(SendingState.Done, feature.sendingState.value)
    }

    @Test
    fun aSendWhoseNonceTheNodeSaysIsUsedIsNeitherPreparedNorSignedAgain() {
        env.sendAnswer = { NodeRes.err(NodeError.NonceTaken("nonce too low: next nonce 3, tx nonce 2")) }

        confirm(tonUsdt())

        // It may be this very transaction, sent by a resend the node answered late: the history tells.
        assertEquals(ConfirmationError.NonceTaken, feature.confirmationError.value)
        assertFalse(ConfirmationError.NonceTaken.isRetryable)
        assertNull(feature.retry())
        scheduler.runCurrent()
        assertEquals(1, env.prepareRequestsV3.size)
        assertEquals(1, signed.size)
    }

    @Test
    fun aSendTheNodeMayHaveTakenIsNotSignedAgainWhenItsResendIsRejected() {
        val attempts = CopyOnWriteArrayList<ByteArray>()
        env.sendAnswer = { sentTx ->
            attempts += sentTx.data
            if (attempts.size == 1) {
                NodeRes.err(NodeError.NetworkWrap(IllegalStateException("timeout")))
            } else {
                NodeRes.err(NodeError.Rejected(ALREADY_KNOWN, "already known"))
            }
        }

        confirm(tonUsdt())
        assertEquals(ConfirmationError.NetworkError, feature.confirmationError.value)

        feature.retry()
        scheduler.runCurrent()

        assertEquals(ConfirmationError.OutcomeUnknown, feature.confirmationError.value)
        assertEquals(2, attempts.size)
        assertTrue(attempts[0].contentEquals(attempts[1]))
        assertEquals(1, env.prepareRequestsV3.size)
        assertEquals(1, signed.size)
        assertNull(feature.retry())
        assertEquals(2, attempts.size)
    }

    @Test
    fun aSendTheNodeRefusedKeepsItsErrorWhileTheScreenIsUncoveredAndTicks() {
        env.sendAnswer = { NodeRes.err(NodeError.Rejected(BLOCKHASH_NOT_FOUND, "Transaction simulation failed")) }

        confirm(tonUsdt())
        feature.refreshFees()
        scheduler.advanceTimeBy(QUOTE_TICKS_MS)
        scheduler.runCurrent()

        assertTrue(feature.confirmationError.value is ConfirmationError.NodeRejected)
        assertEquals(1, env.prepareRequestsV3.size)
        assertEquals(1, signed.size)
    }

    @Test
    fun aOneClickSwapTheNodeRefusedEndsWithTheRefusal() {
        env.sendAnswer = { NodeRes.err(NodeError.Rejected(BLOCKHASH_NOT_FOUND, "Transaction simulation failed")) }
        open(tonUsdt())
        val scope = CoroutineScope(dispatchers.main)
        val headless = HeadlessSwapConfirm(feature, scope, review = false, feeId = null)

        headless.start()
        repeat(SETTLE_STEPS) {
            scheduler.advanceTimeBy(SETTLE_STEP_MS)
            scheduler.runCurrent()
        }

        val phase = headless.updates.value.phase
        assertTrue("phase: $phase", phase is HeadlessSwapPhase.Ended && phase.error is ConfirmationError.NodeRejected)
        assertEquals(1, signed.size)
        scope.cancel()
    }

    @Test
    fun anEvmRouteIsSignedAtTheNetworkPriceForTheGasItQuoted() {
        env.quoteAnswerV3 = { SwapConfirmFixtures.quoteV3(V3_USDT_ETH) }
        env.prepareAnswerV3 = { SwapConfirmFixtures.prepareV3(V3_USDT_ETH) }
        val priced = CopyOnWriteArrayList<Payload>()
        env.payloadFeeAnswer = { payload ->
            priced += payload
            ChainRes.ok(networkFee((payload.meta as PayloadMeta.Evm.Main).gas!!))
        }

        confirm(usdtEth())

        val approve = prepared(V3_USDT_ETH).operation.payloads.first()
        val quoted = approve.fee as Fee.Gas
        assertEquals(quoted.limit, (priced.single().meta as PayloadMeta.Evm.Main).gas)
        assertEquals(approve.id, priced.single().id)
        assertEquals(networkFee(quoted.limit), signed.single().first().fee)
    }

    @Test
    fun aSignatureTheLedgerGaveAfterTheRouteLapsedIsNotSent() {
        val closing = SwapConfirmFixtures.snapshotV3(V3_TON_USDT).copy(
            receivedAt = -(ROUTE_LIFETIME_MS - ROUTE_EXPIRY_MARGIN_MS - SIGNING_WINDOW_MS),
            lifetimeMillis = ROUTE_LIFETIME_MS,
        )
        coEvery { env.ledgerSigner.signTransactions(any(), any()) } answers {
            Thread.sleep(SIGNING_WINDOW_MS * 3)
            secondArg<List<LedgerTransactionRequest>>().indices.map { byteArrayOf(it.toByte()) }
        }

        confirm(tonUsdt().withQuote(closing))
        feature.refreshFees()
        scheduler.advanceTimeBy(QUOTE_TICKS_MS)
        scheduler.runCurrent()

        assertEquals(ConfirmationError.Expired, feature.confirmationError.value)
        assertTrue(env.sent.isEmpty())
        assertEquals(1, env.prepareRequestsV3.size)

        feature.retry()
        scheduler.runCurrent()

        assertEquals(2, env.prepareRequestsV3.size)
    }

    private fun networkFee(limit: BigInteger) = Fee.Eip1559(
        asset = TestAssets.eth.value.chain.toAsset(),
        limit = limit,
        networkPrice = NETWORK_PRICE,
        maxPrice = NETWORK_PRICE + NETWORK_PRICE,
        minerPrice = BigInteger.ONE,
        amount = limit * (NETWORK_PRICE + NETWORK_PRICE),
    )

    private fun confirm(request: ConfirmRequest) {
        open(request)
        confirmShown()
    }

    private fun confirmShown() {
        feature.sendTransaction(requireNotNull(shown.lastOrNull { it != null }) {
            "nothing was prepared; error: ${feature.confirmationError.value}"
        })
        repeat(SETTLE_STEPS) {
            scheduler.advanceTimeBy(SETTLE_STEP_MS)
            scheduler.runCurrent()
        }
    }

    private fun ConfirmRequest.withQuote(quote: ConfirmType.Swap.Quote) = ConfirmRequest(
        data = data,
        type = (type as ConfirmType.Swap).copy(quote = quote),
    )

    private fun open(request: ConfirmRequest) {
        feature = env.feature(request)
        shown = env.collectPending(feature)
        scheduler.runCurrent()
    }

    private fun prepared(name: String) = PreparedSwapOperation.from(SwapConfirmFixtures.prepareV3(name))

    private fun tonUsdt() = SwapConfirmFixtures.swapRequest(
        assetId = SwapConfirmFixtures.TON_ASSET_ID,
        destinationAssetId = SwapConfirmFixtures.USDT_TON_ASSET_ID,
        sourceAmount = SwapConfirmFixtures.TON_SOURCE_AMOUNT,
        quote = SwapConfirmFixtures.snapshotV3(V3_TON_USDT),
    )

    private fun usdtEth() = SwapConfirmFixtures.swapRequest(
        assetId = SwapConfirmFixtures.USDT_ETH_ASSET_ID,
        destinationAssetId = SwapConfirmFixtures.ETH_ASSET_ID,
        sourceAmount = SwapConfirmFixtures.USDT_SOURCE_AMOUNT,
        quote = SwapConfirmFixtures.snapshotV3(V3_USDT_ETH),
    )

    private companion object {
        const val SETTLE_STEPS = 20
        const val SETTLE_STEP_MS = 1_000L
        const val ROUTE_AGE_MS = 600_000L
        const val ROUTE_LIFETIME_MS = 60_000L
        const val BLOCKHASH_NOT_FOUND = -32002
        const val ALREADY_KNOWN = -32000
        const val ROUTE_EXPIRY_MARGIN_MS = 5_000L
        const val SIGNING_WINDOW_MS = 100L
        const val QUOTE_TICKS_MS = 120_000L
        val NETWORK_PRICE: BigInteger = BigInteger.parseString("900000000")
    }
}
