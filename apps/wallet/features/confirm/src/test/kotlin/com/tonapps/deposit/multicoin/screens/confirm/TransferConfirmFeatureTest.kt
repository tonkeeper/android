package com.tonapps.deposit.multicoin.screens.confirm

import com.ionspin.kotlin.bignum.integer.BigInteger
import com.tonapps.blockchain.model.CommonTransactionData
import com.tonapps.blockchain.model.ConfirmContext
import com.tonapps.blockchain.model.ConfirmOperations
import com.tonapps.blockchain.model.ConfirmRequest
import com.tonapps.blockchain.model.ConfirmType
import com.tonapps.chainkit.core.chain.api.models.ChainRes
import com.tonapps.chainkit.core.chain.api.models.NodeError
import com.tonapps.chainkit.core.chain.api.models.NodeRes
import com.tonapps.chainkit.core.chain.api.models.SignRes
import com.tonapps.chainkit.core.chain.model.account.Account
import com.tonapps.chainkit.core.chain.model.account.Address
import com.tonapps.chainkit.core.chain.model.account.Asset
import com.tonapps.chainkit.core.chain.model.account.Chain
import com.tonapps.chainkit.core.chain.model.account.MnemonicWallet
import com.tonapps.chainkit.core.chain.model.transaction.Fee
import com.tonapps.chainkit.core.op.Intent
import com.tonapps.chainkit.core.op.Operation
import com.tonapps.chainkit.core.op.OpRes
import com.tonapps.chainkit.core.op.Payload
import com.tonapps.chainkit.core.op.PayloadMeta
import com.tonapps.chainkit.core.op.SignedPayload
import com.tonapps.chainkit.core.op.ValidationError
import com.tonapps.chainkit.core.op.ValidationRes
import com.tonapps.chainkit.transaction.GasReserveResult
import com.tonapps.core.flags.InAppReviewManager
import com.tonapps.core.flags.BootFlags
import com.tonapps.core.flags.WalletFeatureKey
import com.tonapps.deposit.multicoin.screens.confirm.base.ConfirmationError
import com.tonapps.deposit.multicoin.screens.confirm.base.SUCCESS_HOLD_MS
import com.tonapps.deposit.multicoin.screens.confirm.base.SendingState
import com.tonapps.deposit.multicoin.screens.confirm.base.SignatureGate
import com.tonapps.deposit.multicoin.screens.confirm.base.operation.FindingSeverity
import com.tonapps.deposit.multicoin.screens.confirm.base.operation.OperationConfirmAction
import com.tonapps.deposit.multicoin.screens.confirm.base.operation.OperationShortfall
import com.tonapps.chainkit.core.chain.api.models.ChainError
import com.tonapps.deposit.multicoin.screens.confirm.fee.FeeAccount
import com.tonapps.deposit.multicoin.screens.confirm.fee.RelayerSend
import com.tonapps.deposit.multicoin.screens.confirm.fee.TxFee
import com.tonapps.deposit.multicoin.screens.confirm.fee.TxFeeBuilder
import com.tonapps.deposit.multicoin.screens.confirm.fee.TxFeeState
import com.tonapps.deposit.multicoin.screens.confirm.transfer.TransferConfirmFeature
import com.tonapps.deposit.multicoin.screens.confirm.transfer.confirmsAsTransferOperation
import com.tonapps.wallet.ChainKitProvider
import com.tonapps.wallet.data.multichain.account.McAccountRepository
import com.tonapps.wallet.data.multichain.tx.PostTransactionRefreshSchedule
import com.tonapps.wallet.data.passcode.WalletUnlocker
import com.tonapps.wallet.data.settings.SettingsRepository
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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.serialization.json.JsonPrimitive
import org.junit.After
import com.tonapps.deposit.multicoin.screens.confirm.fee.GaslessRelayer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import com.tonapps.bus.core.AnalyticsHelper
import com.tonapps.bus.core.contract.EventExecutor
import com.tonapps.bus.generated.DefaultEvents
import org.json.JSONObject
import java.math.BigDecimal
import java.util.concurrent.CopyOnWriteArrayList
import com.tonapps.chainkit.core.chain.model.num.toDisplayUnit

/**
 * The wallet's own send on the operation confirm: the transfer the screen holds is built through
 * chain-kit, priced, held to what the user asked for and signed as built.
 */
class TransferConfirmFeatureTest : BaseUnitTest() {

    private val cryptoWallet = mockk<MnemonicWallet>()
    private val accountRepo = mockk<McAccountRepository>()
    private val walletUnlocker = mockk<WalletUnlocker>()
    private val provider = mockk<ChainKitProvider>()
    private val feeBuilder = mockk<TxFeeBuilder>()
    private val settingsRepository = mockk<SettingsRepository>(relaxed = true)
    private val refreshSchedule = mockk<PostTransactionRefreshSchedule>(relaxed = true)

