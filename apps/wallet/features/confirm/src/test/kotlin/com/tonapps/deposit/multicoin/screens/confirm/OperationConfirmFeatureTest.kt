package com.tonapps.deposit.multicoin.screens.confirm

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ionspin.kotlin.bignum.integer.BigInteger
import com.tonapps.blockchain.model.CommonTransactionData
import com.tonapps.blockchain.model.ConfirmOperations
import com.tonapps.blockchain.model.ConfirmRequest
import com.tonapps.blockchain.model.ConfirmType
import com.tonapps.bus.core.AnalyticsHelper
import com.tonapps.bus.core.contract.EventExecutor
import com.tonapps.bus.generated.DefaultEvents
import com.tonapps.chainkit.core.chain.api.models.ChainError
import com.tonapps.chainkit.core.chain.api.models.ChainRes
import com.tonapps.chainkit.core.chain.api.models.NodeError
import com.tonapps.chainkit.core.chain.api.models.NodeRes
import com.tonapps.chainkit.core.chain.api.models.SignError
import com.tonapps.chainkit.core.chain.api.models.SignRes
import com.tonapps.chainkit.core.chain.model.account.Account
import com.tonapps.chainkit.core.chain.model.account.Address
import com.tonapps.chainkit.core.chain.model.account.Asset
import com.tonapps.chainkit.core.chain.model.account.Chain
import com.tonapps.chainkit.core.chain.model.account.MnemonicWallet
import com.tonapps.chainkit.core.chain.model.transaction.Fee
import com.tonapps.chainkit.core.op.Intent
import com.tonapps.chainkit.core.op.Operation
import com.tonapps.chainkit.core.op.Payload
import com.tonapps.chainkit.core.op.PayloadMeta
import com.tonapps.chainkit.core.op.SignedPayload
import com.tonapps.chainkit.core.op.ValidationError
import com.tonapps.chainkit.core.op.ValidationRes
import com.tonapps.core.flags.BootFlags
import com.tonapps.deposit.multicoin.screens.confirm.base.BaseConfirmFeature
import com.tonapps.deposit.multicoin.screens.confirm.base.ConfirmEvent
import com.tonapps.deposit.multicoin.screens.confirm.base.ConfirmationError
import com.tonapps.deposit.multicoin.screens.confirm.base.ConfirmationErrorCause
import com.tonapps.deposit.multicoin.screens.confirm.base.SUCCESS_HOLD_MS
import com.tonapps.deposit.multicoin.screens.confirm.base.SendingState
import com.tonapps.deposit.multicoin.screens.confirm.base.SignatureGate
import com.tonapps.deposit.multicoin.screens.confirm.base.operation.BaseOperationConfirmFeature
import com.tonapps.deposit.multicoin.screens.confirm.base.operation.FindingSeverity
import com.tonapps.deposit.multicoin.screens.confirm.base.operation.OperationConfirmAction
import com.tonapps.deposit.multicoin.screens.confirm.base.operation.OperationFeeMethods
import com.tonapps.deposit.multicoin.screens.confirm.base.operation.OperationTracker
import com.tonapps.deposit.multicoin.screens.confirm.base.operation.QuoteChangedException
import com.tonapps.deposit.multicoin.screens.confirm.fee.FeeAccount
import com.tonapps.deposit.multicoin.screens.confirm.fee.RelayerOutcomeUnknownException
import com.tonapps.deposit.multicoin.screens.confirm.fee.RelayerQuote
import com.tonapps.deposit.multicoin.screens.confirm.fee.RelayerRejectedException
import com.tonapps.deposit.multicoin.screens.confirm.fee.RelayerSend
import com.tonapps.deposit.multicoin.screens.confirm.fee.TxFee
import com.tonapps.deposit.multicoin.screens.confirm.fee.TxFeeState
import com.tonapps.deposit.multicoin.screens.confirm.perpetual.PerpetualDepositAuthorizer
import com.tonapps.deposit.multicoin.screens.confirm.perpetual.PerpetualDepositConfirmFeature
import com.tonapps.deposit.multicoin.screens.confirm.perpetual.PerpetualDepositFeeMethods
import com.tonapps.deposit.multicoin.screens.confirm.perpetual.PerpetualDepositTracker
import com.tonapps.log.L
import com.tonapps.time.TimeProvider
import com.tonapps.wallet.ChainKitProvider
import com.tonapps.wallet.data.multichain.account.AccountWithDetails
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
import io.mockk.slot
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.math.BigDecimal as JavaBigDecimal
import java.util.concurrent.CopyOnWriteArrayList

