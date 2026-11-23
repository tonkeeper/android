package com.tonapps.perps.funding

import com.ionspin.kotlin.bignum.integer.BigInteger as ChainInteger
import com.tonapps.blockchain.model.CommonTransactionData
import com.tonapps.blockchain.model.ConfirmRequest
import com.tonapps.blockchain.model.ConfirmType
import com.tonapps.bus.generated.Events.Perps.PerpsSourceScreen
import com.tonapps.chainkit.core.chain.api.models.ChainRes
import com.tonapps.chainkit.core.chain.api.models.NodeRes
import com.tonapps.chainkit.core.chain.api.models.SignRes
import com.tonapps.chainkit.core.chain.model.account.Account
import com.tonapps.chainkit.core.chain.model.account.Chain
import com.tonapps.chainkit.core.chain.model.account.MnemonicWallet
import com.tonapps.chainkit.core.chain.model.transaction.Fee
import com.tonapps.chainkit.core.op.Payload
import com.tonapps.chainkit.core.op.SignedPayload
import com.tonapps.chainkit.core.op.ValidationRes
import com.tonapps.deposit.multicoin.screens.confirm.base.SendingState
import com.tonapps.deposit.multicoin.screens.confirm.base.SignatureGate
import com.tonapps.deposit.multicoin.screens.confirm.base.operation.OperationConfirmAction
import com.tonapps.deposit.multicoin.screens.confirm.fee.FeeAccount
import com.tonapps.deposit.multicoin.screens.confirm.fee.TxFee
import com.tonapps.deposit.multicoin.screens.confirm.fee.TxFeeState
import com.tonapps.deposit.multicoin.screens.confirm.perpetual.PerpetualDepositConfirmFeature
import com.tonapps.perps.funding.PerpsFundingTestEnv.Companion.DEPOSIT_EXECUTION_ID
import com.tonapps.perps.funding.PerpsFundingTestEnv.Companion.GET_ACCOUNT
import com.tonapps.perps.funding.PerpsFundingTestEnv.Companion.EVM_NONCE
import com.tonapps.perps.funding.PerpsFundingTestEnv.Companion.GATEWAY
import com.tonapps.perps.funding.PerpsFundingTestEnv.Companion.L1_ADDRESS
import com.tonapps.perps.funding.PerpsFundingTestEnv.Companion.QUOTE_DEPOSIT
import com.tonapps.perps.funding.PerpsFundingTestEnv.Companion.RELAY_DEPOSITORY
import com.tonapps.perps.funding.PerpsFundingTestEnv.Companion.USDC
import com.tonapps.perps.funding.PerpsFundingTestEnv.Companion.USDC_TOKEN
import com.tonapps.perps.funding.PerpsFundingTestEnv.Companion.WALLET_ID
import com.tonapps.wallet.data.multichain.tx.PostTransactionRefreshSchedule
import com.tonapps.wallet.data.passcode.WalletUnlocker
import com.tonapps.wallet.testing.BaseUnitTest
import com.tonapps.wallet.testing.TestAssets
import com.tonapps.wallet.testing.TestWallet
import com.tonapps.wallet.testing.testAccount
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigInteger

/**
 * A deposit from the backend's quote to the swipe: the operation that was quoted is the one priced,
 * signed payload by payload and broadcast to the chain, and the backend hears of nothing in between.
 * The backend is [PerpsFundingTestEnv]'s fake, the chain a mocked provider; the service, the quote
 * reader, the confirm feature and the sender it broadcasts through are real.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PerpsDepositConfirmFlowTest : BaseUnitTest() {

    private val walletUnlocker = mockk<WalletUnlocker>()
    private val refreshSchedule = mockk<PostTransactionRefreshSchedule>(relaxed = true)
    private val priced = mutableListOf<String>()
    private val signed = mutableListOf<Pair<String, Long>>()
    private val broadcast = mutableListOf<Pair<Account, String>>()

    private lateinit var env: PerpsFundingTestEnv

    @Before
    fun setUp() {
        env = PerpsFundingTestEnv()
        coEvery { walletUnlocker.unlockWallet(any(), any()) } returns mockk<MnemonicWallet>()
        coEvery { env.provider.estimateFee(any<Payload>(), any(), any()) } answers {
            val payload = firstArg<Payload>()
            priced += payload.id
            // As chainkit prices them: a signature the relayer submits costs the sender no gas.
            val fee = when (payload.type) {
                Payload.Type.Eip7702Authorization, Payload.Type.Eip7821HermesDelegate -> ChainInteger.ZERO
                else -> ChainInteger.parseString(PAYLOAD_FEE)
            }
            ChainRes.ok(Fee.Value(Chain.Ethereum.Mainnet.toAsset(), fee))
        }
        coEvery { env.provider.signPayload(any(), any(), any(), any(), any()) } answers {
            val payload = firstArg<Payload>()
            signed += payload.id to arg<ChainInteger>(NONCE_ARGUMENT).longValue()
            when (payload.type) {
                Payload.Type.Eip7702Authorization -> SignRes.ok(
                    SignedPayload(
                        payloadId = payload.id,
                        type = SignedPayload.Type.Eip7702Authorization,
                        chain = payload.chain,
                        data = buildJsonObject {
                            put("y_parity", 1)
                            put("r", "0x" + "ab".repeat(32))
                            put("s", "0x" + "cd".repeat(32))
                        },
                    ),
                )
                Payload.Type.Eip7821HermesDelegate -> SignRes.ok(
                    SignedPayload(
                        payloadId = payload.id,
                        type = SignedPayload.Type.Eip7821HermesDelegate,
                        chain = payload.chain,
                        data = JsonPrimitive("0x" + "ef".repeat(64) + "1b"),
                    ),
                )
                else -> SignRes.ok(SignedPayload.signed(payload, "0xsigned-${payload.id}"))
            }
        }
        coEvery { env.provider.reportPayloadSent(any(), any(), any(), any(), any()) } returns Unit
        coEvery { env.provider.validateOperation(any(), any(), any()) } returns ValidationRes.ok(Unit)
        coEvery { env.provider.broadcastPayload(any(), any()) } answers {
            broadcast += secondArg<Account>() to (firstArg<SignedPayload>().data as JsonPrimitive).content
            NodeRes.ok("0xhash-${broadcast.size}")
        }
    }

    @Test
    fun whatTheBackendQuotedIsWhatIsSignedAndBroadcast() = runTest {
        env.quoteDeposits(provider = "direct", credited = AMOUNT, executionId = null) {
            listOf(
                env.evmPayload("approve", to = USDC_TOKEN, data = env.approveData(GATEWAY, AMOUNT)),
                env.evmPayload("deposit", to = GATEWAY, data = env.gatewayDepositData(L1_ADDRESS, AMOUNT)),
            )
        }
        val request = env.depositService.confirmRequest(WALLET_ID, env.source(USDC.id), AMOUNT, PerpsSourceScreen.PerpsHome)
        assertEquals(listOf("approve", "deposit"), priced)
        priced.clear()

        val feature = PerpetualDepositConfirmFeature(
            request = request,
            provider = env.provider,
            accountRepo = env.mcAccountRepository,
            walletUnlocker = walletUnlocker,
            signatureGate = SignatureGate { false },
            postTransactionRefreshSchedule = refreshSchedule,
            timeProvider = env.serverTime,
            tracker = null,
            authorizer = env.depositService,
            depositFeeMethods = env.feeMethods,
        )
        runCurrent()

        assertEquals(listOf("approve", "deposit"), priced)
        assertNotNull(feature.fee.value)
        assertTrue(broadcast.isEmpty())

        feature.sendAction(OperationConfirmAction.Confirm)
        runCurrent()

        // The approve and the deposit are two transactions of one account, a nonce apart.
        assertEquals(listOf("approve" to EVM_NONCE, "deposit" to EVM_NONCE + 1), signed)
        // Each goes to the chain as it was signed, the approve ahead of the deposit that spends it.
        assertEquals(listOf("0xsigned-approve", "0xsigned-deposit"), broadcast.map { it.second })
        broadcast.forEach { (account, _) ->
            assertEquals(TestWallet.ETH_ADDRESS, account.address.display)
            assertEquals(Chain.Ethereum.Mainnet, account.chain)
        }
        assertNull(feature.confirmationError.value)
        assertEquals(SendingState.Done, feature.sendingState.value)
        verify(exactly = 1) { refreshSchedule.notifyTransactionSent(WALLET_ID, Chain.Ethereum.Mainnet) }
        // Quoting is all the backend was asked for: the deposit reaches it through the chain.
        assertEquals(listOf(GET_ACCOUNT, QUOTE_DEPOSIT), env.endpoints)
    }

    @Test
    fun aPreviewPaidFromAnotherChainOpensPricedAsItsSignatures() = runTest {
        env.accounts[TestAssets.ton.id] = testAccount(TestAssets.ton, balance = TON_AMOUNT.toString())
        env.withoutAccount()
        env.quoteDeposits(provider = "relay", credited = AMOUNT, executionId = DEPOSIT_EXECUTION_ID) {
            listOf(env.authorizationPayload("authorization"), env.hermesPayload("execute"))
        }
        val request = env.depositService.confirmRequest(WALLET_ID, env.source(TestAssets.ton.id), TON_AMOUNT, PerpsSourceScreen.PerpsHome)

        val feature = PerpetualDepositConfirmFeature(
            request = request,
            provider = env.provider,
            accountRepo = env.mcAccountRepository,
            walletUnlocker = walletUnlocker,
            signatureGate = SignatureGate { false },
            postTransactionRefreshSchedule = refreshSchedule,
            timeProvider = env.serverTime,
            tracker = null,
            authorizer = env.depositService,
            depositFeeMethods = env.feeMethods,
        )
        runCurrent()

        // The deposit pays from TON and its signatures are on eth/mainnet: priced all the same, at no gas.
        assertNull(feature.confirmationError.value)
        assertEquals(listOf("authorization", "execute"), priced)
        assertEquals("0", feature.fee.value?.amount.toString())
    }

    @Test
    fun aPreviewShowsItsEstimateAndIsAuthorizedOnTheSwipe() = runTest {
        answerPaymentsAsTheConfirmScreenAsks()
        env.withoutAccount()
        val previewed = { asked: JsonObject -> "operation" !in asked && "change_pub_key" in asked }
        env.quoteDeposits(
            provider = "relay",
            credited = AMOUNT,
            executionId = DEPOSIT_EXECUTION_ID,
        ) { asked ->
            if (previewed(asked)) {
                listOf(env.hermesPayload("execute"))
            } else {
                listOf(env.evmPayload("pay", to = RELAY_DEPOSITORY, value = ETH_AMOUNT, data = env.nativeDepositData(), authorizationNonce = EVM_NONCE + 1))
            }
        }
        val request = env.depositService.confirmRequest(WALLET_ID, env.source(TestAssets.eth.id), ETH_AMOUNT, PerpsSourceScreen.PerpsHome)
        assertEquals(listOf("pay"), priced)
        priced.clear()

        val feature = PerpetualDepositConfirmFeature(
            request = request,
            provider = env.provider,
            accountRepo = env.mcAccountRepository,
            walletUnlocker = walletUnlocker,
            signatureGate = SignatureGate { false },
            postTransactionRefreshSchedule = refreshSchedule,
            timeProvider = env.serverTime,
            tracker = null,
            authorizer = env.depositService,
            depositFeeMethods = env.feeMethods,
        )
        runCurrent()

        assertEquals(listOf("execute", "pay"), priced)
        assertEquals(PAYLOAD_FEE, feature.fee.value?.amount.toString())
        assertNull(feature.confirmationError.value)
        assertEquals(listOf(GET_ACCOUNT, QUOTE_DEPOSIT, QUOTE_DEPOSIT), env.endpoints)
        assertTrue(feature.delegatesAddress)

        feature.sendAction(OperationConfirmAction.Confirm)
        runCurrent()

        // The swipe signs the preview, quotes again with it, and signs and sends what that answered.
        assertEquals(listOf(GET_ACCOUNT, QUOTE_DEPOSIT, QUOTE_DEPOSIT, QUOTE_DEPOSIT), env.endpoints)
        assertEquals(listOf("execute", "pay", "pay"), priced)
        assertEquals(listOf("execute", "pay"), signed.map { it.first })
        assertEquals(EVM_NONCE, signed.last().second)
        assertEquals(listOf("0xsigned-pay"), broadcast.map { it.second })
        assertNull(feature.confirmationError.value)
        assertEquals(SendingState.Done, feature.sendingState.value)
    }

    @Test
    fun aPreviewThatCreditsLessOnTheSwipeShowsTheNewDepositAndTheNextSwipeSendsIt() = runTest {
        answerPaymentsAsTheConfirmScreenAsks()
        env.withoutAccount()
        val previewed = { asked: JsonObject -> "operation" !in asked && "change_pub_key" in asked }
        val quoteFirstDeposit = { credited: BigInteger ->
            env.quoteDeposits(provider = "relay", credited = credited, executionId = DEPOSIT_EXECUTION_ID) { asked ->
                if (previewed(asked)) {
                    listOf(env.hermesPayload("execute"))
                } else {
                    listOf(env.evmPayload("pay", to = RELAY_DEPOSITORY, value = ETH_AMOUNT, data = env.nativeDepositData(), authorizationNonce = EVM_NONCE + 1))
                }
            }
        }
        quoteFirstDeposit(AMOUNT)
        val request = env.depositService.confirmRequest(WALLET_ID, env.source(TestAssets.eth.id), ETH_AMOUNT, PerpsSourceScreen.PerpsHome)
        val lowered = AMOUNT * BigInteger.valueOf(98L) / BigInteger.valueOf(100L)
        quoteFirstDeposit(lowered)

        val feature = PerpetualDepositConfirmFeature(
            request = request,
            provider = env.provider,
            accountRepo = env.mcAccountRepository,
            walletUnlocker = walletUnlocker,
            signatureGate = SignatureGate { false },
            postTransactionRefreshSchedule = refreshSchedule,
            timeProvider = env.serverTime,
            tracker = null,
            authorizer = env.depositService,
            depositFeeMethods = env.feeMethods,
        )
        runCurrent()

        feature.sendAction(OperationConfirmAction.Confirm)
        runCurrent()

        assertTrue(broadcast.isEmpty())
        assertEquals(listOf("execute"), signed.map { it.first })
        assertTrue(feature.quoteChanged.value)
        assertNull(feature.confirmationError.value)
        assertEquals(SendingState.None, feature.sendingState.value)
        assertEquals(lowered.toString(), feature.deposit.value.quote.depositAmount.toString())
        assertEquals("pay", feature.deposit.value.operation.payloads.single().id)
        assertEquals(PAYLOAD_FEE, feature.fee.value?.amount.toString())

        feature.sendAction(OperationConfirmAction.Confirm)
        runCurrent()

        assertFalse(feature.quoteChanged.value)
        assertTrue(feature.delegatesAddress)
        assertEquals(listOf(GET_ACCOUNT, QUOTE_DEPOSIT, QUOTE_DEPOSIT, QUOTE_DEPOSIT), env.endpoints)
        assertEquals(listOf("execute", "pay"), signed.map { it.first })
        assertEquals(listOf("0xsigned-pay"), broadcast.map { it.second })
        assertNull(feature.confirmationError.value)
        assertEquals(SendingState.Done, feature.sendingState.value)
    }

    private fun answerPaymentsAsTheConfirmScreenAsks() {
        coEvery { env.feeMethods.build(any(), any(), any(), any()) } answers {
            val deposit = firstArg<ConfirmRequest>().type as ConfirmType.PerpetualDeposit
            val native = TxFeeState(energy = thirdArg(), estimated = arg(3))
            if (deposit.payableByRelayer) {
                native
            } else {
                native.copy(options = listOf(TxFee(account = FeeAccount.Chain(thirdArg()), fee = arg(3))))
            }
        }
        every { env.feeMethods.payment(any()) } answers {
            val preview = firstArg<ConfirmRequest>()
            val deposit = preview.type as ConfirmType.PerpetualDeposit
            deposit.paymentOperation?.let { payment ->
                ConfirmRequest(
                    data = CommonTransactionData(
                        assetId = preview.data.assetId,
                        walletId = preview.data.walletId,
                        expiresAt = preview.data.expiresAt,
                    ),
                    type = deposit.copy(operation = payment, paymentOperation = null),
                )
            }
        }
    }

    private companion object {
        val ETH_AMOUNT: BigInteger = BigInteger("10000000000000000")
        val TON_AMOUNT: BigInteger = BigInteger.valueOf(10_000_000_000L)
        const val PAYLOAD_FEE = "300000000000000"
        const val NONCE_ARGUMENT = 3
        val AMOUNT: BigInteger = BigInteger.valueOf(5_000_000L)
    }
}