    private val accounts = mutableMapOf(
        TestAssets.usdtEth.id to testAccount(TestAssets.usdtEth, balance = "100000000"),
        TestAssets.eth.id to testAccount(TestAssets.eth, balance = BALANCE.toString(), price = "2000"),
        TestAssets.ton.id to testAccount(TestAssets.ton, balance = BALANCE.toString()),
    )
    private val builtMemos = mutableListOf<String?>()
    private val built = mutableListOf<BigInteger>()
    private val priced = mutableListOf<PayloadMeta>()
    private val pricedAs = mutableListOf<Account>()
    private val signedAs = mutableListOf<Account>()
    private val signed = mutableListOf<PayloadMeta>()
    private val signedFees = mutableListOf<Fee>()
    private val intents = mutableListOf<Intent>()
    private var reserve = GasReserveResult(AMOUNT, isAmountAdjusted = false, error = null)
    private var tokenGas: BigInteger = BigInteger.ZERO

    private val scheduler get() = dispatchers.scheduler

    @Before
    fun setUp() {
        mockkObject(InAppReviewManager)
        every { InAppReviewManager.onTransactionSent() } returns Unit
        BootFlags.update(BootFlags.values.copy(txValidation = true))
        coEvery { accountRepo.getWallet(TestWallet.ID) } returns testWallet()
        coEvery { accountRepo.findAccount(TestWallet.ID, any(), any(), any()) } answers { accounts[secondArg<String>()] }
        coEvery { walletUnlocker.unlockWallet(any(), any()) } returns cryptoWallet
        coEvery { provider.transferPayload(any(), any(), any(), any(), any()) } answers {
            val account = firstArg<Account>()
            val amount = thirdArg<BigInteger>()
            built += amount
            builtMemos += arg<String?>(3)
            OpRes.ok(account.nativeTransfer(secondArg(), amount, arg(3)))
        }
        coEvery { provider.estimateFee(any<Payload>(), any(), any()) } answers {
            priced += firstArg<Payload>().meta
            pricedAs += secondArg<Account>()
            ChainRes.ok(FEE)
        }
        coEvery { provider.getNonce(any()) } returns NodeRes.ok(BigInteger.ONE)
        coEvery { provider.calculateGasReserve(any(), any(), any(), any(), any(), any()) } answers { NodeRes.ok(reserve) }
        val agreed = slot<Intent>()
        coEvery { provider.validateOperation(any(), any(), capture(agreed)) } answers {
            intents += agreed.captured
            ValidationRes.ok(Unit)
        }
        coEvery { provider.signPayload(any(), any(), any(), any(), any()) } answers {
            val payload = firstArg<Payload>()
            signed += payload.meta
            signedAs += secondArg<Account>()
            signedFees += thirdArg<Fee>()
            SignRes.ok(SignedPayload.signed(payload, "0xsigned"))
        }
        coEvery { provider.broadcastPayload(any(), any()) } returns NodeRes.ok("0xhash")
        coEvery { provider.reportPayloadSent(any(), any(), any(), any(), any()) } returns Unit
        coEvery { feeBuilder.buildSwapOperation(any(), any(), any(), any(), any(), any(), any(), any()) } answers {
            TxFeeState(energy = secondArg(), estimated = arg(6))
        }
        // No default fee stands in unless a test gives one: a fee the chain cannot price stays unpriced.
        coEvery { provider.defaultFee(any()) } returns ChainRes.err(ChainError.BadResponse(message = "no default"))
    }

    @After
    fun tearDown() {
        unmockkObject(InAppReviewManager, AnalyticsHelper.Companion)
    }

    @Test
    fun aTokenSendIsBuiltAsTheContractsTransferAndHeldToWhatTheUserAsked() {
        val feature = open(TestAssets.usdtEth.id, amount = AMOUNT)

        scheduler.runCurrent()

        val meta = priced.single() as PayloadMeta.Evm.Main
        assertEquals(USDT_CONTRACT, meta.to.display.lowercase())
        assertEquals(BigInteger.ZERO, meta.value)
        val intent = intents.single()
        assertEquals(TestAssets.usdtEth.id, intent.listed.single().asset.id)
        assertEquals(AMOUNT, intent.listed.single().amount)
        assertEquals(RECIPIENT, intent.to?.display)
        assertEquals(Intent.Source.Wallet, intent.source)
        assertEquals(AMOUNT, feature.amount.value)
        assertEquals(FEE.amount, feature.fee.value?.amount)
        assertNull(feature.confirmationError.value)
    }

    /** A send goes to the gasless relayer as the operation built for it, a transfer held to its recipient and amount. */
    @Test
    fun aSendIsPricedAsItsOperationForItsRecipient() {
        val payloads = slot<List<Payload>>()
        val sold = slot<BigInteger>()
        val purpose = slot<GaslessRelayer.Purpose>()
        coEvery { feeBuilder.buildSwapOperation(any(), any(), any(), capture(payloads), capture(sold), any(), any(), capture(purpose)) } answers {
            TxFeeState(energy = secondArg(), estimated = arg(6))
        }

        open(TestAssets.usdtEth.id, amount = AMOUNT)
        scheduler.runCurrent()

        assertEquals(priced.single(), payloads.captured.single().meta)
        assertEquals(AMOUNT, sold.captured)
        assertEquals(GaslessRelayer.Call.Type.Transfer, purpose.captured.type)
        assertEquals(RECIPIENT.lowercase(), purpose.captured.recipient?.display?.lowercase())
    }