/**
 * A request that carries its operation, confirmed end to end: every payload priced on open, signed
 * in order on swipe and handed to the sender. The chain is a mocked [ChainKitProvider]; time is the
 * rule's virtual clock, driven by hand.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OperationConfirmFeatureTest : BaseUnitTest() {

    private val cryptoWallet = mockk<MnemonicWallet>()
    private val accountRepo = mockk<McAccountRepository>()
    private val walletUnlocker = mockk<WalletUnlocker>()
    private val provider = mockk<ChainKitProvider>()
    private val refreshSchedule = mockk<PostTransactionRefreshSchedule>(relaxed = true)
    private val serverTime = FakeTimeProvider()
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
        coEvery { walletUnlocker.unlockWallet(any(), any()) } returns cryptoWallet
        coEvery { provider.estimateFee(any<Payload>(), any(), any()) } answers {
            pricedWith += firstArg<Payload>().id to secondArg<Account>()
            ChainRes.ok(PAYLOAD_FEE)
        }
        coEvery { provider.getNonce(any()) } returns NodeRes.ok(NONCE)
        coEvery { provider.validateOperation(any(), any(), any()) } returns ValidationRes.ok(Unit)
        BootFlags.update(BootFlags.values.copy(txValidation = true))
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
    fun showsEveryFindingTheValidatorMakesMostSevereFirst() {
        val blind = ValidationError.BlindCall("1", GATEWAY)
        val overspend = ValidationError.Overspend("0", TestAssets.eth.id, BigInteger.ONE, BigInteger.TEN, BigInteger.ZERO)
        coEvery { provider.validateOperation(any(), any(), any()) } returns ValidationRes.verdict(listOf(blind, overspend))
        val feature = open()

        scheduler.runCurrent()

        assertEquals(listOf<ValidationError>(overspend, blind), feature.findings.value.map { it.error })
    }

    @Test
    fun aWarningInAFlowOfTheWalletsOwnAsksNothing() {
        val unknown = ValidationError.DurableNonce("0")
        val overspend = ValidationError.Overspend("1", TestAssets.usdtEth.id, BigInteger.ONE, BigInteger.TEN, BigInteger.ZERO)
        coEvery { provider.validateOperation(any(), any(), any()) } returns ValidationRes.verdict(listOf(unknown, overspend))
        val feature = depositFeature(depositRequest(operation(), preview = false), authorizer = mockk())

        scheduler.runCurrent()

        val severities = feature.findings.value.associate { it.error to it.severity }
        assertEquals(FindingSeverity.Info, severities[unknown])
        assertEquals(FindingSeverity.Danger, severities[overspend])
    }

    @Test
    fun aDepositIsValidatedAgainstWhatTheUserEnteredNotTheCoinTheOperationCarries() {
        val agreed = slot<Intent>()
        coEvery { provider.validateOperation(any(), any(), capture(agreed)) } returns ValidationRes.ok(Unit)
        val carriesEth = ConfirmOperations.build(listOf(call(index = 0, value = "1000000000000000")))
        depositFeature(depositRequest(carriesEth, preview = false), authorizer = mockk())

        scheduler.runCurrent()

        val spend = agreed.captured.listed.single()
        assertEquals(TestAssets.usdtEth.id, spend.asset.id)
        assertEquals(BigInteger.fromInt(5_000_000), spend.amount)
        assertEquals(Intent.Source.Wallet, agreed.captured.source)
    }

    @Test
    fun aWarningInADappsRequestStaysAWarning() {
        val unknown = ValidationError.DurableNonce("0")
        coEvery { provider.validateOperation(any(), any(), any()) } returns ValidationRes.verdict(listOf(unknown))
        val feature = open()

        scheduler.runCurrent()

        assertEquals(FindingSeverity.Warning, feature.findings.value.single().severity)
    }

    @Test
    fun aValidatorThatThrewIsLoggedThoughTheUserIsAskedNothing() {
        val threw = ValidationError.Unknown(IllegalStateException("parser broke"))
        coEvery { provider.validateOperation(any(), any(), any()) } returns ValidationRes.verdict(listOf(threw))
        mockkObject(L)
        try {
            val feature = depositFeature(depositRequest(operation(), preview = false), authorizer = mockk())

            scheduler.runCurrent()

            verify { L.e(threw) }
            assertEquals(FindingSeverity.Info, feature.findings.value.single().severity)
        } finally {
            unmockkObject(L)
        }
    }

    @Test
    fun withValidationOffTheValidatorIsNotAsked() {
        BootFlags.update(BootFlags.values.copy(txValidation = false))
        val overspend = ValidationError.Overspend("0", TestAssets.eth.id, BigInteger.ONE, BigInteger.TEN, BigInteger.ZERO)
        coEvery { provider.validateOperation(any(), any(), any()) } returns ValidationRes.verdict(listOf(overspend))
        val feature = open()

        scheduler.runCurrent()

        coVerify(exactly = 0) { provider.validateOperation(any(), any(), any()) }
        assertTrue(feature.findings.value.isEmpty())
    }

    @Test
    fun aPreviewIsNotValidated() {
        val feature = depositFeature(depositRequest(operation(), preview = true), authorizer = mockk())

        scheduler.runCurrent()

        coVerify(exactly = 0) { provider.validateOperation(any(), any(), any()) }
        assertTrue(feature.findings.value.isEmpty())
    }

    @Test
    fun confirmSignsThePayloadsInOrderAndSendsTheOperation() {
        val operation = operation()
        val feature = open(operation)
        val events = collectEvents(feature)
        scheduler.runCurrent()

        feature.sendAction(OperationConfirmAction.Confirm)
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

        feature.sendAction(OperationConfirmAction.Confirm)
        scheduler.runCurrent()

        assertEquals(listOf("authorization" to NONCE, "1" to NONCE), signedWith)
    }

    @Test
    fun confirmIsIgnoredUntilTheOperationIsPriced() {
        val feature = open()

        feature.sendAction(OperationConfirmAction.Confirm)
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
        feature.sendAction(OperationConfirmAction.Retry)
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

        assertEquals(ConfirmationError.InsufficientNetworkFee(ETH_SYMBOL, JavaBigDecimal("0.0006")), feature.confirmationError.value)
        assertNull(feature.fee.value)
    }

    @Test
    fun theCoinThePayloadsSpendCountsAgainstTheBalance() {
        val feature = open(ConfirmOperations.build(listOf(call(index = 0, value = ONE_ETH))))
        scheduler.runCurrent()

        assertEquals(ConfirmationError.InsufficientNetworkFee(ETH_SYMBOL, JavaBigDecimal("1.0003")), feature.confirmationError.value)
        assertNull(feature.fee.value)
    }

    @Test
    fun aCoinSourceDepositTheNodeRefusesForFundsNamesTheCoin() {
        assertEquals(Chain.Ethereum.Mainnet.toAsset().id, TestAssets.eth.id)
        chain.error = NodeError.InsufficientFunds("insufficient funds for gas * price + value")
        val feature = depositFeature(depositRequest(operation(), preview = false, assetId = TestAssets.eth.id), mockk())
        scheduler.runCurrent()
        assertNull(feature.confirmationError.value)

        feature.sendAction(OperationConfirmAction.Confirm)
        scheduler.runCurrent()

        assertEquals(ConfirmationError.InsufficientNetworkFee(ETH_SYMBOL), feature.confirmationError.value)
        assertEquals(SendingState.None, feature.sendingState.value)
        assertTrue(chain.broadcast.isEmpty())
    }

    @Test
    fun aSendTheNodeRefusesForFundsEndsTheRedSendAtValidation() {
        val events = recordAnalytics()
        try {
            chain.error = NodeError.InsufficientFunds("insufficient funds for gas * price + value")
            val feature = depositFeature(depositRequest(operation(), preview = false, assetId = TestAssets.eth.id), mockk())
            scheduler.runCurrent()

            feature.sendAction(OperationConfirmAction.Confirm)
            scheduler.runCurrent()

            assertEquals(ConfirmationError.InsufficientNetworkFee(ETH_SYMBOL), feature.confirmationError.value)
            val terminal = events.single { (event, props) -> event == "op_terminal" && props["operation"] == "send" }.second
            assertEquals("fail", terminal["outcome"])
            assertEquals("validation", terminal["stage"])
            assertEquals("insufficient_network_fee", terminal["error_type"])
            assertEquals("insufficient funds for gas * price + value", terminal["error_message"])
            // The terminal repeats the attempt's send metadata.
            val attempt = events.single { (event, props) -> event == "op_attempt" && props["operation"] == "send" }.second
            assertEquals(attempt["other_metadata"], terminal["other_metadata"])
            JSONObject(terminal["other_metadata"] as String).getString("fee_paid_in")
        } finally {
            unmockkObject(AnalyticsHelper.Companion)
        }
    }

    @Test
    fun aCoinSourceDepositTheNodeRefusesToPriceForFundsNamesTheCoin() {
        coEvery { provider.estimateFee(any<Payload>(), any(), any()) } returns ChainRes.err(ChainError.InsufficientFunds("no gas"))
        val feature = depositFeature(depositRequest(operation(), preview = false, assetId = TestAssets.eth.id), mockk())
        scheduler.runCurrent()

        assertEquals(ConfirmationError.InsufficientNetworkFee(ETH_SYMBOL), feature.confirmationError.value)
        assertNull(feature.fee.value)
    }

    @Test
    fun aTokenDepositTheNodeRefusesToPriceForFundsKeepsInsufficientBalance() {
        coEvery { provider.estimateFee(any<Payload>(), any(), any()) } returns ChainRes.err(ChainError.InsufficientFunds("no gas"))
        val feature = depositFeature(depositRequest(operation(), preview = false), mockk())
        scheduler.runCurrent()

        assertEquals(ConfirmationError.InsufficientBalance, feature.confirmationError.value)
    }

    @Test
    fun anAuthorizedPreviewTheCoinCannotPayForIsNeverSigned() {
        accounts[TestAssets.eth.id] = testAccount(TestAssets.eth, balance = "600000000000000", price = "2000")
        val authorized = depositRequest(ConfirmOperations.build(listOf(call(index = 0), call(index = 1), call(index = 2))), preview = false)
        val authorizer = mockk<PerpetualDepositAuthorizer>()
        coEvery { authorizer.authorize(any(), any()) } returns authorized
        val feature = depositFeature(depositRequest(operation(), preview = true), authorizer)
        scheduler.runCurrent()
        assertNull(feature.confirmationError.value)

        feature.sendAction(OperationConfirmAction.Confirm)
        scheduler.runCurrent()

        assertEquals(ConfirmationError.InsufficientNetworkFee(ETH_SYMBOL, JavaBigDecimal("0.0009")), feature.confirmationError.value)
        assertTrue(signedWith.isEmpty())
        assertTrue(chain.broadcast.isEmpty())
        coVerify(exactly = 1) { authorizer.authorize(any(), any()) }
        assertEquals(SendingState.None, feature.sendingState.value)
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
        val feature = open(expiresAt = serverTime.currentTimeMillis() + 60_000L)
        scheduler.runCurrent()

        feature.sendAction(OperationConfirmAction.Confirm)
        scheduler.runCurrent()

        assertEquals(listOf("0", "1"), signedWith.map { it.first })
    }

    @Test
    fun anOperationPastItsDeadlineIsNeverSigned() {
        val feature = open(expiresAt = serverTime.currentTimeMillis() - 1L)
        scheduler.runCurrent()

        // TK-4608: the screen does not count the deadline down; only the swipe finds it passed.
        assertNull(feature.confirmationError.value)

        feature.sendAction(OperationConfirmAction.Confirm)
        scheduler.runCurrent()

        assertEquals(ConfirmationError.Expired, feature.confirmationError.value)

        // Preparing again does not move the deadline: the next swipe is refused the same way.
        feature.sendAction(OperationConfirmAction.Retry)
        scheduler.runCurrent()
        feature.sendAction(OperationConfirmAction.Confirm)
        scheduler.runCurrent()

        assertTrue(signedWith.isEmpty())
        assertTrue(chain.broadcast.isEmpty())
        assertEquals(ConfirmationError.Expired, feature.confirmationError.value)
    }

    @Test
    fun aDeadlineStillAheadOnTheServerIsSignedWhileTheDeviceClockRunsAhead() {
        serverTime.offsetMs = -DEVICE_CLOCK_SKEW_MS
        val feature = open(expiresAt = serverTime.currentTimeMillis() + 60_000L)
        scheduler.runCurrent()

        feature.sendAction(OperationConfirmAction.Confirm)
        scheduler.runCurrent()

        assertEquals(listOf("0", "1"), signedWith.map { it.first })
    }

    @Test
    fun aDeadlinePassedOnTheServerIsNeverSignedWhileTheDeviceClockRunsBehind() {
        serverTime.offsetMs = DEVICE_CLOCK_SKEW_MS
        val feature = open(expiresAt = serverTime.currentTimeMillis() - 1L)
        scheduler.runCurrent()

        feature.sendAction(OperationConfirmAction.Confirm)
        scheduler.runCurrent()

        assertTrue(signedWith.isEmpty())
        assertEquals(ConfirmationError.Expired, feature.confirmationError.value)
    }

    @Test
    fun aFailedSendKeepsTheSignatureAndRetrySendsItAgain() {
        chain.error = IllegalStateException("backend is down")
        val feature = open()
        scheduler.runCurrent()

        feature.sendAction(OperationConfirmAction.Confirm)
        scheduler.runCurrent()

        assertEquals(SendingState.None, feature.sendingState.value)
        assertEquals(ConfirmationError.Unknown, feature.confirmationError.value)

        chain.error = null
        feature.sendAction(OperationConfirmAction.Retry)
        scheduler.runCurrent()

        // Signed once: the retry carries the very same bytes.
        assertEquals(2, signedWith.size)
        assertEquals(listOf("0xsigned0", "0xsigned1"), chain.broadcast.map { it.transaction })
        coVerify(exactly = 1) { walletUnlocker.unlockWallet(any(), any()) }
        assertEquals(SendingState.Done, feature.sendingState.value)
        assertNull(feature.confirmationError.value)
    }

    @Test
    fun aSendTheNodeRefusesShowsTheNodesReason() {
        chain.error = NodeError.InsufficientFunds("insufficient funds for gas * price + value")
        val feature = open()
        scheduler.runCurrent()

        feature.sendAction(OperationConfirmAction.Confirm)
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

        feature.sendAction(OperationConfirmAction.Confirm)
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
        feature.sendAction(OperationConfirmAction.Confirm)
        scheduler.runCurrent()
        pricedWith.clear()

        feature.sendAction(OperationConfirmAction.Retry)
        assertNull(feature.fee.value)
        scheduler.runCurrent()

        assertEquals(listOf("0", "1"), pricedWith.map { it.first })
        assertNotNull(feature.fee.value)
        // The failed signature belonged to the price that is gone.
        assertNull(feature.confirmationError.value)
        assertEquals(SendingState.None, feature.sendingState.value)
        coVerify(exactly = 1) { walletUnlocker.unlockWallet(any(), any()) }
    }

    @Test
    fun aSwipeAfterTheSendIsDoneSendsNothingMore() {
        val feature = open()
        scheduler.runCurrent()
        feature.sendAction(OperationConfirmAction.Confirm)
        scheduler.advanceTimeBy(SUCCESS_HOLD_MS)
        scheduler.runCurrent()

        feature.sendAction(OperationConfirmAction.Confirm)
        feature.sendAction(OperationConfirmAction.Retry)
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

        feature.sendAction(OperationConfirmAction.Confirm)
        feature.sendAction(OperationConfirmAction.Confirm)
        // Each of the two payloads takes the delay.
        scheduler.advanceTimeBy(2_000L)
        scheduler.runCurrent()

        assertEquals(2, chain.broadcast.size)
    }

    @Test
    fun aCancelledPasscodeLeavesTheSwipeReady() {
        coEvery { walletUnlocker.unlockWallet(any(), any()) } throws CancellationException()
        val feature = open()
        scheduler.runCurrent()

        feature.sendAction(OperationConfirmAction.Confirm)
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
            TestFeature(request, provider, accountRepo, walletUnlocker, refreshSchedule, serverTime)
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
                expiresAt = serverTime.currentTimeMillis() - 1L,
            ),
            type = deposit,
        )

        val feature = depositFeature(request, authorizer = mockk())
        scheduler.runCurrent()

        assertSame(deposit, feature.deposit.value)
        // TK-4608: the screen does not count the deadline down; only the swipe checks it.
        assertNull(feature.confirmationError.value)
    }

    @Test
    fun aPerpsDepositIsBroadcastThroughChainkit() {
        val request = ConfirmRequest(
            data = CommonTransactionData(
                assetId = TestAssets.usdtEth.id,
                walletId = TestWallet.ID,
                expiresAt = serverTime.currentTimeMillis() + 60_000L,
            ),
            type = ConfirmType.PerpetualDeposit(
                operation = operation(),
                amount = BigInteger.fromInt(5_000_000),
                quote = depositQuote(),
            ),
        )
        val feature = depositFeature(request, authorizer = mockk())
        scheduler.runCurrent()

        feature.sendAction(OperationConfirmAction.Confirm)
        scheduler.runCurrent()

        assertEquals(listOf("0xsigned0", "0xsigned1"), chain.broadcast.map { it.transaction })
        assertEquals(SendingState.Done, feature.sendingState.value)
    }

    @Test
    fun aChangedQuoteThatCannotBePricedShowsWhyAndSendsNothing() {
        val changed = depositRequest(ConfirmOperations.build(listOf(call(index = 2))), preview = false)
        val authorizer = mockk<PerpetualDepositAuthorizer>()
        coEvery { authorizer.authorize(any(), any()) } throws QuoteChangedException(changed, "output.min_amount")
        coEvery { provider.estimateFee(any<Payload>(), any(), any()) } answers {
            if (firstArg<Payload>().id == "2") {
                ChainRes.err(ChainError.BadResponse())
            } else {
                ChainRes.ok(PAYLOAD_FEE)
            }
        }
        val request = depositRequest(operation(), preview = true)
        val feature = depositFeature(request, authorizer = authorizer)
        scheduler.runCurrent()

        feature.sendAction(OperationConfirmAction.Confirm)
        scheduler.runCurrent()

        assertEquals(ConfirmationError.BadResponse, feature.confirmationError.value)
        assertFalse(feature.quoteChanged.value)
        assertSame(request, feature.quoted.value)
        assertTrue(signedWith.isEmpty())
        assertTrue(chain.broadcast.isEmpty())
    }

    @Test
    fun aChangedQuoteIsSignedAsShownAndItsFailedSendShowsWhy() {
        val changed = depositRequest(ConfirmOperations.build(listOf(call(index = 2), call(index = 3))), preview = false)
        val authorizer = mockk<PerpetualDepositAuthorizer>()
        coEvery { authorizer.authorize(any(), any()) } throws QuoteChangedException(changed, "output.min_amount")
        val feature = depositFeature(depositRequest(operation(), preview = true), authorizer = authorizer)
        scheduler.runCurrent()

        feature.sendAction(OperationConfirmAction.Confirm)
        scheduler.runCurrent()

        assertSame(changed, feature.quoted.value)
        assertTrue(feature.quoteChanged.value)
        assertNull(feature.confirmationError.value)
        assertEquals(SendingState.None, feature.sendingState.value)

        chain.error = NodeError.InsufficientFunds("insufficient funds for gas * price + value")
        feature.sendAction(OperationConfirmAction.Confirm)
        scheduler.runCurrent()

        assertFalse(feature.quoteChanged.value)
        assertEquals(listOf("2", "3"), signedWith.map { it.first })
        assertEquals(ConfirmationError.InsufficientBalance, feature.confirmationError.value)
        coVerify(exactly = 1) { authorizer.authorize(any(), any()) }
    }

    @Test
    fun aChangedQuoteGivesUpTheRedSendForTheNewQuoteAndTheNextSendFailsOnItsOwn() {
        val events = recordAnalytics()
        try {
            val changed = depositRequest(ConfirmOperations.build(listOf(call(index = 2), call(index = 3))), preview = false)
            val authorizer = mockk<PerpetualDepositAuthorizer>()
            coEvery { authorizer.authorize(any(), any()) } throws QuoteChangedException(changed, "output.min_amount")
            val feature = depositFeature(depositRequest(operation(), preview = true), authorizer = authorizer)
            scheduler.runCurrent()

            feature.sendAction(OperationConfirmAction.Confirm)
            scheduler.runCurrent()
            chain.error = NodeError.InsufficientFunds("insufficient funds for gas * price + value")
            feature.sendAction(OperationConfirmAction.Confirm)
            scheduler.runCurrent()

            val terminals = events.filter { (event, props) -> event == "op_terminal" && props["operation"] == "send" }.map { it.second }
            assertEquals(listOf("cancel", "fail"), terminals.map { it["outcome"] })
            assertEquals(listOf("send", "validation"), terminals.map { it["stage"] })
            assertEquals(listOf("quote_changed", "insufficient_balance"), terminals.map { it["error_type"] })
            assertEquals("quote changed: output.min_amount", terminals.first()["error_message"])
        } finally {
            unmockkObject(AnalyticsHelper.Companion)
        }
    }

    @Test
    fun aChangedQuoteThatCannotBePricedFailsTheRedSendWithWhy() {
        val events = recordAnalytics()
        try {
            val changed = depositRequest(ConfirmOperations.build(listOf(call(index = 2))), preview = false)
            val authorizer = mockk<PerpetualDepositAuthorizer>()
            coEvery { authorizer.authorize(any(), any()) } throws QuoteChangedException(changed, "output.min_amount")
            coEvery { provider.estimateFee(any<Payload>(), any(), any()) } answers {
                if (firstArg<Payload>().id == "2") {
                    ChainRes.err(ChainError.BadResponse(message = "node down"))
                } else {
                    ChainRes.ok(PAYLOAD_FEE)
                }
            }
            val feature = depositFeature(depositRequest(operation(), preview = true), authorizer = authorizer)
            scheduler.runCurrent()

            feature.sendAction(OperationConfirmAction.Confirm)
            scheduler.runCurrent()

            assertEquals(ConfirmationError.BadResponse, feature.confirmationError.value)
            val terminal = events.single { (event, props) -> event == "op_terminal" && props["operation"] == "send" }.second
            assertEquals("fail", terminal["outcome"])
            assertEquals("send", terminal["stage"])
            assertEquals("bad_response", terminal["error_type"])
            assertEquals("node down", terminal["error_message"])
        } finally {
            unmockkObject(AnalyticsHelper.Companion)
        }
    }

    @Test
    fun aFirstDepositRefusedAsTooLowSaysItsMinimumAndSendsNothing() {
        val events = recordAnalytics()
        try {
            val authorizer = mockk<PerpetualDepositAuthorizer>()
            coEvery { authorizer.authorize(any(), any()) } throws BelowMinimumRefusal(JavaBigDecimal("3.51"))
            val feature = depositFeature(depositRequest(operation(), preview = true), authorizer = authorizer)
            scheduler.runCurrent()

            feature.sendAction(OperationConfirmAction.Confirm)
            scheduler.runCurrent()

            assertEquals(ConfirmationError.DepositBelowMinimum(JavaBigDecimal("3.51")), feature.confirmationError.value)
            assertEquals(SendingState.None, feature.sendingState.value)
            assertTrue(signedWith.isEmpty())
            assertTrue(chain.broadcast.isEmpty())
            val terminal = events.single { (event, props) -> event == "op_terminal" && props["operation"] == "send" }.second
            assertEquals("fail", terminal["outcome"])
            assertEquals("validation", terminal["stage"])
            assertEquals("below_minimum", terminal["error_type"])
        } finally {
            unmockkObject(AnalyticsHelper.Companion)
        }
    }

    @Test
    fun aRetryOfAFailedDepositShowsItQuotedAgainAndConfirmsThatQuote() {
        val requoted = depositRequest(ConfirmOperations.build(listOf(call(index = 2), call(index = 3))), preview = true)
        val authorizer = mockk<PerpetualDepositAuthorizer>()
        coEvery { authorizer.authorize(any(), any()) } throws BelowMinimumRefusal(JavaBigDecimal("3.51"))
        coEvery { authorizer.requote(any()) } returns requoted
        val request = depositRequest(operation(), preview = true)
        val feature = depositFeature(request, authorizer = authorizer)
        scheduler.runCurrent()
        feature.sendAction(OperationConfirmAction.Confirm)
        scheduler.runCurrent()
        pricedWith.clear()

        feature.sendAction(OperationConfirmAction.Retry)
        scheduler.runCurrent()

        coVerify(exactly = 1) { authorizer.requote(request) }
        assertSame(requoted, feature.quoted.value)
        assertSame(requoted.type, feature.deposit.value)
        assertEquals(listOf("2", "3"), pricedWith.map { it.first })
        assertNotNull(feature.fee.value)
        assertNull(feature.confirmationError.value)
        assertFalse(feature.quoteChanged.value)

        feature.sendAction(OperationConfirmAction.RefreshFees)
        scheduler.runCurrent()
        coVerify(exactly = 1) { authorizer.requote(any()) }

        feature.sendAction(OperationConfirmAction.Confirm)
        scheduler.runCurrent()

        coVerify(exactly = 1) { authorizer.authorize(requoted, any()) }
    }

    @Test
    fun aFeeRefreshAfterARefusedRequoteQuotesAgainRatherThanShowTheOldQuote() {
        val authorizer = mockk<PerpetualDepositAuthorizer>()
        coEvery { authorizer.authorize(any(), any()) } throws IllegalStateException("quote refused")
        coEvery { authorizer.requote(any()) } throws BelowMinimumRefusal(JavaBigDecimal("3.72"))
        val request = depositRequest(operation(), preview = true)
        val feature = depositFeature(request, authorizer = authorizer)
        scheduler.runCurrent()
        feature.sendAction(OperationConfirmAction.Confirm)
        scheduler.runCurrent()
        feature.sendAction(OperationConfirmAction.Retry)
        scheduler.runCurrent()

        feature.sendAction(OperationConfirmAction.RefreshFees)
        scheduler.runCurrent()

        coVerify(exactly = 2) { authorizer.requote(request) }
        assertEquals(ConfirmationError.DepositBelowMinimum(JavaBigDecimal("3.72")), feature.confirmationError.value)
        assertNull(feature.fee.value)
    }

    @Test
    fun aRetryWhoseNewQuoteIsRefusedSaysWhyAndKeepsTheQuoteItHad() {
        val authorizer = mockk<PerpetualDepositAuthorizer>()
        coEvery { authorizer.authorize(any(), any()) } throws IllegalStateException("quote refused")
        coEvery { authorizer.requote(any()) } throws BelowMinimumRefusal(JavaBigDecimal("3.72"))
        val request = depositRequest(operation(), preview = true)
        val feature = depositFeature(request, authorizer = authorizer)
        scheduler.runCurrent()
        feature.sendAction(OperationConfirmAction.Confirm)
        scheduler.runCurrent()
        assertEquals(ConfirmationError.Unknown, feature.confirmationError.value)

        feature.sendAction(OperationConfirmAction.Retry)
        scheduler.runCurrent()

        assertEquals(ConfirmationError.DepositBelowMinimum(JavaBigDecimal("3.72")), feature.confirmationError.value)
        assertSame(request, feature.quoted.value)
        assertNull(feature.fee.value)
    }

    @Test
    fun aFeeRefreshWhileARetryQuotesAgainKeepsTheNewQuote() {
        val requoted = depositRequest(ConfirmOperations.build(listOf(call(index = 2))), preview = true)
        val authorizer = mockk<PerpetualDepositAuthorizer>()
        coEvery { authorizer.authorize(any(), any()) } throws IllegalStateException("quote refused")
        coEvery { authorizer.requote(any()) } coAnswers {
            delay(1_000L)
            requoted
        }
        val feature = depositFeature(depositRequest(operation(), preview = true), authorizer = authorizer)
        scheduler.runCurrent()
        feature.sendAction(OperationConfirmAction.Confirm)
        scheduler.runCurrent()

        feature.sendAction(OperationConfirmAction.Retry)
        scheduler.runCurrent()
        feature.sendAction(OperationConfirmAction.RefreshFees)
        scheduler.advanceTimeBy(1_000L)
        scheduler.runCurrent()

        assertSame(requoted, feature.quoted.value)
        assertNull(feature.confirmationError.value)
        assertNotNull(feature.fee.value)
    }

    @Test
    fun aRetryReportsItsRequoteAsAQuoteOperation() {
        val events = recordAnalytics()
        try {
            val requoted = depositRequest(ConfirmOperations.build(listOf(call(index = 2))), preview = true)
            val authorizer = mockk<PerpetualDepositAuthorizer>()
            coEvery { authorizer.authorize(any(), any()) } throws IllegalStateException("quote refused")
            coEvery { authorizer.requote(any()) } throws BelowMinimumRefusal(JavaBigDecimal("3.72")) andThen requoted
            val feature = depositFeature(depositRequest(operation(), preview = true), authorizer = authorizer)
            scheduler.runCurrent()
            feature.sendAction(OperationConfirmAction.Confirm)
            scheduler.runCurrent()

            feature.sendAction(OperationConfirmAction.Retry)
            scheduler.runCurrent()
            feature.sendAction(OperationConfirmAction.Retry)
            scheduler.runCurrent()

            val quotes = events.filter { (event, props) -> event == "op_terminal" && props["operation"] == "quote" }.map { it.second }
            assertEquals(listOf("fail", "success"), quotes.map { it["outcome"] })
            assertEquals("below_minimum", quotes.first()["error_type"])
            assertEquals("validation", quotes.first()["stage"])
        } finally {
            unmockkObject(AnalyticsHelper.Companion)
        }
    }

    @Test
    fun aRequoteThatCannotReachTheBackendEndsAtTheQuoteStage() {
        val events = recordAnalytics()
        try {
            val authorizer = mockk<PerpetualDepositAuthorizer>()
            coEvery { authorizer.authorize(any(), any()) } throws IllegalStateException("quote refused")
            coEvery { authorizer.requote(any()) } throws IOException("offline")
            val feature = depositFeature(depositRequest(operation(), preview = true), authorizer = authorizer)
            scheduler.runCurrent()
            feature.sendAction(OperationConfirmAction.Confirm)
            scheduler.runCurrent()

            feature.sendAction(OperationConfirmAction.Retry)
            scheduler.runCurrent()

            val quote = events.single { (event, props) -> event == "op_terminal" && props["operation"] == "quote" }.second
            assertEquals("fail", quote["outcome"])
            assertEquals("quote", quote["stage"])
        } finally {
            unmockkObject(AnalyticsHelper.Companion)
        }
    }

    @Test
    fun aFeeRefreshPricesTheDepositShownWithoutQuotingItAgain() {
        val authorizer = mockk<PerpetualDepositAuthorizer>()
        val request = depositRequest(operation(), preview = true)
        val feature = depositFeature(request, authorizer = authorizer)
        scheduler.runCurrent()
        pricedWith.clear()

        feature.sendAction(OperationConfirmAction.RefreshFees)
        scheduler.runCurrent()

        coVerify(exactly = 0) { authorizer.requote(any()) }
        assertSame(request, feature.quoted.value)
        assertEquals(listOf("0", "1"), pricedWith.map { it.first })
    }

    @Test
    fun aCoinShortOfItsFeeReportsWhatTheNodeSaidUnderTheWrap() {
        val events = recordAnalytics()
        try {
            coEvery { provider.estimateFee(any<Payload>(), any(), any()) } returns ChainRes.err(ChainError.InsufficientFunds("no gas"))
            val feature = depositFeature(depositRequest(operation(), preview = false, assetId = TestAssets.eth.id), mockk())
            scheduler.runCurrent()

            assertEquals(ConfirmationError.InsufficientNetworkFee(ETH_SYMBOL), feature.confirmationError.value)
            val terminal = events.single { (event, props) -> event == "op_terminal" && props["operation"] == "emulate" }.second
            assertEquals("validation", terminal["stage"])
            assertEquals("insufficient_network_fee", terminal["error_type"])
            assertEquals("no gas", terminal["error_message"])
        } finally {
            unmockkObject(AnalyticsHelper.Companion)
        }
    }

    @Test
    fun aDepositTellsItsTrackerEachErrorItShowsAndEachChangedQuoteButNotACancelledPasscode() {
        val changed = depositRequest(ConfirmOperations.build(listOf(call(index = 2), call(index = 3))), preview = false)
        val authorizer = mockk<PerpetualDepositAuthorizer>()
        coEvery { authorizer.authorize(any(), any()) } throws QuoteChangedException(changed, "output.min_amount")
        val tracker = RecordingDepositTracker()
        val feature = depositFeature(depositRequest(operation(), preview = true), authorizer = authorizer, tracker = tracker)
        scheduler.runCurrent()

        coEvery { walletUnlocker.unlockWallet(any(), any()) } throws CancellationException()
        feature.sendAction(OperationConfirmAction.Confirm)
        scheduler.runCurrent()

        assertTrue(tracker.errors.isEmpty())
        assertEquals(0, tracker.quoteChanges)

        coEvery { walletUnlocker.unlockWallet(any(), any()) } returns cryptoWallet
        feature.sendAction(OperationConfirmAction.Confirm)
        scheduler.runCurrent()

        assertEquals(1, tracker.quoteChanges)
        assertTrue(tracker.errors.isEmpty())

        chain.error = NodeError.InsufficientFunds("insufficient funds for gas * price + value")
        feature.sendAction(OperationConfirmAction.Confirm)
        scheduler.runCurrent()

        assertEquals(listOf<ConfirmationError>(ConfirmationError.InsufficientBalance), tracker.errors)
        assertEquals(1, tracker.quoteChanges)
    }

    @Test
    fun aRelayedMethodSendsThroughTheRelayerAndNothingThroughTheChain() {
        val methods = FakeFeeMethods(batteryCharges = BATTERY_BALANCE) { "0xrelayed" }
        val feature = open(feeMethods = methods)
        val events = collectEvents(feature)
        scheduler.runCurrent()

        assertEquals(Asset.Battery.ID, feature.feeMethod.value?.id)
        assertEquals(Asset.Battery.ID, feature.fee.value?.asset?.id)

        feature.sendAction(OperationConfirmAction.Confirm)
        scheduler.runCurrent()

        assertEquals(listOf(Asset.Battery.ID), methods.relayed.map { it.id })
        assertTrue(signedWith.isEmpty())
        assertTrue(chain.broadcast.isEmpty())
        verify(exactly = 1) { refreshSchedule.notifyTransactionSent(TestWallet.ID, Chain.Ethereum.Mainnet) }
        assertEquals(SendingState.Done, feature.sendingState.value)

        scheduler.advanceTimeBy(SUCCESS_HOLD_MS)
        scheduler.runCurrent()
        assertEquals(listOf<ConfirmEvent>(ConfirmEvent.Done), events)
    }

    @Test
    fun aTrackedRelayerThatReturnsNoHashIsAskedOnce() {
        val methods = FakeFeeMethods(batteryCharges = BATTERY_BALANCE) { null }
        val tracked = mutableListOf<String?>()
        val tracker = object : OperationTracker {
            override suspend fun trackRelayed(request: ConfirmRequest, account: Account, send: suspend () -> String?): String? =
                send().also { tracked += it }
        }
        val feature = open(feeMethods = methods, tracker = tracker)
        scheduler.runCurrent()

        feature.sendAction(OperationConfirmAction.Confirm)
        scheduler.runCurrent()

        assertEquals(listOf(Asset.Battery.ID), methods.relayed.map { it.id })
        assertEquals(listOf<String?>(null), tracked)
        assertEquals(SendingState.Done, feature.sendingState.value)
    }

    @Test
    fun theCoinPickedOverTheRelayerIsSignedAndBroadcast() {
        val methods = FakeFeeMethods(batteryCharges = BATTERY_BALANCE) { "0xrelayed" }
        val feature = open(feeMethods = methods)
        scheduler.runCurrent()

        val coin = feature.feeMethods.value?.options?.first { it.isNative } ?: error("no coin method")
        feature.pickFee(coin)
        scheduler.runCurrent()
        feature.sendAction(OperationConfirmAction.Confirm)
        scheduler.runCurrent()

        assertEquals(listOf(coin), methods.remembered)
        assertTrue(methods.relayed.isEmpty())
        assertEquals(listOf("0xsigned0", "0xsigned1"), chain.broadcast.map { it.transaction })
    }

    @Test
    fun aWalletShortOfItsCoinPreparesWhenTheRelayerCanPay() {
        accounts[TestAssets.eth.id] = testAccount(TestAssets.eth, balance = "0", price = "2000")
        val feature = open(feeMethods = FakeFeeMethods(batteryCharges = BATTERY_BALANCE) { "0xrelayed" })
        scheduler.runCurrent()

        assertNull(feature.confirmationError.value)
        assertEquals(Asset.Battery.ID, feature.feeMethod.value?.id)
    }

    @Test
    fun aWalletShortOfItsCoinWithNothingRelayableIsShortOfTheNetworkFee() {
        accounts[TestAssets.eth.id] = testAccount(TestAssets.eth, balance = "0", price = "2000")
        val feature = open(feeMethods = FakeFeeMethods(batteryCharges = BigInteger.ZERO) { "0xrelayed" })
        scheduler.runCurrent()

        assertEquals(ConfirmationError.InsufficientNetworkFee(ETH_SYMBOL, JavaBigDecimal("0.0006")), feature.confirmationError.value)
        assertNull(feature.fee.value)
    }

    @Test
    fun aRelayerThatCannotPriceTheMethodLeavesTheShortfallAndSendsNothing() {
        accounts[TestAssets.eth.id] = testAccount(TestAssets.eth, balance = "0", price = "2000")
        val methods = FakeFeeMethods(batteryCharges = BATTERY_BALANCE, quote = RelayerQuote { _, _ -> null }) { "0xrelayed" }
        val feature = open(feeMethods = methods)
        scheduler.runCurrent()

        assertEquals(ConfirmationError.InsufficientBalance, feature.confirmationError.value)
        assertEquals(false, feature.feeMethods.value?.findOption(Asset.Battery.ID)?.available)

        feature.sendAction(OperationConfirmAction.Confirm)
        scheduler.runCurrent()
        assertTrue(methods.relayed.isEmpty())
        assertTrue(chain.broadcast.isEmpty())
    }

    @Test
    fun aRelayerThatLostTheAnswerLeavesNothingToRetry() {
        val methods = FakeFeeMethods(batteryCharges = BATTERY_BALANCE) {
            throw RelayerOutcomeUnknownException(IOException("timeout"))
        }
        val feature = open(feeMethods = methods)
        scheduler.runCurrent()

        feature.sendAction(OperationConfirmAction.Confirm)
        scheduler.runCurrent()
        assertEquals(ConfirmationError.OutcomeUnknown, feature.confirmationError.value)

        val priced = pricedWith.size
        feature.sendAction(OperationConfirmAction.Retry)
        feature.sendAction(OperationConfirmAction.Confirm)
        scheduler.runCurrent()

        assertEquals(1, methods.relayed.size)
        assertEquals(priced, pricedWith.size)
        assertEquals(ConfirmationError.OutcomeUnknown, feature.confirmationError.value)
    }

    @Test
    fun aRelayerThatLostTheAnswerIgnoresAFeeRefresh() {
        val methods = FakeFeeMethods(batteryCharges = BATTERY_BALANCE) {
            throw RelayerOutcomeUnknownException(IOException("timeout"))
        }
        val feature = open(feeMethods = methods)
        scheduler.runCurrent()

        feature.sendAction(OperationConfirmAction.Confirm)
        scheduler.runCurrent()
        assertEquals(ConfirmationError.OutcomeUnknown, feature.confirmationError.value)

        val built = methods.built
        feature.sendAction(OperationConfirmAction.RefreshFees)
        scheduler.runCurrent()
        feature.sendAction(OperationConfirmAction.Confirm)
        scheduler.runCurrent()

        assertEquals(built, methods.built)
        assertEquals(1, methods.relayed.size)
        assertEquals(ConfirmationError.OutcomeUnknown, feature.confirmationError.value)
    }

    @Test
    fun aRelayerRejectionThatSentNothingKeepsItsReasonForAnalyticsAndCanBeAskedAgain() {
        val events = recordAnalytics()
        try {
            var reject = true
            val methods = FakeFeeMethods(batteryCharges = BATTERY_BALANCE) {
                if (reject) {
                    throw RelayerRejectedException(reason = "not enough charges", nothingSent = true)
                }
                "0xrelayed"
            }
            val feature = open(feeMethods = methods)
            scheduler.runCurrent()

            feature.sendAction(OperationConfirmAction.Confirm)
            scheduler.runCurrent()

            // The screen says only that the send failed: the reason is for analytics and the logs.
            assertEquals(ConfirmationError.RelayerRejected("not enough charges"), feature.confirmationError.value)
            val terminal = events.single { (event, props) -> event == "op_terminal" && props["operation"] == "send" }.second
            assertEquals("relayer_rejected", terminal["error_type"])
            assertEquals("not enough charges", terminal["error_message"])

            reject = false
            feature.sendAction(OperationConfirmAction.Retry)
            scheduler.runCurrent()
            feature.sendAction(OperationConfirmAction.Confirm)
            scheduler.runCurrent()

            assertEquals(2, methods.relayed.size)
            assertEquals(SendingState.Done, feature.sendingState.value)
        } finally {
            unmockkObject(AnalyticsHelper.Companion)
        }
    }

    @Test
    fun aRelayerRejectionThatMayHaveSentLeavesNothingToRetry() {
        val methods = FakeFeeMethods(batteryCharges = BATTERY_BALANCE) {
            throw RelayerRejectedException(reason = null)
        }
        val feature = open(feeMethods = methods)
        scheduler.runCurrent()

        feature.sendAction(OperationConfirmAction.Confirm)
        scheduler.runCurrent()
        assertEquals(ConfirmationError.OutcomeUnknown, feature.confirmationError.value)

        feature.sendAction(OperationConfirmAction.Retry)
        feature.sendAction(OperationConfirmAction.Confirm)
        scheduler.runCurrent()

        assertEquals(1, methods.relayed.size)
        assertEquals(ConfirmationError.OutcomeUnknown, feature.confirmationError.value)
    }

    @Test
    fun aRelayerThatRefusedTheSendCanBeAskedAgain() {
        var refuse = true
        val methods = FakeFeeMethods(batteryCharges = BATTERY_BALANCE) {
            if (refuse) {
                throw IllegalStateException("relayer failed")
            }
            "0xrelayed"
        }
        val feature = open(feeMethods = methods)
        scheduler.runCurrent()

        feature.sendAction(OperationConfirmAction.Confirm)
        scheduler.runCurrent()
        assertEquals(ConfirmationError.Unknown, feature.confirmationError.value)

        refuse = false
        feature.sendAction(OperationConfirmAction.Retry)
        scheduler.runCurrent()
        feature.sendAction(OperationConfirmAction.Confirm)
        scheduler.runCurrent()

        assertEquals(2, methods.relayed.size)
        assertEquals(SendingState.Done, feature.sendingState.value)
    }

    @Test
    fun refreshingTheFeesPricesTheOperationAgainUntilItIsSent() {
        val methods = FakeFeeMethods(batteryCharges = BATTERY_BALANCE) { "0xrelayed" }
        val feature = open(feeMethods = methods)
        scheduler.runCurrent()
        assertEquals(1, methods.built)

        feature.sendAction(OperationConfirmAction.RefreshFees)
        scheduler.runCurrent()
        assertEquals(2, methods.built)

        feature.sendAction(OperationConfirmAction.Confirm)
        scheduler.runCurrent()
        feature.sendAction(OperationConfirmAction.RefreshFees)
        scheduler.runCurrent()

        assertEquals(2, methods.built)
        assertEquals(SendingState.Done, feature.sendingState.value)
    }

    @Test
    fun aFirstDepositIsOfferedTheRelayerPricedOnItsPayment() {
        val payment = depositRequest(ConfirmOperations.build(listOf(call(index = 5))), preview = false)
        val relayedFor = mutableListOf<ConfirmRequest>()
        val feeMethods = relayingDepositFeeMethods(payment, relayedFor)
        val feature = depositFeature(depositRequest(operation(), preview = true), mockk(), feeMethods)
        scheduler.runCurrent()

        assertEquals(Asset.Battery.ID, feature.feeMethod.value?.id)
        coVerify { feeMethods.build(payment, any(), any(), any()) }
    }

    @Test
    fun aRelayedFirstDepositRegistersTheKeyThenRelaysTheDepositItGets() {
        val payment = depositRequest(ConfirmOperations.build(listOf(call(index = 5))), preview = false)
        val authorized = depositRequest(ConfirmOperations.build(listOf(call(index = 2))), preview = false)
        val authorizer = mockk<PerpetualDepositAuthorizer>()
        coEvery { authorizer.authorize(any(), any()) } returns authorized
        val relayedFor = mutableListOf<ConfirmRequest>()
        val feature = depositFeature(
            depositRequest(operation(), preview = true),
            authorizer,
            relayingDepositFeeMethods(payment, relayedFor),
        )
        scheduler.runCurrent()

        feature.sendAction(OperationConfirmAction.Confirm)
        scheduler.runCurrent()

        assertEquals(listOf(authorized), relayedFor)
        assertTrue(signedWith.isEmpty())
        assertTrue(chain.broadcast.isEmpty())
        coVerify(exactly = 1) { authorizer.authorize(any(), any()) }
        assertEquals(SendingState.Done, feature.sendingState.value)
    }

    @Test
    fun aFirstDepositSignedInTheCoinIsNeverSignedWhenTheCoinCannotPayWhateverTheRelayerOffers() {
        accounts[TestAssets.eth.id] = testAccount(TestAssets.eth, balance = "600000000000000", price = "2000")
        val authorized = depositRequest(ConfirmOperations.build(listOf(call(index = 0), call(index = 1), call(index = 2))), preview = false)
        val authorizer = mockk<PerpetualDepositAuthorizer>()
        coEvery { authorizer.authorize(any(), any()) } returns authorized
        val feature = depositFeature(
            depositRequest(operation(), preview = true),
            authorizer,
            relayingDepositFeeMethods(payment = null, relayedFor = mutableListOf()),
        )
        scheduler.runCurrent()
        assertNull(feature.confirmationError.value)

        feature.sendAction(OperationConfirmAction.Confirm)
        scheduler.runCurrent()

        assertEquals(ConfirmationError.InsufficientNetworkFee(ETH_SYMBOL, JavaBigDecimal("0.0009")), feature.confirmationError.value)
        assertTrue(signedWith.isEmpty())
        assertTrue(chain.broadcast.isEmpty())
        coVerify(exactly = 1) { authorizer.authorize(any(), any()) }
    }

    @Test
    fun aRelayedFirstDepositWhoseFeeRoseAfterTheKeyShowsTheNewFeeAndRelaysNothing() {
        val payment = depositRequest(ConfirmOperations.build(listOf(call(index = 5))), preview = false)
        val authorized = depositRequest(ConfirmOperations.build(listOf(call(index = 2))), preview = false)
        val authorizer = mockk<PerpetualDepositAuthorizer>()
        coEvery { authorizer.authorize(any(), any()) } returns authorized
        val relayedFor = mutableListOf<ConfirmRequest>()
        val feature = depositFeature(
            depositRequest(operation(), preview = true),
            authorizer,
            relayingDepositFeeMethods(payment, relayedFor) { built ->
                if (built === authorized) {
                    RAISED_CHARGES
                } else {
                    RELAYED_CHARGES
                }
            },
        )
        scheduler.runCurrent()

        feature.sendAction(OperationConfirmAction.Confirm)
        scheduler.runCurrent()

        assertSame(authorized, feature.quoted.value)
        assertTrue(feature.quoteChanged.value)
        assertEquals(RAISED_CHARGES, feature.fee.value?.amount)
        assertTrue(relayedFor.isEmpty())
    }

    @Test
    fun aRelayedFirstDepositWhoseQuoteChangedShowsItAndRelaysItOnTheNextSwipe() {
        val payment = depositRequest(ConfirmOperations.build(listOf(call(index = 5))), preview = false)
        val changed = depositRequest(ConfirmOperations.build(listOf(call(index = 2))), preview = false)
        val authorizer = mockk<PerpetualDepositAuthorizer>()
        coEvery { authorizer.authorize(any(), any()) } throws QuoteChangedException(changed, "output.min_amount")
        val relayedFor = mutableListOf<ConfirmRequest>()
        val feature = depositFeature(
            depositRequest(operation(), preview = true),
            authorizer,
            relayingDepositFeeMethods(payment, relayedFor),
        )
        scheduler.runCurrent()

        feature.sendAction(OperationConfirmAction.Confirm)
        scheduler.runCurrent()

        assertSame(changed, feature.quoted.value)
        assertTrue(feature.quoteChanged.value)
        assertTrue(relayedFor.isEmpty())

        feature.sendAction(OperationConfirmAction.Confirm)
        scheduler.runCurrent()

        assertEquals(listOf(changed), relayedFor)
        assertTrue(chain.broadcast.isEmpty())
        coVerify(exactly = 1) { authorizer.authorize(any(), any()) }
    }

    @Test
    fun aPreviewIsNeverOfferedTheRelayer() {
        val methods = FakeFeeMethods(batteryCharges = BATTERY_BALANCE) { "0xrelayed" }
        val request = ConfirmRequest(
            data = CommonTransactionData(assetId = TestAssets.usdtEth.id, walletId = TestWallet.ID, preview = true),
            type = ConfirmType.Call(operation()),
        )
        val feature = TestFeature(request, provider, accountRepo, walletUnlocker, refreshSchedule, serverTime, methods)
        scheduler.runCurrent()

        assertNull(feature.feeMethods.value)
        assertEquals(0, methods.built)
    }

    private fun depositFeature(
        request: ConfirmRequest,
        authorizer: PerpetualDepositAuthorizer,
        feeMethods: PerpetualDepositFeeMethods = coinOnlyDepositFeeMethods(),
        tracker: PerpetualDepositTracker? = null,
    ): PerpetualDepositConfirmFeature {
        return PerpetualDepositConfirmFeature(
            request = request,
            provider = provider,
            accountRepo = accountRepo,
            walletUnlocker = walletUnlocker,
            signatureGate = SignatureGate { false },
            postTransactionRefreshSchedule = refreshSchedule,
            timeProvider = serverTime,
            tracker = tracker,
            authorizer = authorizer,
            depositFeeMethods = feeMethods,
        )
    }

    private fun coinOnlyDepositFeeMethods(): PerpetualDepositFeeMethods {
        val feeMethods = mockk<PerpetualDepositFeeMethods>()
        coEvery { feeMethods.build(any(), any(), any(), any()) } answers { TxFeeState(energy = thirdArg(), estimated = arg(3)) }
        every { feeMethods.payment(any()) } returns null
        every { feeMethods.buildsUnpriced } returns false
        return feeMethods
    }

    private fun relayingDepositFeeMethods(
        payment: ConfirmRequest?,
        relayedFor: MutableList<ConfirmRequest>,
        charges: (ConfirmRequest) -> BigInteger = { RELAYED_CHARGES },
    ): PerpetualDepositFeeMethods {
        val feeMethods = mockk<PerpetualDepositFeeMethods>(relaxed = true)
        every { feeMethods.payment(any()) } returns payment
        coEvery { feeMethods.chargeCostInTon() } returns null
        coEvery { feeMethods.build(any(), any(), any(), any()) } answers {
            val built = firstArg<ConfirmRequest>()
            val battery = TxFee(
                account = FeeAccount.Keeper(balance = BATTERY_BALANCE, rate = null),
                fee = Fee.Value(Asset.Battery(Chain.Ethereum.Mainnet), charges(built)),
            )
            TxFeeState(
                energy = thirdArg(),
                estimated = arg(3),
                options = listOf(battery, TxFee(account = FeeAccount.Chain(thirdArg()), fee = arg(3))),
                relayer = RelayerSend { _, _ ->
                    relayedFor += built
                    "0xrelayed"
                },
            )
        }
        return feeMethods
    }

    private fun depositRequest(operation: Operation, preview: Boolean, assetId: String = TestAssets.usdtEth.id) = ConfirmRequest(
        data = CommonTransactionData(
            assetId = assetId,
            walletId = TestWallet.ID,
            expiresAt = System.currentTimeMillis() + 60_000L,
            preview = preview,
        ),
        type = ConfirmType.PerpetualDeposit(
            operation = operation,
            amount = BigInteger.fromInt(5_000_000),
            quote = depositQuote(),
        ),
    )

    private fun open(
        operation: Operation = operation(),
        expiresAt: Long? = null,
        feeMethods: OperationFeeMethods? = null,
        tracker: OperationTracker? = null,
    ): TestFeature {
        val request = ConfirmRequest(
            data = CommonTransactionData(assetId = TestAssets.usdtEth.id, walletId = TestWallet.ID, expiresAt = expiresAt),
            type = ConfirmType.Call(operation),
        )
        return TestFeature(request, provider, accountRepo, walletUnlocker, refreshSchedule, serverTime, feeMethods, tracker)
    }

    private fun recordAnalytics(): List<Pair<String, Map<String, Any>>> {
        val tracked = CopyOnWriteArrayList<Pair<String, Map<String, Any>>>()
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
        return tracked
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
        walletUnlocker: WalletUnlocker,
        refreshSchedule: PostTransactionRefreshSchedule,
        timeProvider: TimeProvider,
        feeMethods: OperationFeeMethods? = null,
        tracker: OperationTracker? = null,
    ) : BaseOperationConfirmFeature(
        request = request,
        provider = provider,
        accountRepo = accountRepo,
        walletUnlocker = walletUnlocker,
        signatureGate = SignatureGate { false },
        postTransactionRefreshSchedule = refreshSchedule,
        intent = { _, _ -> Intent(spends = null, to = null, source = Intent.Source.DApp) },
        timeProvider = timeProvider,
        tracker = tracker,
        relayedFeeMethods = feeMethods,
    )

    private class FakeFeeMethods(
        private val batteryCharges: BigInteger,
        private val quote: RelayerQuote? = null,
        private val send: suspend (TxFee) -> String?,
    ) : OperationFeeMethods {

        val relayed = mutableListOf<TxFee>()
        val remembered = mutableListOf<TxFee>()
        var built = 0

        override suspend fun build(
            request: ConfirmRequest,
            position: AccountWithDetails,
            energy: AccountWithDetails,
            estimated: Fee,
        ): TxFeeState {
            built += 1
            val battery = TxFee(
                account = FeeAccount.Keeper(balance = batteryCharges, rate = null),
                fee = Fee.Value(Asset.Battery(Chain.Ethereum.Mainnet), RELAYED_CHARGES),
            )
            return TxFeeState(
                energy = energy,
                estimated = estimated,
                options = listOf(battery, TxFee(account = FeeAccount.Chain(energy), fee = estimated)),
                relayer = RelayerSend { _, option ->
                    relayed += option
                    send(option)
                },
                quote = quote,
            )
        }

        override fun remember(walletId: String, option: TxFee) {
            remembered += option
        }
    }

    private class RecordingDepositTracker : PerpetualDepositTracker {

        val errors = mutableListOf<ConfirmationError>()
        var quoteChanges = 0

        override fun onError(error: ConfirmationError) {
            errors += error
        }

        override fun onQuoteChanged() {
            quoteChanges += 1
        }
    }

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
        const val DEVICE_CLOCK_SKEW_MS = 60L * 60L * 1_000L
        val NONCE: BigInteger = BigInteger.fromInt(7)
        val PAYLOAD_FEE = Fee.Value(Chain.Ethereum.Mainnet.toAsset(), BigInteger.parseString("300000000000000"))
        val RELAYED_CHARGES: BigInteger = BigInteger.fromInt(12)
        val RAISED_CHARGES: BigInteger = BigInteger.fromInt(13)
        val BATTERY_BALANCE: BigInteger = BigInteger.fromInt(100)
        val ETH_SYMBOL: String = Chain.Ethereum.Mainnet.toAsset().symbol
    }
}

private class BelowMinimumRefusal(minUsd: JavaBigDecimal) : Exception("amount_too_low"), ConfirmationErrorCause {
    override val confirmationError: ConfirmationError = ConfirmationError.DepositBelowMinimum(minUsd)
}
