package com.tonapps.deposit.multicoin.screens.confirm

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ionspin.kotlin.bignum.integer.BigInteger
import com.tonapps.blockchain.model.CommonTransactionData
import com.tonapps.blockchain.model.ConfirmOperations
import com.tonapps.blockchain.model.ConfirmRequest
import com.tonapps.blockchain.model.ConfirmType
import com.tonapps.chainkit.core.chain.api.models.ChainError
import com.tonapps.chainkit.core.chain.api.models.ChainRes
import com.tonapps.chainkit.core.chain.api.models.NodeError
import com.tonapps.chainkit.core.chain.api.models.NodeRes
import com.tonapps.chainkit.core.chain.api.models.SignError
import com.tonapps.chainkit.core.chain.api.models.SignRes
import com.tonapps.chainkit.core.chain.model.account.Account
import com.tonapps.chainkit.core.chain.model.account.Address
import com.tonapps.chainkit.core.chain.model.account.Chain
import com.tonapps.chainkit.core.chain.model.account.CryptoWallet
import com.tonapps.chainkit.core.chain.model.transaction.Fee
import com.tonapps.chainkit.core.op.Operation
import com.tonapps.chainkit.core.op.Payload
import com.tonapps.chainkit.core.op.PayloadMeta
import com.tonapps.chainkit.core.op.SignedPayload
import com.tonapps.deposit.multicoin.screens.confirm.base.BaseConfirmFeature
import com.tonapps.deposit.multicoin.screens.confirm.base.operation.BaseOperationConfirmFeature
import com.tonapps.deposit.multicoin.screens.confirm.base.operation.OperationConfirmAction
import com.tonapps.deposit.multicoin.screens.confirm.base.ConfirmEvent
import com.tonapps.deposit.multicoin.screens.confirm.base.ConfirmationError
import com.tonapps.deposit.multicoin.screens.confirm.base.SUCCESS_HOLD_MS
import com.tonapps.deposit.multicoin.screens.confirm.base.SendingState
import com.tonapps.deposit.multicoin.screens.confirm.perpetual.PerpetualDepositConfirmFeature
import com.tonapps.wallet.ChainKitProvider
import com.tonapps.wallet.data.multichain.account.McAccountRepository
import com.tonapps.wallet.data.multichain.tx.PostTransactionRefreshSchedule
import com.tonapps.wallet.data.passcode.PasscodeManager
import com.tonapps.wallet.testing.BaseUnitTest
import com.tonapps.wallet.testing.TestAssets
import com.tonapps.wallet.testing.TestWallet
import com.tonapps.wallet.testing.testAccount
import com.tonapps.wallet.testing.testWallet
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * A request that carries its operation, confirmed end to end: every payload priced on open, signed
 * in order on swipe and handed to the sender. The chain is a mocked [ChainKitProvider]; time is the
 * rule's virtual clock, driven by hand.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OperationConfirmFeatureTest : BaseUnitTest() {

    private val cryptoWallet = mockk<CryptoWallet>()
    private val accountRepo = mockk<McAccountRepository>()
    private val passcodeManager = mockk<PasscodeManager>()
    private val provider = mockk<ChainKitProvider>()
    private val refreshSchedule = mockk<PostTransactionRefreshSchedule>(relaxed = true)
    private val chain = FakeChain()

    private val accounts = mutableMapOf(
        TestAssets.usdtEth.id to testAccount(TestAssets.usdtEth, balance = "100000000"),
        TestAssets.eth.id to testAccount(TestAssets.eth, balance = ONE_ETH, price = "2000"),
    )
    private val pricedWith = mutableListOf<Pair<String, Account>>()
    private val signedWith = mutableListOf<Pair<String, BigInteger>>()

    private val scheduler get() = dispatchers.scheduler

    @Before
    fun setUp() {
        coEvery { provider.reportPayloadSent(any(), any(), any(), any(), any()) } returns Unit
        coEvery { provider.broadcastPayload(any(), any()) } coAnswers {
            chain.send(secondArg(), (firstArg<SignedPayload>().data as JsonPrimitive).content)
        }
        coEvery { accountRepo.getWallet(TestWallet.ID) } returns testWallet()
        coEvery { accountRepo.findAccount(TestWallet.ID, any(), any(), any()) } answers { accounts[secondArg<String>()] }
        coEvery { passcodeManager.unlockMultichainVault<CryptoWallet>(any(), any()) } returns cryptoWallet
        coEvery { provider.estimateFee(any<Payload>(), any(), any()) } answers {
            pricedWith += firstArg<Payload>().id to secondArg<Account>()
            ChainRes.ok(PAYLOAD_FEE)
        }
        coEvery { provider.getNonce(any()) } returns NodeRes.ok(NONCE)
        coEvery { provider.signPayload(any(), any(), any(), any(), any()) } answers {
            val payload = firstArg<Payload>()
            signedWith += payload.id to arg<BigInteger>(3)
            SignRes.ok(SignedPayload.signed(payload, "0xsigned${payload.id}"))
        }
    }

    @Test
    fun pricesEveryPayloadOnOpenAndShowsTheTotal() {
        val feature = open()
        assertNull(feature.fee.value)

        scheduler.runCurrent()

        assertEquals(listOf("0", "1"), pricedWith.map { it.first })
        // Priced for the sender's own account on the chain, whatever asset the request names.
        pricedWith.forEach { (_, account) ->
            assertEquals(TestWallet.ETH_ADDRESS, account.address.display)
            assertEquals(TestAssets.eth.id, account.asset.id)
        }
        val fee = feature.fee.value
        assertEquals(TestAssets.eth.id, fee?.asset?.id)
        assertEquals(PAYLOAD_FEE.amount + PAYLOAD_FEE.amount, fee?.amount)
        // The rate of the coin that pays it comes along, so the row can show what the fee is worth.
        assertEquals(BigDecimal.parseString("2000"), feature.feeRate.value?.value)
        assertEquals(TestWallet.ID, feature.wallet.value?.id)
        assertEquals(TestAssets.usdtEth.id, feature.account.value?.asset?.id)
        assertNull(feature.confirmationError.value)
    }

    @Test
    fun confirmSignsThePayloadsInOrderAndSendsTheOperation() {
        val operation = operation()
        val feature = open(operation)
        val events = collectEvents(feature)
        scheduler.runCurrent()

        feature.sendAction(OperationConfirmAction.Confirm(context))
        assertEquals(SendingState.Loading, feature.sendingState.value)
        scheduler.runCurrent()

        // Each transaction takes the next nonce.
        assertEquals(listOf("0" to NONCE, "1" to NONCE + BigInteger.ONE), signedWith)
        // The account that signed is the one the payloads are broadcast from, in the operation's order.
        assertTrue(chain.broadcast.all { it.account.address.display == TestWallet.ETH_ADDRESS })
        assertEquals(listOf("0xsigned0", "0xsigned1"), chain.broadcast.map { it.transaction })
        verify(exactly = 1) { refreshSchedule.notifyTransactionSent(TestWallet.ID, Chain.Ethereum.Mainnet) }
        assertEquals(SendingState.Done, feature.sendingState.value)
        assertTrue(events.isEmpty())

        // The done state holds for a beat before the screen is told to leave.
        scheduler.advanceTimeBy(SUCCESS_HOLD_MS)
        scheduler.runCurrent()
        assertEquals(listOf<ConfirmEvent>(ConfirmEvent.Done), events)
    }

    @Test
    fun aDocumentARelayerSubmitsTakesNoNonce() {
        val authorization = Payload(
            id = "authorization",
            type = Payload.Type.Eip7702Authorization,
            chain = Chain.Ethereum.Mainnet,
            version = Payload.VERSION,
            fee = null,
            meta = PayloadMeta.Evm.Authorization(chainId = 1L, address = address(HERMES), nonce = NONCE),
        )
        val feature = open(ConfirmOperations.build(listOf(authorization, call(index = 1))))
        scheduler.runCurrent()

        feature.sendAction(OperationConfirmAction.Confirm(context))
        scheduler.runCurrent()

        assertEquals(listOf("authorization" to NONCE, "1" to NONCE), signedWith)
    }

    @Test
    fun confirmIsIgnoredUntilTheOperationIsPriced() {
        val feature = open()

        feature.sendAction(OperationConfirmAction.Confirm(context))
        scheduler.runCurrent()

        assertTrue(chain.broadcast.isEmpty())
        assertEquals(SendingState.None, feature.sendingState.value)
    }

    @Test
    fun aFeeThatCannotBePricedShowsTheErrorAndRetryPricesAgain() {
        coEvery { provider.estimateFee(any<Payload>(), any(), any()) } returns ChainRes.err(ChainError.BadResponse())
        val feature = open()
        scheduler.runCurrent()

        assertEquals(ConfirmationError.BadResponse, feature.confirmationError.value)
        assertNull(feature.fee.value)

        coEvery { provider.estimateFee(any<Payload>(), any(), any()) } returns ChainRes.ok(PAYLOAD_FEE)
        feature.sendAction(OperationConfirmAction.Retry(context))
        scheduler.runCurrent()

        assertNull(feature.confirmationError.value)
        assertNotNull(feature.fee.value)
        assertTrue(chain.broadcast.isEmpty())
    }

    @Test
    fun aFeeTheCoinCannotCoverBlocksTheOperation() {
        accounts[TestAssets.eth.id] = testAccount(TestAssets.eth, balance = "500000000000000", price = "2000")
        val feature = open()
        scheduler.runCurrent()

        assertEquals(ConfirmationError.InsufficientBalance, feature.confirmationError.value)
        assertNull(feature.fee.value)
    }

    @Test
    fun theCoinThePayloadsSpendCountsAgainstTheBalance() {
        val feature = open(ConfirmOperations.build(listOf(call(index = 0, value = ONE_ETH))))
        scheduler.runCurrent()

        assertEquals(ConfirmationError.InsufficientBalance, feature.confirmationError.value)
    }

    @Test
    fun aPayloadOnAnotherChainIsRefused() {
        val foreign = ConfirmOperations.payload(0, Chain.Smartchain.Mainnet, PayloadMeta.Evm.Main(to = address(GATEWAY), value = BigInteger.ZERO))
        val feature = open(ConfirmOperations.build(listOf(foreign)))
        scheduler.runCurrent()

        assertEquals(ConfirmationError.UnsupportedTransaction, feature.confirmationError.value)
        assertTrue(pricedWith.isEmpty())
    }

    @Test
    fun anOperationIsSignedBeforeItsDeadline() {
        val feature = open(expiresAt = System.currentTimeMillis() + 60_000L)
        scheduler.runCurrent()

        feature.sendAction(OperationConfirmAction.Confirm(context))
        scheduler.runCurrent()

        assertEquals(listOf("0", "1"), signedWith.map { it.first })
    }

    @Test
    fun anOperationPastItsDeadlineIsNeverSigned() {
        val feature = open(expiresAt = System.currentTimeMillis() - 1L)
        scheduler.runCurrent()

        // TK-4608: the screen does not count the deadline down; only the swipe finds it passed.
        assertNull(feature.confirmationError.value)

        feature.sendAction(OperationConfirmAction.Confirm(context))
        scheduler.runCurrent()

        assertEquals(ConfirmationError.Expired, feature.confirmationError.value)

        // Preparing again does not move the deadline: the next swipe is refused the same way.
        feature.sendAction(OperationConfirmAction.Retry(context))
        scheduler.runCurrent()
        feature.sendAction(OperationConfirmAction.Confirm(context))
        scheduler.runCurrent()

        assertTrue(signedWith.isEmpty())
        assertTrue(chain.broadcast.isEmpty())
        assertEquals(ConfirmationError.Expired, feature.confirmationError.value)
    }

    @Test
    fun aFailedSendKeepsTheSignatureAndRetrySendsItAgain() {
        chain.error = IllegalStateException("backend is down")
        val feature = open()
        scheduler.runCurrent()

        feature.sendAction(OperationConfirmAction.Confirm(context))
        scheduler.runCurrent()

        assertEquals(SendingState.None, feature.sendingState.value)
        assertEquals(ConfirmationError.Unknown, feature.confirmationError.value)

        chain.error = null
        feature.sendAction(OperationConfirmAction.Retry(context))
        scheduler.runCurrent()

        // Signed once: the retry carries the very same bytes.
        assertEquals(2, signedWith.size)
        assertEquals(listOf("0xsigned0", "0xsigned1"), chain.broadcast.map { it.transaction })
        coVerify(exactly = 1) { passcodeManager.unlockMultichainVault<CryptoWallet>(any(), any()) }
        assertEquals(SendingState.Done, feature.sendingState.value)
        assertNull(feature.confirmationError.value)
    }

    @Test
    fun aSendTheNodeRefusesShowsTheNodesReason() {
        chain.error = NodeError.InsufficientFunds("insufficient funds for gas * price + value")
        val feature = open()
        scheduler.runCurrent()

        feature.sendAction(OperationConfirmAction.Confirm(context))
        scheduler.runCurrent()

        assertEquals(ConfirmationError.InsufficientBalance, feature.confirmationError.value)
        assertEquals(SendingState.None, feature.sendingState.value)
    }

    @Test
    fun aPayloadThatCannotBeSignedSendsNothing() {
        coEvery { provider.signPayload(any(), any(), any(), any(), any()) } returns
            SignRes.err(SignError.InternalError("quote again"))
        val feature = open()
        scheduler.runCurrent()

        feature.sendAction(OperationConfirmAction.Confirm(context))
        scheduler.runCurrent()

        assertEquals(ConfirmationError.SignInternalError, feature.confirmationError.value)
        assertEquals(SendingState.None, feature.sendingState.value)
        assertTrue(chain.broadcast.isEmpty())
    }

    @Test
    fun aRetryWithNothingSignedPricesAgainAndLeavesTheSwipeReady() {
        coEvery { provider.signPayload(any(), any(), any(), any(), any()) } returns
            SignRes.err(SignError.InternalError("quote again"))
        val feature = open()
        scheduler.runCurrent()
        feature.sendAction(OperationConfirmAction.Confirm(context))
        scheduler.runCurrent()
        pricedWith.clear()

        feature.sendAction(OperationConfirmAction.Retry(context))
        assertNull(feature.fee.value)
        scheduler.runCurrent()

        assertEquals(listOf("0", "1"), pricedWith.map { it.first })
        assertNotNull(feature.fee.value)
        // The failed signature belonged to the price that is gone.
        assertNull(feature.confirmationError.value)
        assertEquals(SendingState.None, feature.sendingState.value)
        coVerify(exactly = 1) { passcodeManager.unlockMultichainVault<CryptoWallet>(any(), any()) }
    }

    @Test
    fun aSwipeAfterTheSendIsDoneSendsNothingMore() {
        val feature = open()
        scheduler.runCurrent()
        feature.sendAction(OperationConfirmAction.Confirm(context))
        scheduler.advanceTimeBy(SUCCESS_HOLD_MS)
        scheduler.runCurrent()

        feature.sendAction(OperationConfirmAction.Confirm(context))
        feature.sendAction(OperationConfirmAction.Retry(context))
        scheduler.runCurrent()

        assertEquals(2, chain.broadcast.size)
        assertEquals(2, signedWith.size)
        assertEquals(SendingState.Done, feature.sendingState.value)
    }

    @Test
    fun aSecondSwipeWhileSendingIsIgnored() {
        chain.delayMs = 1_000L
        val feature = open()
        scheduler.runCurrent()

        feature.sendAction(OperationConfirmAction.Confirm(context))
        feature.sendAction(OperationConfirmAction.Confirm(context))
        // Each of the two payloads takes the delay.
        scheduler.advanceTimeBy(2_000L)
        scheduler.runCurrent()

        assertEquals(2, chain.broadcast.size)
    }

    @Test
    fun aCancelledPasscodeLeavesTheSwipeReady() {
        coEvery { passcodeManager.unlockMultichainVault<CryptoWallet>(any(), any()) } throws CancellationException()
        val feature = open()
        scheduler.runCurrent()

        feature.sendAction(OperationConfirmAction.Confirm(context))
        scheduler.runCurrent()

        assertEquals(SendingState.None, feature.sendingState.value)
        assertNull(feature.confirmationError.value)
        assertTrue(chain.broadcast.isEmpty())
    }

    @Test
    fun aRequestWithoutAnOperationIsRefused() {
        val request = ConfirmRequest(
            data = CommonTransactionData(assetId = TestAssets.usdtEth.id, walletId = TestWallet.ID),
            type = ConfirmType.PerpetualWithdraw(amount = BigInteger.TEN),
        )

        assertThrows(IllegalArgumentException::class.java) {
            TestFeature(request, provider, accountRepo, passcodeManager, refreshSchedule)
        }
    }

    @Test
    fun aPerpsDepositOpensWhateverItsQuoteDeadline() {
        val deposit = ConfirmType.PerpetualDeposit(
            operation = operation(),
            amount = BigInteger.fromInt(5_000_000),
            quote = depositQuote(),
        )
        val request = ConfirmRequest(
            data = CommonTransactionData(
                assetId = TestAssets.usdtEth.id,
                walletId = TestWallet.ID,
                expiresAt = System.currentTimeMillis() - 1L,
            ),
            type = deposit,
        )

        val feature = PerpetualDepositConfirmFeature(request, provider, accountRepo, passcodeManager, refreshSchedule, tracker = null, authorizer = mockk())
        scheduler.runCurrent()

        assertSame(deposit, feature.deposit)
        // TK-4608: the screen does not count the deadline down; only the swipe checks it.
        assertNull(feature.confirmationError.value)
    }

    @Test
    fun aPerpsDepositIsBroadcastThroughChainkit() {
        val request = ConfirmRequest(
            data = CommonTransactionData(
                assetId = TestAssets.usdtEth.id,
                walletId = TestWallet.ID,
                expiresAt = System.currentTimeMillis() + 60_000L,
            ),
            type = ConfirmType.PerpetualDeposit(
                operation = operation(),
                amount = BigInteger.fromInt(5_000_000),
                quote = depositQuote(),
            ),
        )
        val feature = PerpetualDepositConfirmFeature(request, provider, accountRepo, passcodeManager, refreshSchedule, tracker = null, authorizer = mockk())
        scheduler.runCurrent()

        feature.sendAction(OperationConfirmAction.Confirm(context))
        scheduler.runCurrent()

        assertEquals(listOf("0xsigned0", "0xsigned1"), chain.broadcast.map { it.transaction })
        assertEquals(SendingState.Done, feature.sendingState.value)
    }

    private fun open(operation: Operation = operation(), expiresAt: Long? = null): TestFeature {
        val request = ConfirmRequest(
            data = CommonTransactionData(assetId = TestAssets.usdtEth.id, walletId = TestWallet.ID, expiresAt = expiresAt),
            type = ConfirmType.Call(operation),
        )
        return TestFeature(request, provider, accountRepo, passcodeManager, refreshSchedule)
    }

    private fun depositQuote() = ConfirmType.PerpetualDeposit.Quote(
        id = "quote-1",
        executionId = null,
        beneficiary = GATEWAY,
        depositAmount = BigInteger.fromInt(5_000_000),
        crossChain = false,
    )

    private fun operation() = ConfirmOperations.build(listOf(call(index = 0), call(index = 1)))

    private fun call(index: Int, value: String = "0") = ConfirmOperations.payload(
        index = index,
        chain = Chain.Ethereum.Mainnet,
        meta = PayloadMeta.Evm.Main(to = address(GATEWAY), value = BigInteger.parseString(value), data = "0x"),
    )

    private fun address(value: String) = Address.force(value, Chain.Ethereum.Mainnet)

    private fun collectEvents(feature: BaseConfirmFeature<*, *>): List<ConfirmEvent> {
        val events = mutableListOf<ConfirmEvent>()
        CoroutineScope(dispatchers.main).launch { feature.events.collect { events += it } }
        return events
    }

    private class TestFeature(
        request: ConfirmRequest,
        provider: ChainKitProvider,
        accountRepo: McAccountRepository,
        passcodeManager: PasscodeManager,
        refreshSchedule: PostTransactionRefreshSchedule,
    ) : BaseOperationConfirmFeature(
        request = request,
        provider = provider,
        accountRepo = accountRepo,
        passcodeManager = passcodeManager,
        postTransactionRefreshSchedule = refreshSchedule,
    )

    private class Broadcast(val account: Account, val transaction: String)

    // The chain the default sender broadcasts to: it takes every signed payload, or answers [error].
    private class FakeChain {

        val broadcast = mutableListOf<Broadcast>()
        var error: Throwable? = null
        var delayMs = 0L

        suspend fun send(account: Account, transaction: String): NodeRes<String> {
            if (delayMs > 0) {
                delay(delayMs)
            }
            when (val failure = error) {
                null -> Unit
                is NodeError -> return NodeRes.err(failure)
                else -> throw failure
            }
            broadcast += Broadcast(account, transaction)
            return NodeRes.ok("0xhash${broadcast.size}")
        }
    }

    private companion object {
        const val GATEWAY = "0x3B4D794a66304F130a4Db8F2551B0070dfCf5ca7"
        const val HERMES = "0xd1c44e466b70002ac54fb727ed7cbe8f92c782f1"
        const val ONE_ETH = "1000000000000000000"
        val NONCE: BigInteger = BigInteger.fromInt(7)
        val PAYLOAD_FEE = Fee.Value(Chain.Ethereum.Mainnet.toAsset(), BigInteger.parseString("300000000000000"))
    }
}