    @Test
    fun aWalletWhose2faTurnedSigningOffIsRefusedBeforeThePasscode() {
        val feature = open(request(TestAssets.usdtEth.id, AMOUNT), signatureGate = SignatureGate { true })
        scheduler.runCurrent()

        feature.sendAction(OperationConfirmAction.Confirm)
        scheduler.runCurrent()

        assertEquals(ConfirmationError.SignatureDisabled, feature.confirmationError.value)
        assertTrue(signed.isEmpty())
        coVerify(exactly = 0) { walletUnlocker.unlockWallet(any(), any()) }
        coVerify(exactly = 0) { provider.broadcastPayload(any(), any()) }
        assertEquals(SendingState.None, feature.sendingState.value)
    }

    @Test
    fun swipingSignsAndSendsThePayloadAsBuilt() {
        val feature = open(TestAssets.usdtEth.id, amount = AMOUNT)
        scheduler.runCurrent()

        feature.sendAction(OperationConfirmAction.Confirm)
        scheduler.runCurrent()

        assertEquals(priced.single(), signed.single())
        // Priced and signed as the token's own account, as chain-kit signs an SPL transfer that names its mint.
        assertEquals(TestAssets.usdtEth.id, pricedAs.single().asset.id)
        assertEquals(TestAssets.usdtEth.id, signedAs.single().asset.id)
        coVerify { provider.broadcastPayload(match { (it.data as JsonPrimitive).content == "0xsigned" }, any()) }
        assertEquals(SendingState.Done, feature.sendingState.value)
        scheduler.advanceTimeBy(SUCCESS_HOLD_MS + 1)
    }

    @Test
    fun aWholeCoinBalanceSendsWhatIsLeftPastTheFee() {
        reserve = GasReserveResult(LEFT, isAmountAdjusted = true, error = null)
        val feature = open(TestAssets.eth.id, amount = BALANCE, isMax = true)

        scheduler.runCurrent()

        // Built for what was asked, then again for what is left once the fee is known; priced once, as
        // the transaction confirm priced it: the fee does not follow the amount.
        assertEquals(listOf(BALANCE, LEFT), built)
        assertEquals(listOf(BALANCE), priced.map { (it as PayloadMeta.Evm.Main).value })
        assertEquals(LEFT, feature.amount.value)
        assertEquals(BALANCE, intents.single().listed.single().amount)

        feature.sendAction(OperationConfirmAction.Confirm)
        scheduler.runCurrent()

        // What is left is signed with the fee it was fitted to: the two make the whole balance.
        assertEquals(LEFT, (signed.single() as PayloadMeta.Evm.Main).value)
        assertEquals(FEE, signedFees.single())
        scheduler.advanceTimeBy(SUCCESS_HOLD_MS + 1)
    }

    @Test
    fun aPrepareThatThrowsEndsTheRedEmulateAsUnknown() {
        val events = recordAnalytics()
        coEvery { provider.getNonce(any()) } throws IllegalStateException("node client broke")
        val feature = open(TestAssets.eth.id, amount = AMOUNT)
        scheduler.runCurrent()

        assertEquals(ConfirmationError.Unknown, feature.confirmationError.value)
        val terminal = events.single { (event, props) -> event == "op_terminal" && props["operation"] == "emulate" }.second
        assertEquals("fail", terminal["outcome"])
        assertEquals("emulate", terminal["stage"])
        assertEquals("unknown", terminal["error_type"])
        assertEquals("node client broke", terminal["error_message"])
    }

    @Test
    fun aSendReportsToRedWhatItSends() {
        val events = recordAnalytics()
        reserve = GasReserveResult(LEFT, isAmountAdjusted = true, error = null)
        val feature = open(TestAssets.eth.id, amount = BALANCE, isMax = true)
        scheduler.runCurrent()

        feature.sendAction(OperationConfirmAction.Confirm)
        scheduler.runCurrent()

        // What is left past the fee, as the transaction confirm reported it.
        val attempt = events.single { (event, props) -> event == "op_attempt" && props["operation"] == "send" }.second
        val metadata = JSONObject(attempt["other_metadata"] as String)
        val left = TestAssets.eth.value.toDisplayUnit(LEFT).value.doubleValue(exactRequired = false)
        assertEquals(left, metadata.getDouble("amount"), 0.0)
        assertEquals("coin", metadata.getString("fee_paid_in"))
        scheduler.advanceTimeBy(SUCCESS_HOLD_MS + 1)
    }

    @Test
    fun aRelayerSendsTheWholeCoinBalance() {
        reserve = GasReserveResult(LEFT, isAmountAdjusted = true, error = null)
        val shrinkToFit = slot<Boolean>()
        coEvery { feeBuilder.buildSwapOperation(any(), any(), any(), any(), any(), capture(shrinkToFit), any(), any()) } answers {
            val energy = secondArg<com.tonapps.wallet.data.multichain.account.AccountWithDetails>()
            val estimated = arg<Fee>(6)
            TxFeeState(
                energy = energy,
                estimated = estimated,
                options = listOf(battery(), TxFee(account = FeeAccount.Chain(energy), fee = estimated)),
                relayer = RelayerSend { _, _ -> "0xrelayed" },
            )
        }
        val feature = open(TestAssets.eth.id, amount = BALANCE, isMax = true)
        scheduler.runCurrent()

        assertTrue(shrinkToFit.captured)
        feature.pickFee(feature.feeMethods.value!!.options.first { it.viaRelayer })
        scheduler.runCurrent()

        assertEquals(BALANCE, feature.amount.value)
    }

    @Test
    fun theFindingsShownAreOfWhatThePickedMethodSigns() {
        val danger = ValidationError.ForeignRecipient("p_user", expected = "0xaa", actual = "0xbb")
        coEvery { provider.validateOperation(any(), any(), any()) } answers {
            if (firstArg<Operation>() == RELAYED) {
                ValidationRes.verdict(listOf(danger))
            } else {
                ValidationRes.ok(Unit)
            }
        }
        givenRelayedOperation()
        val feature = open(TestAssets.usdtEth.id, amount = AMOUNT)
        scheduler.runCurrent()

        feature.pickFee(feature.feeMethods.value!!.options.first { it.viaRelayer })
        scheduler.runCurrent()
        assertEquals(listOf(danger), feature.findings.value.map { it.error })

        feature.pickFee(feature.feeMethods.value!!.options.first { !it.viaRelayer })
        scheduler.runCurrent()
        assertTrue(feature.findings.value.isEmpty())
    }

    @Test
    fun theSwipeWaitsForTheFindingsOfWhatItSigns() {
        val read = CompletableDeferred<Unit>()
        coEvery { provider.validateOperation(any(), any(), any()) } coAnswers {
            if (firstArg<Operation>() == RELAYED) {
                read.await()
            }
            ValidationRes.ok(Unit)
        }
        givenRelayedOperation()
        val feature = open(TestAssets.usdtEth.id, amount = AMOUNT)
        scheduler.runCurrent()

        feature.pickFee(feature.feeMethods.value!!.options.first { it.viaRelayer })
        scheduler.runCurrent()
        assertTrue(feature.findingsReading.value)

        read.complete(Unit)
        scheduler.runCurrent()
        assertFalse(feature.findingsReading.value)
    }

    @Test
    fun aCheckOfTheRelayersOperationThatThrowsStillLetsTheSwipeGo() {
        coEvery { provider.validateOperation(any(), any(), any()) } answers {
            if (firstArg<Operation>() == RELAYED) {
                throw IllegalStateException("validator failed")
            }
            ValidationRes.ok(Unit)
        }
        givenRelayedOperation()
        val feature = open(TestAssets.usdtEth.id, amount = AMOUNT)
        scheduler.runCurrent()

        feature.pickFee(feature.feeMethods.value!!.options.first { it.viaRelayer })
        scheduler.runCurrent()

        assertFalse(feature.findingsReading.value)
        // Read as a validator answering Unknown: in the wallet's own flow it asks nothing of the user.
        val finding = feature.findings.value.single()
        assertTrue(finding.error is ValidationError.Unknown)
        assertEquals(FindingSeverity.Info, finding.severity)
    }

    @Test
    fun aValidatorThatThrowsOnWhatIsSignedStopsTheSend() {
        coEvery { provider.validateOperation(any(), any(), any()) } throws IllegalStateException("validator failed")
        val feature = open(TestAssets.usdtEth.id, amount = AMOUNT)
        scheduler.runCurrent()

        feature.sendAction(OperationConfirmAction.Confirm)
        scheduler.runCurrent()

        assertTrue(signed.isEmpty())
        coVerify(exactly = 0) { provider.broadcastPayload(any(), any()) }
    }

    @Test
    fun aCoinSendItsBalanceCannotPayForIsShortOnBalance() {
        reserve = GasReserveResult(AMOUNT, isAmountAdjusted = false, error = GasReserveResult.Error.InsufficientBalance)
        val feature = open(TestAssets.eth.id, amount = AMOUNT)

        scheduler.runCurrent()

        assertEquals(ConfirmationError.InsufficientBalance, feature.confirmationError.value)
    }

    @Test
    fun aTokenSendTheCoinCannotPayForNamesTheCoin() {
        reserve = GasReserveResult(AMOUNT, isAmountAdjusted = false, error = GasReserveResult.Error.InsufficientBalance)
        val feature = open(TestAssets.usdtEth.id, amount = AMOUNT)

        scheduler.runCurrent()

        assertEquals(ConfirmationError.InsufficientNetworkFee(ETH_SYMBOL), feature.confirmationError.value)
        // The coin is what to buy: the token itself is there. The fee alone is to be paid of it.
        val shortfall = feature.shortfall.value as OperationShortfall.Balance
        assertEquals(TestAssets.eth.id, shortfall.account.asset.id)
        assertEquals(FEE.amount, shortfall.required)
        assertTrue(shortfall.forFees)
        // What was priced stays on screen.
        assertEquals(FEE.amount, feature.fee.value?.amount)
        assertEquals(AMOUNT, feature.amount.value)
    }

    @Test
    fun aCoinSendShortOfItsBalanceOffersTheCoin() {
        reserve = GasReserveResult(AMOUNT, isAmountAdjusted = false, error = GasReserveResult.Error.InsufficientBalance)
        val feature = open(TestAssets.eth.id, amount = AMOUNT)

        scheduler.runCurrent()

        // To be paid: the amount and the fee, against the coin's balance.
        val shortfall = feature.shortfall.value as OperationShortfall.Balance
        assertEquals(TestAssets.eth.id, shortfall.account.asset.id)
        assertEquals(AMOUNT + FEE.amount, shortfall.required)
        assertFalse(shortfall.forFees)
        assertEquals(FEE.amount, feature.fee.value?.amount)
    }

    @Test
    fun aTokenSendOverItsBalanceTellsWhatIsToBePaidOfTheToken() {
        coEvery { provider.estimateFee(any<Payload>(), any(), any()) } returns ChainRes.err(ChainError.InsufficientFunds("over"))
        val feature = open(TestAssets.usdtEth.id, amount = OVER_TOKEN_BALANCE)

        scheduler.runCurrent()

        // The chain could not price it, so the fee shows as unavailable, as it did on the transaction confirm.
        assertTrue(feature.feeUnavailable.value)
        val shortfall = feature.shortfall.value as OperationShortfall.Balance
        assertEquals(TestAssets.usdtEth.id, shortfall.account.asset.id)
        assertEquals(OVER_TOKEN_BALANCE, shortfall.required)
        assertFalse(shortfall.forFees)
    }

    @Test
    fun aNonceTheNodeDoesNotGiveLeavesTheFeeOnScreen() {
        coEvery { provider.getNonce(any()) } returns NodeRes.err(NodeError.Rejected(code = 1, message = "busy"))
        val feature = open(TestAssets.eth.id, amount = AMOUNT)

        scheduler.runCurrent()

        assertEquals(ConfirmationError.NodeRejected("busy"), feature.confirmationError.value)
        assertEquals(FEE.amount, feature.fee.value?.amount)
        assertEquals(AMOUNT, feature.amount.value)
    }

    @Test
    fun aSendWhoseNonceAnotherTransactionTookIsNotSentAgain() {
        coEvery { provider.broadcastPayload(any(), any()) } returns NodeRes.err(NodeError.NonceTaken("nonce too low: next nonce 3, tx nonce 2"))
        val feature = open(TestAssets.eth.id, amount = AMOUNT)
        scheduler.runCurrent()

        feature.sendAction(OperationConfirmAction.Confirm)
        scheduler.runCurrent()

        assertEquals(ConfirmationError.NonceTaken, feature.confirmationError.value)

        // Neither a retry nor another swipe sends the signature the network never takes.
        feature.sendAction(OperationConfirmAction.Retry)
        scheduler.runCurrent()
        feature.sendAction(OperationConfirmAction.Confirm)
        scheduler.runCurrent()

        coVerify(exactly = 1) { provider.broadcastPayload(any(), any()) }
        assertEquals(1, signed.size)
        assertEquals(ConfirmationError.NonceTaken, feature.confirmationError.value)
        assertEquals(SendingState.None, feature.sendingState.value)
        // It may be out: the history the user is told to check is refreshed.
        coVerify { refreshSchedule.notifyTransactionSent(any(), any()) }
    }

    @Test
    fun aSendTheNodeRefusedIsSentAgainAsSignedOnRetry() {
        coEvery { provider.broadcastPayload(any(), any()) } returnsMany listOf(
            NodeRes.err(NodeError.Rejected(code = -32005, message = "rate limit exceeded")),
            NodeRes.ok("0xhash"),
        )
        val feature = open(TestAssets.eth.id, amount = AMOUNT)
        scheduler.runCurrent()

        feature.sendAction(OperationConfirmAction.Confirm)
        scheduler.runCurrent()

        assertEquals(ConfirmationError.NodeRejected("rate limit exceeded"), feature.confirmationError.value)

        feature.sendAction(OperationConfirmAction.Retry)
        scheduler.runCurrent()

        coVerify(exactly = 2) { provider.broadcastPayload(match { (it.data as JsonPrimitive).content == "0xsigned" }, any()) }
        assertEquals(1, signed.size)
        assertEquals(SendingState.Done, feature.sendingState.value)
        scheduler.advanceTimeBy(SUCCESS_HOLD_MS + 1)
    }

    @Test
    fun aTokenSendABatteryWouldPayForOffersTheRecharge() {
        reserve = GasReserveResult(AMOUNT, isAmountAdjusted = false, error = GasReserveResult.Error.InsufficientBalance)
        coEvery { feeBuilder.buildSwapOperation(any(), any(), any(), any(), any(), any(), any(), any()) } answers {
            val energy = secondArg<com.tonapps.wallet.data.multichain.account.AccountWithDetails>()
            val estimated = arg<Fee>(6)
            TxFeeState(
                energy = energy,
                estimated = estimated,
                // The charges fall short of what the relayer priced.
                options = listOf(battery(charges = BigInteger.ONE, fee = BigInteger.TEN), TxFee(account = FeeAccount.Chain(energy), fee = estimated, available = false)),
                relayer = RelayerSend { _, _ -> "0xrelayed" },
            )
        }
        val feature = open(TestAssets.usdtEth.id, amount = AMOUNT)

        scheduler.runCurrent()

        val shortfall = feature.shortfall.value as OperationShortfall.Fee
        assertEquals(TestAssets.eth.id, shortfall.coin.asset.id)
        assertTrue(shortfall.transfer)
    }

    @Test
    fun aFeeTheChainCannotPriceStillShowsWhatIsSent() {
        coEvery { provider.estimateFee(any<Payload>(), any(), any()) } returns ChainRes.err(ChainError.BadResponse(message = "down"))
        val feature = open(TestAssets.usdtEth.id, amount = AMOUNT)

        scheduler.runCurrent()

        assertTrue(feature.feeUnavailable.value)
        assertEquals(ConfirmationError.BadResponse, feature.confirmationError.value)
        assertEquals(TestAssets.usdtEth.id, feature.account.value?.asset?.id)
        assertEquals(AMOUNT, feature.asked.value?.amount)
        assertNull(feature.shortfall.value)
    }

    @Test
    fun theCoinATokensSendCarriesIsTheRelayersToAttach() {
        tokenGas = BALANCE + BigInteger.ONE
        coEvery { feeBuilder.buildSwapOperation(any(), any(), any(), any(), any(), any(), any(), any()) } answers {
            val energy = secondArg<com.tonapps.wallet.data.multichain.account.AccountWithDetails>()
            val estimated = arg<Fee>(6)
            TxFeeState(
                energy = energy,
                estimated = estimated,
                options = listOf(battery(), TxFee(account = FeeAccount.Chain(energy), fee = estimated)),
                relayer = RelayerSend { _, _ -> "0xrelayed" },
            )
        }
        val feature = open(TestAssets.usdtEth.id, amount = AMOUNT)

        scheduler.runCurrent()

        assertNull(feature.confirmationError.value)
        assertEquals(tokenGas, feature.feeMethods.value?.attachedEnergy)
    }

    @Test
    fun aCoinSendIsTheWalletsOwnWhicheverPaysTheFee() {
        val over = BALANCE + BigInteger.ONE
        reserve = GasReserveResult(over, isAmountAdjusted = false, error = GasReserveResult.Error.InsufficientBalance)
        coEvery { feeBuilder.buildSwapOperation(any(), any(), any(), any(), any(), any(), any(), any()) } answers {
            val energy = secondArg<com.tonapps.wallet.data.multichain.account.AccountWithDetails>()
            val estimated = arg<Fee>(6)
            // As the builder states a TON payment's value: attached to the message the relayer sends.
            TxFeeState(
                energy = energy,
                estimated = estimated,
                attachedEnergy = over,
                options = listOf(battery(), TxFee(account = FeeAccount.Chain(energy), fee = estimated)),
                relayer = RelayerSend { _, _ -> "0xrelayed" },
            )
        }
        val feature = open(TestAssets.eth.id, amount = over)

        scheduler.runCurrent()

        assertEquals(ConfirmationError.InsufficientNetworkFee(ETH_SYMBOL, BigDecimal("1.000000000000000001")), feature.confirmationError.value)
    }

    @Test
    fun theCoinATokensSendCarriesIsTheWalletsWithNoRelayer() {
        tokenGas = BALANCE + BigInteger.ONE
        val feature = open(TestAssets.usdtEth.id, amount = AMOUNT)

        scheduler.runCurrent()

        assertEquals(ConfirmationError.InsufficientNetworkFee(ETH_SYMBOL, BigDecimal("1.000300000000000001")), feature.confirmationError.value)
    }

    @Test
    fun aTonSendIsNotAskedOfTheRelayer() {
        coEvery { provider.estimateFee(any<Payload>(), any(), any()) } returns ChainRes.ok(Fee.Value(TestAssets.ton.value, BigInteger.fromInt(5_000_000)))
        givenRelayedOperation()

        open(tonRequest(encryptionKey = null))
        scheduler.runCurrent()

        // The battery would pay the TON sent as well as its gas: TON pays for it.
        coVerify(exactly = 0) { feeBuilder.buildSwapOperation(any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    /** A relayer would deliver the stand-in an encrypted comment is priced as: no relayer is asked for one. */
    @Ignore("A TON send is not relayed while the battery charges for the TON sent; only a TON send carries an encrypted comment")
    @Test
    fun anEncryptedCommentIsRelayedAsTheWalletEncryptsIt() {
        coEvery { provider.tonEmulationPayload(SECRET.length, any(), any()) } returns SignRes.ok(STAND_IN)
        coEvery { provider.encryptTonMemo(SECRET, any(), any(), any()) } returns SignRes.ok(ENCRYPTED)
        // A TON send is priced in TON.
        coEvery { provider.estimateFee(any<Payload>(), any(), any()) } answers {
            priced += firstArg<Payload>().meta
            ChainRes.ok(Fee.Value(TestAssets.ton.value, BigInteger.fromInt(5_000_000)))
        }
        val asked = mutableListOf<List<Payload>>()
        coEvery { feeBuilder.buildSwapOperation(any(), any(), any(), capture(asked), any(), any(), any(), any()) } answers {
            val energy = secondArg<com.tonapps.wallet.data.multichain.account.AccountWithDetails>()
            TxFeeState(
                energy = energy,
                estimated = arg(6),
                options = listOf(battery(), TxFee(account = FeeAccount.Chain(energy), fee = arg(6))),
                relayer = RelayerSend { _, _ -> "relayed" },
            )
        }
        val feature = open(tonRequest(encryptionKey = RECIPIENT_KEY))
        scheduler.runCurrent()
        feature.pickFee(feature.feeMethods.value!!.options.first { it.viaRelayer })
        scheduler.runCurrent()

        feature.sendAction(OperationConfirmAction.Confirm)
        scheduler.runCurrent()

        // Priced as a stand-in of its size, then, once unlocked, asked of the relayer as the wallet encrypted it.
        assertEquals(STAND_IN, (asked.first().single().meta as PayloadMeta.Ton.Main).body)
        assertEquals(ENCRYPTED, (asked.last().single().meta as PayloadMeta.Ton.Main).body)
        assertTrue(signed.isEmpty())
        scheduler.advanceTimeBy(SUCCESS_HOLD_MS + 1)
    }

    @Test
    fun anEncryptedCommentIsPricedAsAStandInAndEncryptedOnceUnlocked() {
        coEvery { provider.tonEmulationPayload(SECRET.length, any(), any()) } returns SignRes.ok(STAND_IN)
        coEvery { provider.encryptTonMemo(SECRET, any(), any(), any()) } returns SignRes.ok(ENCRYPTED)
        val feature = open(tonRequest(encryptionKey = RECIPIENT_KEY))
        scheduler.runCurrent()

        // Built with no plain comment: its place is held by a stand-in of its size.
        assertEquals(listOf<String?>(null), builtMemos)
        val standIn = priced.single() as PayloadMeta.Ton.Main
        assertEquals(STAND_IN, standIn.body)
        assertNull(standIn.comment)

        feature.sendAction(OperationConfirmAction.Confirm)
        scheduler.runCurrent()

        val sent = signed.single() as PayloadMeta.Ton.Main
        assertEquals(ENCRYPTED, sent.body)
        assertNull(sent.comment)
        // The encrypted send is held to the intent again before it is signed, but costs what its stand-in did:
        // not priced again, and signed with the fee and nonce the screen had.
        assertEquals(2, intents.size)
        assertEquals(1, priced.size)
        coVerify(exactly = 1) { provider.getNonce(any()) }
        assertEquals(FEE, signedFees.single())
        scheduler.advanceTimeBy(SUCCESS_HOLD_MS + 1)
    }

    @Test
    fun aDangerOnlyTheEncryptedSendMakesStopsIt() {
        coEvery { provider.tonEmulationPayload(any(), any(), any()) } returns SignRes.ok(STAND_IN)
        coEvery { provider.encryptTonMemo(any(), any(), any(), any()) } returns SignRes.ok(ENCRYPTED)
        val danger = ValidationError.ForeignRecipient("0", expected = TON_RECIPIENT, actual = "0:bb")
        coEvery { provider.validateOperation(any(), any(), any()) } answers {
            val payload = firstArg<com.tonapps.chainkit.core.op.Operation>().payloads.single().meta as PayloadMeta.Ton.Main
            if (payload.body == ENCRYPTED) {
                ValidationRes.verdict(listOf(danger))
            } else {
                ValidationRes.ok(Unit)
            }
        }
        val feature = open(tonRequest(encryptionKey = RECIPIENT_KEY))
        scheduler.runCurrent()

        feature.sendAction(OperationConfirmAction.Confirm)
        scheduler.runCurrent()

        assertTrue(signed.isEmpty())
        coVerify(exactly = 0) { provider.broadcastPayload(any(), any()) }
        assertEquals(ConfirmationError.VerificationFailed, feature.confirmationError.value)
    }

    @Test
    fun onlyTheWalletsOwnSendConfirmsAsAnOperation() {
        features.set(WalletFeatureKey.OPERATION_TRANSFER_ENABLED, true)
        assertTrue(request(TestAssets.eth.id, AMOUNT).confirmsAsTransferOperation())
        assertTrue(request(TestAssets.eth.id, AMOUNT, encryptionKey = "00").confirmsAsTransferOperation())
        assertFalse(request(TestAssets.eth.id, AMOUNT, context = dapp()).confirmsAsTransferOperation())

        features.set(WalletFeatureKey.OPERATION_TRANSFER_ENABLED, false)
        assertFalse(request(TestAssets.eth.id, AMOUNT).confirmsAsTransferOperation())
    }

    private fun open(assetId: String, amount: BigInteger, isMax: Boolean = false) = open(request(assetId, amount, isMax))

    private fun open(request: ConfirmRequest, signatureGate: SignatureGate = SignatureGate { false }) = TransferConfirmFeature(
        request = request,
        provider = provider,
        accountRepo = accountRepo,
        walletUnlocker = walletUnlocker,
        signatureGate = signatureGate,
        postTransactionRefreshSchedule = refreshSchedule,
        timeProvider = FakeTimeProvider(),
        feeBuilder = feeBuilder,
        settingsRepository = settingsRepository,
    )

    private fun request(
        assetId: String,
        amount: BigInteger,
        isMax: Boolean = false,
        encryptionKey: String? = null,
        context: ConfirmContext? = null,
    ) = ConfirmRequest(
        data = CommonTransactionData(assetId = assetId, walletId = TestWallet.ID),
        type = ConfirmType.Transfer(
            operation = ConfirmOperations.transfer(chain = Chain.Ethereum.Mainnet, to = RECIPIENT, amount = amount),
            isMax = isMax,
            commentEncryptionKey = encryptionKey,
        ),
        context = context,
    )

    private fun tonRequest(encryptionKey: String?) = ConfirmRequest(
        data = CommonTransactionData(assetId = TestAssets.ton.id, walletId = TestWallet.ID),
        type = ConfirmType.Transfer(
            operation = ConfirmOperations.transfer(chain = Chain.Ton.Mainnet, to = TON_RECIPIENT, amount = AMOUNT, comment = SECRET),
            commentEncryptionKey = encryptionKey,
        ),
    )

    private fun dapp() = ConfirmContext.Dapp(requestId = "1", topic = "topic", name = "dapp", url = "https://dapp.example")

    // ConfirmAnalytics reads AnalyticsHelper.Default when the feature is built: call before open().
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

    // A battery option the relayer has priced, with [RELAYED] as what it would sign.
    private fun givenRelayedOperation() {
        coEvery { feeBuilder.buildSwapOperation(any(), any(), any(), any(), any(), any(), any(), any()) } answers {
            val energy = secondArg<com.tonapps.wallet.data.multichain.account.AccountWithDetails>()
            val estimated = arg<Fee>(6)
            val battery = battery()
            TxFeeState(
                energy = energy,
                estimated = estimated,
                options = listOf(battery, TxFee(account = FeeAccount.Chain(energy), fee = estimated)),
                relayer = RelayerSend { _, _ -> "0xrelayed" },
                quotedId = battery.id,
                relayedOperation = RELAYED,
            )
        }
    }

    private fun battery(charges: BigInteger = BigInteger.fromInt(100), fee: BigInteger = BigInteger.ONE) = TxFee(
        account = FeeAccount.Keeper(balance = charges, rate = null),
        fee = Fee.Value(Asset.Battery(Chain.Ethereum.Mainnet), fee),
    )

    // What chain-kit builds: the coin paid to the recipient, or the token contract's transfer.
    private fun Account.nativeTransfer(to: Address, amount: BigInteger, memo: String?): PayloadMeta = when {
        chain is Chain.Ton -> PayloadMeta.Ton.Main(to = to, value = amount, comment = memo)
        asset is Asset.Token -> PayloadMeta.Evm.Main(
            to = Address.force(USDT_CONTRACT, Chain.Ethereum.Mainnet),
            value = tokenGas,
            data = "0xa9059cbb${amount.toString(16)}",
        )
        else -> PayloadMeta.Evm.Main(to = to, value = amount)
    }

    private companion object {
        const val RECIPIENT = "0x3B4D794a66304F130a4Db8F2551B0070dfCf5ca7"
        const val USDT_CONTRACT = "0xdac17f958d2ee523a2206206994597c13d831ec7"
        const val TON_RECIPIENT = "EQAFpdzyZmOwfi02U_8iUA6xxo7wsTJtoy0N6rf25Q9N9KWQ"
        const val RECIPIENT_KEY = "66c63e4ab095d55a21d148972b691efcc16d6e757b28aa52c9ac56b54ccefa4d"
        const val SECRET = "secret"
        const val STAND_IN = "te6standin"
        const val ENCRYPTED = "te6encrypted"
        val AMOUNT: BigInteger = BigInteger.fromInt(5_000_000)
        val BALANCE: BigInteger = BigInteger.parseString("1000000000000000000")
        val FEE = Fee.Value(Chain.Ethereum.Mainnet.toAsset(), BigInteger.parseString("300000000000000"))
        val LEFT: BigInteger = BALANCE - FEE.amount

        // What a relayer would sign for a battery-paid send: its own operation, not the screen's.
        val RELAYED = Operation.build(
            id = "relayed",
            payloads = listOf(
                Payload("p_user", Payload.Type.Eip7821HermesDelegate, Chain.Ethereum.Mainnet, Payload.VERSION, null, PayloadMeta.Evm.HermesDelegate(typedData = "{}")),
            ),
        )
        val OVER_TOKEN_BALANCE: BigInteger = BigInteger.fromInt(100_000_001)
        val ETH_SYMBOL: String = Chain.Ethereum.Mainnet.toAsset().symbol
    }
}
