package com.tonapps.perps.funding

import com.ionspin.kotlin.bignum.decimal.BigDecimal as ChainDecimal
import com.ionspin.kotlin.bignum.integer.BigInteger as ChainInteger
import com.tonapps.blockchain.model.ConfirmRequest
import com.tonapps.blockchain.model.ConfirmType
import com.tonapps.bus.generated.Events.Perps.PerpsSourceScreen
import com.tonapps.chainkit.core.chain.api.models.ChainError
import com.tonapps.chainkit.core.chain.api.models.ChainRes
import com.tonapps.chainkit.core.chain.api.models.SignRes
import com.tonapps.chainkit.core.chain.model.account.Asset
import com.tonapps.chainkit.core.chain.model.account.Chain
import com.tonapps.chainkit.core.chain.model.account.MnemonicWallet
import com.tonapps.chainkit.core.chain.model.transaction.Transaction
import com.tonapps.chainkit.core.chain.model.transaction.Fee
import com.tonapps.chainkit.core.op.Operation
import com.tonapps.chainkit.core.op.Payload
import com.tonapps.chainkit.core.op.PayloadMeta
import com.tonapps.chainkit.core.op.SignedPayload
import com.tonapps.deposit.multicoin.screens.confirm.base.ConfirmationError
import com.tonapps.deposit.multicoin.screens.confirm.base.ConfirmationErrorCause
import com.tonapps.deposit.multicoin.screens.confirm.base.operation.QuoteChangedException
import com.tonapps.deposit.multicoin.screens.confirm.fee.FeeAccount
import com.tonapps.deposit.multicoin.screens.confirm.fee.TxFee
import com.tonapps.deposit.multicoin.screens.confirm.fee.TxFeeState
import com.tonapps.perps.data.LIGHTER_API_KEY_INDEX
import com.tonapps.perps.data.LIGHTER_NIL_ACCOUNT_INDEX
import com.tonapps.perps.funding.PerpsFundingTestEnv.Companion.CHANGE_PUB_KEY_CONTRACT
import com.tonapps.perps.funding.PerpsFundingTestEnv.Companion.DEPOSIT_EXECUTION_ID
import com.tonapps.perps.funding.PerpsFundingTestEnv.Companion.DEPOSIT_OPERATION_ID
import com.tonapps.perps.funding.PerpsFundingTestEnv.Companion.EVM_NONCE
import com.tonapps.perps.funding.PerpsFundingTestEnv.Companion.GATEWAY
import com.tonapps.perps.funding.PerpsFundingTestEnv.Companion.GET_ACCOUNT
import com.tonapps.perps.funding.PerpsFundingTestEnv.Companion.HERMES_DEADLINE_SECONDS
import com.tonapps.perps.funding.PerpsFundingTestEnv.Companion.HERMES_DELEGATE
import com.tonapps.perps.funding.PerpsFundingTestEnv.Companion.HERMES_NONCE
import com.tonapps.perps.funding.PerpsFundingTestEnv.Companion.HTTP_BAD_REQUEST
import com.tonapps.perps.funding.PerpsFundingTestEnv.Companion.HTTP_UNAVAILABLE
import com.tonapps.perps.funding.PerpsFundingTestEnv.Companion.L1_ADDRESS
import com.tonapps.perps.funding.PerpsFundingTestEnv.Companion.QUOTE_DEPOSIT
import com.tonapps.perps.funding.PerpsFundingTestEnv.Companion.QUOTE_TTL_MS
import com.tonapps.perps.funding.PerpsFundingTestEnv.Companion.RELAY_DEPOSITORY
import com.tonapps.perps.funding.PerpsFundingTestEnv.Companion.TRON_RELAY_DEPOSITORY
import com.tonapps.perps.funding.PerpsFundingTestEnv.Companion.TRADING_PUBLIC_KEY
import com.tonapps.perps.funding.PerpsFundingTestEnv.Companion.USDC
import com.tonapps.perps.funding.PerpsFundingTestEnv.Companion.USDC_TOKEN
import com.tonapps.perps.funding.PerpsFundingTestEnv.Companion.WALLET_ID
import com.tonapps.wallet.data.multichain.fees.NetworkFees
import com.tonapps.wallet.testing.BaseUnitTest
import com.tonapps.wallet.testing.TestAssets
import com.tonapps.wallet.testing.TestWallet
import com.tonapps.wallet.testing.testAccount
import com.tonapps.wallet.testing.testAsset
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.math.BigDecimal
import java.math.BigInteger

/**
 * A deposit as the perps backend sees it: one quote, whose operation becomes the confirm request,
 * and nothing else. The backend is [PerpsFundingTestEnv]'s fake; everything between an HTTP answer
 * and the request the confirm screen opens with is real.
 */
class PerpsDepositServiceTest : BaseUnitTest() {

    private lateinit var env: PerpsFundingTestEnv

    @Before
    fun setUp() {
        env = PerpsFundingTestEnv()
    }

    @Test
    fun aDepositAsksTheBackendForAQuoteAndNothingElse() = runTest {
        quoteRelayedDeposit()

        env.depositService.confirmRequest(WALLET_ID, env.source(TestAssets.eth.id), ETH_AMOUNT, PerpsSourceScreen.PerpsHome)

        assertEquals(listOf(GET_ACCOUNT, QUOTE_DEPOSIT), env.endpoints)
        val asked = env.quoteBodies.single()
        assertEquals(TestAssets.eth.id, asked.string("source_asset"))
        assertEquals(ETH_AMOUNT.toString(), asked.string("source_amount"))
        assertEquals(TestWallet.ETH_ADDRESS, asked.string("sender_address"))
        // The account credited is the wallet's own Ethereum address, whatever chain pays.
        assertEquals(L1_ADDRESS, asked.string("destination_address"))
        // Nothing is recorded for a quote: no journal entry, no deposit opened on the backend.
        assertTrue(env.journal.list(WALLET_ID).isEmpty())
    }

    @Test
    fun theConfirmRequestCarriesTheOperationTheQuoteCameWith() = runTest {
        quoteRelayedDeposit()
        // The device clock runs a day ahead: the deadline is set on the server's.
        env.serverTime.offsetMs = -DAY_MS
        val quotedAt = env.serverTime.currentTimeMillis()

        val request = env.depositService.confirmRequest(WALLET_ID, env.source(TestAssets.eth.id), ETH_AMOUNT, PerpsSourceScreen.PerpsHome)

        assertEquals(TestAssets.eth.id, request.data.assetId)
        assertEquals(WALLET_ID, request.data.walletId)
        val deposit = request.type as ConfirmType.PerpetualDeposit
        assertEquals(DEPOSIT_OPERATION_ID, deposit.operation.id)
        val payload = deposit.operation.payloads.single()
        assertEquals("pay", payload.id)
        assertEquals(Chain.Ethereum.Mainnet, payload.chain)
        val meta = payload.meta as PayloadMeta.Evm.Main
        assertTrue(meta.to.display.equals(RELAY_DEPOSITORY, ignoreCase = true))
        assertEquals(ETH_AMOUNT.toString(), meta.value.toString())
        assertEquals(ETH_AMOUNT.toString(), deposit.amount.toString())
        // TK-4608: a minute to sign on the server's clock from when it is quoted, not the quote's own deadline.
        assertTrue(checkNotNull(request.data.expiresAt) in quotedAt + MINUTE_MS..env.serverTime.currentTimeMillis() + MINUTE_MS)
        // A deposit that swaps on the way shows what the quote credits.
        assertEquals(CREDITED.toString(), deposit.minReceive.toString())
        assertEquals(ESTIMATED_DURATION_SECONDS.toLong(), deposit.estimatedDurationSeconds)
    }

    @Test
    fun aQuoteWithoutAnEstimateOpensWithNoArrivalTime() = runTest {
        quoteRelayedDeposit(estimatedDurationSeconds = null)

        val request = env.depositService.confirmRequest(WALLET_ID, env.source(TestAssets.eth.id), ETH_AMOUNT, PerpsSourceScreen.PerpsHome)

        assertNull((request.type as ConfirmType.PerpetualDeposit).estimatedDurationSeconds)
    }

    @Test
    fun theRequestComesBackFromNavigationWithTheBackendsOperation() = runTest {
        quoteRelayedDeposit()
        val request = env.depositService.confirmRequest(WALLET_ID, env.source(TestAssets.eth.id), ETH_AMOUNT, PerpsSourceScreen.PerpsHome)

        val restored = Json.decodeFromString<ConfirmRequest>(Json.encodeToString(request))

        val sent = request.type as ConfirmType.PerpetualDeposit
        val deposit = restored.type as ConfirmType.PerpetualDeposit
        assertEquals(sent.operation.id, deposit.operation.id)
        assertEquals(sent.operation.payloads, deposit.operation.payloads)
        assertEquals(request.data.expiresAt, restored.data.expiresAt)
        assertEquals(sent.minReceive, deposit.minReceive)
        assertEquals(sent.estimatedDurationSeconds, deposit.estimatedDurationSeconds)
    }

    @Test
    fun aDirectDepositApprovesThenCallsTheGatewayAndNamesNoMinimum() = runTest {
        quoteDirectDeposit(USDC_AMOUNT)

        val request = env.depositService.confirmRequest(WALLET_ID, env.source(USDC.id), USDC_AMOUNT, PerpsSourceScreen.PerpsHome)

        val deposit = request.type as ConfirmType.PerpetualDeposit
        assertEquals(listOf("approve", "deposit"), deposit.operation.payloads.map { it.id })
        assertEquals(USDC_AMOUNT.toString(), deposit.amount.toString())
        // Credited one to one: there is nothing a minimum would tell.
        assertNull(deposit.minReceive)
        assertEquals(listOf(GET_ACCOUNT, QUOTE_DEPOSIT), env.endpoints)
    }

    @Test
    fun aTokenDepositOverTheBalanceIsRefusedAfterTheQuote() = runTest {
        val overBalance = BigInteger.valueOf(200_000_000L)
        quoteDirectDeposit(overBalance)

        val failure = failureOf { env.depositService.confirmRequest(WALLET_ID, env.source(USDC.id), overBalance, PerpsSourceScreen.PerpsHome) }

        assertTrue(failure is PerpsDepositException.InsufficientBalance)
        assertEquals(PerpsDepositQuoteError.InsufficientBalance, failure.toDepositQuoteError())
    }

    @Test
    fun aBatchedDepositIsSignedAsTheBackendBuiltIt() = runTest {
        quoteBatchedDeposit(authorizationNonce = EVM_NONCE + 1)

        val request = env.depositService.confirmRequest(WALLET_ID, env.source(USDC.id), USDC_AMOUNT, PerpsSourceScreen.PerpsHome)

        val payload = (request.type as ConfirmType.PerpetualDeposit).operation.payloads.single()
        val meta = payload.meta as PayloadMeta.Evm.Main
        assertTrue(meta.to.display.equals(TestWallet.ETH_ADDRESS, ignoreCase = true))
        assertEquals(directBatchData(), meta.data)
        val authorization = checkNotNull(meta.authorization)
        assertTrue(authorization.address.display.equals(HERMES_DELEGATE, ignoreCase = true))
        assertEquals((EVM_NONCE + 1).toString(), authorization.nonce.toString())
    }

    @Test
    fun aTokenDepositFoldedIntoAHermesBatchOnItsOwnChainIsQuoted() = runTest {
        env.quoteDeposits(provider = "relay", credited = CREDITED, executionId = DEPOSIT_EXECUTION_ID) {
            listOf(arbitrumBatch(authorizationChainId = ARBITRUM_CHAIN_ID))
        }

        val request = env.depositService.confirmRequest(WALLET_ID, arbitrumSource(), ETH_AMOUNT, PerpsSourceScreen.PerpsHome)

        val delegation = checkNotNull(((request.type as ConfirmType.PerpetualDeposit).operation.payloads.single().meta as PayloadMeta.Evm.Main).authorization)
        assertEquals(ARBITRUM_CHAIN_ID, delegation.chainId)
    }

    @Test
    fun aDepositWhosePaymentDelegatesAnywhereButTheSignedHermesIsNoQuote() = runTest {
        val payments: Map<String, Pair<() -> Unit, () -> JsonObject>> = mapOf(
            "a delegation to another contract" to Pair(
                {},
                { env.evmPayload("pay", to = RELAY_DEPOSITORY, value = ETH_AMOUNT, data = env.nativeDepositData(), authorizationNonce = EVM_NONCE + 1, authorizationAddress = OTHER_DELEGATE) },
            ),
            "a chain the signed block leaves out" to Pair(
                {},
                {
                    env.evmPayload(
                        "pay",
                        to = RELAY_DEPOSITORY,
                        value = ETH_AMOUNT,
                        authorizationNonce = EVM_NONCE + 1,
                        authorizationChainId = BSC_CHAIN_ID,
                        chain = "bsc/mainnet",
                    )
                },
            ),
            "another chain's id" to Pair({}, { arbitrumBatch(authorizationChainId = Chain.Ethereum.Mainnet.number) }),
            "the nonce the payment spends" to Pair(
                {},
                { env.evmPayload("pay", to = RELAY_DEPOSITORY, value = ETH_AMOUNT, data = env.nativeDepositData(), authorizationNonce = 0) },
            ),
            "no verified security block" to Pair(
                { env.servedSecurityBlock = null },
                { env.evmPayload("pay", to = RELAY_DEPOSITORY, value = ETH_AMOUNT, data = env.nativeDepositData(), authorizationNonce = EVM_NONCE + 1) },
            ),
        )

        payments.forEach { (case, setup) ->
            val (prepare, payment) = setup
            env = PerpsFundingTestEnv()
            prepare()
            env.quoteDeposits(provider = "relay", credited = CREDITED, executionId = DEPOSIT_EXECUTION_ID) { listOf(payment()) }

            val failure = failureOf {
                env.depositService.confirmRequest(WALLET_ID, env.source(TestAssets.eth.id), ETH_AMOUNT, PerpsSourceScreen.PerpsHome)
            }

            assertEquals(case, PerpsDepositQuoteError.QuoteFailed, failure.toDepositQuoteError())
        }
    }

    @Test
    fun aRelayedDepositWithoutTheSecurityBlockIsNoQuote() = runTest {
        env.servedSecurityBlock = null
        quoteRelayedDeposit()

        val failure = failureOf { env.depositService.confirmRequest(WALLET_ID, env.source(TestAssets.eth.id), ETH_AMOUNT, PerpsSourceScreen.PerpsHome) }

        assertEquals(PerpsDepositQuoteError.QuoteFailed, failure.toDepositQuoteError())
    }

    @Test
    fun aFirstDepositOfUsdcOnEthereumActivatesTheAccountWithoutAPreview() = runTest {
        quoteBatchedDeposit(authorizationNonce = EVM_NONCE + 1, accountUninitialized = true)

        val request = env.depositService.confirmRequest(WALLET_ID, env.source(USDC.id), USDC_AMOUNT, PerpsSourceScreen.PerpsHome)

        assertFalse(request.data.preview)
        assertTrue((request.type as ConfirmType.PerpetualDeposit).activatesAccount)
    }

    @Test
    fun aDepositTheVenueCannotPlaceActivatesNothing() = runTest {
        quoteBatchedDeposit(authorizationNonce = EVM_NONCE + 1, accountUninitialized = null)

        val request = env.depositService.confirmRequest(WALLET_ID, env.source(USDC.id), USDC_AMOUNT, PerpsSourceScreen.PerpsHome)

        assertFalse((request.type as ConfirmType.PerpetualDeposit).activatesAccount)
    }

    @Test
    fun aQuoteWithoutAnOperationIdCannotBeSigned() = runTest {
        env.quoteDeposits(provider = "relay", credited = CREDITED, executionId = DEPOSIT_EXECUTION_ID, operationId = null) {
            listOf(env.evmPayload("pay", to = RELAY_DEPOSITORY, value = ETH_AMOUNT, data = env.nativeDepositData()))
        }

        val failure = failureOf { env.depositService.confirmRequest(WALLET_ID, env.source(TestAssets.eth.id), ETH_AMOUNT, PerpsSourceScreen.PerpsHome) }

        assertEquals(PerpsDepositQuoteError.QuoteFailed, failure.toDepositQuoteError())
    }

    @Test
    fun aQuoteTheBackendRefusesTellsTheAmountScreenWhy() = runTest {
        val source = env.source(TestAssets.eth.id)

        env.answer(QUOTE_DEPOSIT) { env.refused(HTTP_BAD_REQUEST, code = "quote_refused", reason = "amount_too_low") }
        val tooLow = failureOf { env.depositService.confirmRequest(WALLET_ID, source, ETH_AMOUNT, PerpsSourceScreen.PerpsHome) }
        assertEquals(PerpsDepositQuoteError.BelowMinimum(minUsd = null), tooLow.toDepositQuoteError())

        env.answer(QUOTE_DEPOSIT) {
            env.refused(HTTP_BAD_REQUEST, code = "quote_refused", reason = "amount_too_low", minUsdAmount = "2.53")
        }
        val tooLowPriced = failureOf { env.depositService.confirmRequest(WALLET_ID, source, ETH_AMOUNT, PerpsSourceScreen.PerpsHome) }
        assertEquals(PerpsDepositQuoteError.BelowMinimum(minUsd = BigDecimal("2.53")), tooLowPriced.toDepositQuoteError())

        env.answer(QUOTE_DEPOSIT) { env.refused(HTTP_BAD_REQUEST, code = "quote_refused", reason = "no_route") }
        val noRoute = failureOf { env.depositService.confirmRequest(WALLET_ID, source, ETH_AMOUNT, PerpsSourceScreen.PerpsHome) }
        assertEquals(PerpsDepositQuoteError.QuoteFailed, noRoute.toDepositQuoteError())

        env.answer(QUOTE_DEPOSIT) { env.refused(HTTP_BAD_REQUEST, code = "validation_error", reason = "insufficient_balance") }
        val unfunded = failureOf { env.depositService.confirmRequest(WALLET_ID, source, ETH_AMOUNT, PerpsSourceScreen.PerpsHome) }
        assertEquals(PerpsDepositQuoteError.InsufficientBalance, unfunded.toDepositQuoteError())

        env.answer(QUOTE_DEPOSIT) { env.refused(HTTP_UNAVAILABLE, code = "unavailable", reason = null, retryable = true) }
        val unavailable = failureOf { env.depositService.confirmRequest(WALLET_ID, source, ETH_AMOUNT, PerpsSourceScreen.PerpsHome) }
        assertEquals(PerpsDepositQuoteError.ServiceUnavailable, unavailable.toDepositQuoteError())

        env.answer(QUOTE_DEPOSIT) { throw IOException("offline") }
        val offline = failureOf { env.depositService.confirmRequest(WALLET_ID, source, ETH_AMOUNT, PerpsSourceScreen.PerpsHome) }
        assertEquals(PerpsDepositQuoteError.ServiceUnavailable, offline.toDepositQuoteError())
    }

    @Test
    fun aFirstRelayedDepositOpensAsItsPreviewWithoutThePasscode() = runTest {
        quoteFirstRelayedDeposit()

        val request = env.depositService.confirmRequest(WALLET_ID, env.source(TestAssets.eth.id), ETH_AMOUNT, PerpsSourceScreen.PerpsHome)

        assertEquals(listOf(GET_ACCOUNT, QUOTE_DEPOSIT, QUOTE_DEPOSIT), env.endpoints)
        assertEquals(listOf(true, false), env.quoteBodies.map { it.registersKey() })
        val body = env.quoteBodies.first()
        val asked = body.getValue("change_pub_key").jsonObject
        assertEquals(LIGHTER_NIL_ACCOUNT_INDEX, asked.getValue("account_index").jsonPrimitive.long)
        assertEquals(LIGHTER_API_KEY_INDEX, asked.getValue("api_key_index").jsonPrimitive.int)
        // The key kept at the wallet's creation, read without the passcode.
        assertEquals(TRADING_PUBLIC_KEY, asked.string("pub_key"))
        assertNull(body["operation"])
        val deposit = request.type as ConfirmType.PerpetualDeposit
        assertTrue(request.data.preview)
        assertEquals(listOf("execute"), deposit.operation.payloads.map { it.id })
        assertEquals(ETH_AMOUNT.toString(), deposit.amount.toString())
        assertEquals(CREDITED.toString(), deposit.minReceive.toString())
        assertEquals(listOf("pay"), deposit.paymentOperation?.payloads?.map { it.id })
        assertFalse(deposit.payableByRelayer)
    }

    @Test
    fun aFirstDepositOffAnotherChainIsAlsoQuotedForItsPaymentWithoutTheKey() = runTest {
        val source = arbitrumSource()
        env.withoutAccount()
        env.quoteDeposits(provider = "relay", credited = CREDITED, executionId = DEPOSIT_EXECUTION_ID) { asked ->
            if ("operation" !in asked && asked.registersKey()) {
                listOf(env.authorizationPayload("authorization"), env.hermesPayload("execute"))
            } else {
                listOf(arbitrumPayment())
            }
        }

        val request = env.depositService.confirmRequest(WALLET_ID, source, ETH_AMOUNT, PerpsSourceScreen.PerpsHome)

        assertEquals(listOf(true, false), env.quoteBodies.map { it.registersKey() })
        assertTrue(request.data.preview)
        val deposit = request.type as ConfirmType.PerpetualDeposit
        assertEquals(listOf("authorization", "execute"), deposit.operation.payloads.map { it.id })
        assertEquals(listOf("pay"), deposit.paymentOperation?.payloads?.map { it.id })
    }

    private fun JsonObject.registersKey(): Boolean = get("change_pub_key")?.let { it !is JsonNull } == true

    @Test
    fun aDepositToAnAccountThatExistsNamesNoKey() = runTest {
        quoteRelayedDeposit()

        val request = env.depositService.confirmRequest(WALLET_ID, env.source(TestAssets.eth.id), ETH_AMOUNT, PerpsSourceScreen.PerpsHome)
        env.depositService.confirmRequest(WALLET_ID, env.source(TestAssets.eth.id), ETH_AMOUNT, PerpsSourceScreen.PerpsHome)

        assertTrue(env.quoteBodies.none { it.registersKey() })
        assertEquals(listOf(GET_ACCOUNT, QUOTE_DEPOSIT, QUOTE_DEPOSIT), env.endpoints)
        assertFalse(request.data.preview)
        assertFalse((request.type as ConfirmType.PerpetualDeposit).activatesAccount)
    }

    @Test
    fun aPreviewTheClientDidNotAskForIsNoQuoteAndNothingIsSigned() = runTest {
        env.quoteDeposits(provider = "relay", credited = CREDITED, executionId = DEPOSIT_EXECUTION_ID) {
            listOf(env.authorizationPayload("authorization"), env.hermesPayload("execute"))
        }

        val failure = failureOf {
            env.depositService.confirmRequest(WALLET_ID, env.source(TestAssets.eth.id), ETH_AMOUNT, PerpsSourceScreen.PerpsHome)
        }

        assertEquals(PerpsDepositQuoteError.QuoteFailed, failure.toDepositQuoteError())
        assertFalse(env.quoteBodies.single().registersKey())
        coVerify(exactly = 0) { env.provider.signPayload(any(), any(), any(), any(), any()) }
    }

    @Test
    fun anAccountTheBackendCannotTellAboutIsNoQuote() = runTest {
        quoteFirstRelayedDeposit()
        env.answer(GET_ACCOUNT) { env.refused(HTTP_UNAVAILABLE, code = "unavailable", reason = null, retryable = true) }

        val failure = failureOf {
            env.depositService.confirmRequest(WALLET_ID, env.source(TestAssets.eth.id), ETH_AMOUNT, PerpsSourceScreen.PerpsHome)
        }

        assertEquals(PerpsDepositQuoteError.ServiceUnavailable, failure.toDepositQuoteError())
        assertTrue(env.quoteBodies.isEmpty())
    }

    @Test
    fun aWalletWithoutAKeptKeyIsNoQuoteAndNeverUnlocks() = runTest {
        env.withoutKeptKey()
        quoteFirstRelayedDeposit()

        val failure = failureOf {
            env.depositService.confirmRequest(WALLET_ID, env.source(TestAssets.eth.id), ETH_AMOUNT, PerpsSourceScreen.PerpsHome)
        }

        assertTrue(failure is IllegalStateException)
        assertTrue(env.quoteBodies.isEmpty())
    }

    @Test
    fun anAuthorizedPreviewIsQuotedAgainWithTheOwnersSignatures() = runTest {
        signAuthPayloads()
        quoteFirstRelayedDeposit(previewPayloads = { previewWith(env.hermesDocument()) }, payment = { listOf(arbitrumPayment()) })
        val previewed = env.depositService.confirmRequest(WALLET_ID, arbitrumSource(), ETH_AMOUNT, PerpsSourceScreen.PerpsHome)

        val request = env.depositService.authorize(previewed, mockk<MnemonicWallet>())

        assertEquals(listOf(GET_ACCOUNT, QUOTE_DEPOSIT, QUOTE_DEPOSIT, QUOTE_DEPOSIT), env.endpoints)
        val (preview, _, signed) = env.quoteBodies
        // The second ask is the same request plus the preview's operation, signed.
        assertEquals(preview, JsonObject(signed - "operation"))
        val operation = signed.getValue("operation").jsonObject
        assertEquals(DEPOSIT_OPERATION_ID, operation.string("id"))
        val (authorization, execute) = operation.getValue("payloads").jsonArray.map { it.jsonObject }
        assertEquals("eip7702/authorization/signed", authorization.string("type"))
        assertEquals("eth/mainnet", authorization.string("chain_id"))
        assertEquals(1, authorization.getValue("version").jsonPrimitive.int)
        assertEquals("authorization", authorization.string("payload_id"))
        val authorizationData = authorization.getValue("meta").jsonObject.getValue("data").jsonObject
        assertEquals(AUTHORIZATION_Y_PARITY, authorizationData.getValue("y_parity").jsonPrimitive.int)
        assertEquals(AUTHORIZATION_R, authorizationData.string("r"))
        assertEquals(AUTHORIZATION_S, authorizationData.string("s"))
        assertEquals("eip7821/hermes/delegate/signed", execute.string("type"))
        assertEquals(1, execute.getValue("version").jsonPrimitive.int)
        assertEquals("execute", execute.string("payload_id"))
        assertEquals(EXECUTE_SIGNATURE, execute.getValue("meta").jsonObject.string("data"))

        // Both are signed by the key behind destination_address.
        coVerify(exactly = 2) {
            env.provider.signPayload(any(), match { it.chain == Chain.Ethereum.Mainnet }, any(), any(), any())
        }
        // What is signed and sent is the deposit, not the preview.
        val deposit = request.type as ConfirmType.PerpetualDeposit
        assertFalse(request.data.preview)
        assertEquals("pay", deposit.operation.payloads.single().id)
        assertEquals(DEPOSIT_EXECUTION_ID, deposit.quote.executionId)
        assertTrue((previewed.type as ConfirmType.PerpetualDeposit).activatesAccount)
        assertTrue(deposit.activatesAccount)
    }

    @Test
    fun aFirstDepositFromEthereumSignsTheExecuteAloneAndSendsThePaymentThatDelegates() = runTest {
        signAuthPayloads()
        quoteFirstRelayedDeposit(
            payment = { listOf(env.evmPayload("pay", to = RELAY_DEPOSITORY, value = ETH_AMOUNT, data = env.nativeDepositData(), authorizationNonce = EVM_NONCE + 1)) },
        )
        val previewed = env.depositService.confirmRequest(WALLET_ID, env.source(TestAssets.eth.id), ETH_AMOUNT, PerpsSourceScreen.PerpsHome)

        val request = env.depositService.authorize(previewed, mockk<MnemonicWallet>())

        assertEquals(listOf(GET_ACCOUNT, QUOTE_DEPOSIT, QUOTE_DEPOSIT, QUOTE_DEPOSIT), env.endpoints)
        val (preview, _, signed) = env.quoteBodies
        assertEquals(preview, JsonObject(signed - "operation"))
        val execute = signed.getValue("operation").jsonObject.getValue("payloads").jsonArray.single().jsonObject
        assertEquals("eip7821/hermes/delegate/signed", execute.string("type"))
        assertEquals("execute", execute.string("payload_id"))
        assertEquals(EXECUTE_SIGNATURE, execute.getValue("meta").jsonObject.string("data"))
        coVerify(exactly = 1) { env.provider.signPayload(any(), any(), any(), any(), any()) }
        val pay = (request.type as ConfirmType.PerpetualDeposit).operation.payloads.single()
        val delegation = checkNotNull((pay.meta as PayloadMeta.Evm.Main).authorization)
        assertTrue(delegation.address.display.equals(HERMES_DELEGATE, ignoreCase = true))
        assertEquals(Chain.Ethereum.Mainnet.number, delegation.chainId)
        assertFalse(request.data.preview)
        assertFalse((request.type as ConfirmType.PerpetualDeposit).payableByRelayer)
    }

    @Test
    fun aFirstDepositFromEthereumWhosePaymentDelegatesAnywhereButHermesIsNotSent() = runTest {
        foreignPayments().forEach { (case, payment) ->
            env = PerpsFundingTestEnv()
            signAuthPayloads()
            quoteFirstRelayedDeposit(signedPayment = payment)
            val previewed = env.depositService.confirmRequest(WALLET_ID, env.source(TestAssets.eth.id), ETH_AMOUNT, PerpsSourceScreen.PerpsHome)

            val failure = failureOf { env.depositService.authorize(previewed, mockk<MnemonicWallet>()) }

            assertEquals(case, PerpsDepositQuoteError.QuoteFailed, failure.toDepositQuoteError())
            coVerify(exactly = 1) { env.provider.signPayload(match { it.id == "execute" }, any(), any(), any(), any()) }
        }
    }

    @Test
    fun aFirstDepositFromEthereumPricedOnAPaymentThatDelegatesAnywhereButHermesIsNoQuote() = runTest {
        foreignPayments().forEach { (case, payment) ->
            env = PerpsFundingTestEnv()
            signAuthPayloads()
            quoteFirstRelayedDeposit(payment = payment)

            val failure = failureOf {
                env.depositService.confirmRequest(WALLET_ID, env.source(TestAssets.eth.id), ETH_AMOUNT, PerpsSourceScreen.PerpsHome)
            }

            assertEquals(case, PerpsDepositQuoteError.QuoteFailed, failure.toDepositQuoteError())
            coVerify(exactly = 0) { env.provider.signPayload(any(), any(), any(), any(), any()) }
        }
    }

    @Test
    fun aFirstDepositFromArbitrumWhosePaymentFoldsIntoAHermesBatchIsSent() = runTest {
        signAuthPayloads()
        quoteFirstRelayedDeposit(
            previewPayloads = { previewWith(env.hermesDocument()) },
            payment = { listOf(arbitrumBatch(authorizationChainId = ARBITRUM_CHAIN_ID)) },
        )
        val previewed = env.depositService.confirmRequest(WALLET_ID, arbitrumSource(), ETH_AMOUNT, PerpsSourceScreen.PerpsHome)

        val request = env.depositService.authorize(previewed, mockk<MnemonicWallet>())

        val delegation = checkNotNull(((request.type as ConfirmType.PerpetualDeposit).operation.payloads.single().meta as PayloadMeta.Evm.Main).authorization)
        assertEquals(ARBITRUM_CHAIN_ID, delegation.chainId)
        assertTrue((request.type as ConfirmType.PerpetualDeposit).payableByRelayer)
    }

    @Test
    fun aPreviewAnsweredWithAnotherPreviewIsNoQuote() = runTest {
        signAuthPayloads()
        env.withoutAccount()
        env.quoteDeposits(provider = "relay", credited = CREDITED, executionId = null) {
            listOf(env.hermesPayload("execute"))
        }
        val previewed = env.depositService.confirmRequest(WALLET_ID, env.source(TestAssets.eth.id), ETH_AMOUNT, PerpsSourceScreen.PerpsHome)

        val failure = failureOf { env.depositService.authorize(previewed, mockk<MnemonicWallet>()) }

        assertEquals(PerpsDepositQuoteError.QuoteFailed, failure.toDepositQuoteError())
        assertEquals(listOf(GET_ACCOUNT, QUOTE_DEPOSIT, QUOTE_DEPOSIT, QUOTE_DEPOSIT), env.endpoints)
    }

    @Test
    fun aPreviewQuotedAgainForUpToOnePercentLessIsTheDepositToSend() = runTest {
        signAuthPayloads()
        quoteFirstRelayedDeposit()
        val previewed = env.depositService.confirmRequest(WALLET_ID, env.source(TestAssets.eth.id), ETH_AMOUNT, PerpsSourceScreen.PerpsHome)
        quoteFirstRelayedDeposit(credited = CREDITED * 99.toBigInteger() / 100.toBigInteger())

        val request = env.depositService.authorize(previewed, mockk<MnemonicWallet>())

        assertFalse(request.data.preview)
    }

    @Test
    fun aPreviewQuotedAgainForMoreThanOnePercentLessComesBackAsTheNewDepositToConfirm() = runTest {
        signAuthPayloads()
        quoteFirstRelayedDeposit()
        val previewed = env.depositService.confirmRequest(WALLET_ID, env.source(TestAssets.eth.id), ETH_AMOUNT, PerpsSourceScreen.PerpsHome)
        val lowered = CREDITED * 98.toBigInteger() / 100.toBigInteger()
        quoteFirstRelayedDeposit(credited = lowered)

        val failure = failureOf { env.depositService.authorize(previewed, mockk<MnemonicWallet>()) }

        val changed = (failure as QuoteChangedException).request
        val deposit = changed.type as ConfirmType.PerpetualDeposit
        assertFalse(changed.data.preview)
        assertEquals("pay", deposit.operation.payloads.single().id)
        assertEquals(lowered.toString(), deposit.quote.depositAmount.toString())
        assertEquals(lowered.toString(), deposit.minReceive.toString())
        assertEquals(ETH_AMOUNT.toString(), deposit.amount.toString())
        assertFalse(deposit.payableByRelayer)
        assertEquals(listOf(GET_ACCOUNT, QUOTE_DEPOSIT, QUOTE_DEPOSIT, QUOTE_DEPOSIT), env.endpoints)
    }

    @Test
    fun aPreviewTheBackendRefusesOnceSignedAsTooLowSaysItsMinimum() = runTest {
        signAuthPayloads()
        quoteFirstRelayedDeposit()
        val previewed = env.depositService.confirmRequest(WALLET_ID, env.source(TestAssets.eth.id), ETH_AMOUNT, PerpsSourceScreen.PerpsHome)
        env.answer(QUOTE_DEPOSIT) {
            env.refused(HTTP_BAD_REQUEST, code = "validation_error", reason = "amount_too_low", minUsdAmount = "3.51")
        }

        val failure = failureOf { env.depositService.authorize(previewed, mockk<MnemonicWallet>()) }

        assertEquals(ConfirmationError.DepositBelowMinimum(BigDecimal("3.51")), (failure as ConfirmationErrorCause).confirmationError)
    }

    @Test
    fun aPreviewTheBackendRefusesOnceSignedForAnotherReasonIsNotCalledTooLow() = runTest {
        signAuthPayloads()
        quoteFirstRelayedDeposit()
        val previewed = env.depositService.confirmRequest(WALLET_ID, env.source(TestAssets.eth.id), ETH_AMOUNT, PerpsSourceScreen.PerpsHome)
        env.answer(QUOTE_DEPOSIT) { env.refused(HTTP_BAD_REQUEST, code = "quote_refused", reason = "no_route") }

        val failure = failureOf { env.depositService.authorize(previewed, mockk<MnemonicWallet>()) }

        assertFalse(failure is ConfirmationErrorCause)
        assertEquals(PerpsDepositQuoteError.QuoteFailed, failure.toDepositQuoteError())
    }

    @Test
    fun aDepositQuotedAgainIsTheSameDepositAtTheNewPrice() = runTest {
        quoteFirstRelayedDeposit()
        val previewed = env.depositService.confirmRequest(WALLET_ID, env.source(TestAssets.eth.id), ETH_AMOUNT, PerpsSourceScreen.PerpsHome)
        val lowered = CREDITED * 98.toBigInteger() / 100.toBigInteger()
        quoteFirstRelayedDeposit(credited = lowered)

        val requoted = env.depositService.requote(previewed)

        val deposit = requoted.type as ConfirmType.PerpetualDeposit
        assertTrue(requoted.data.preview)
        assertEquals(ETH_AMOUNT.toString(), deposit.amount.toString())
        assertEquals(lowered.toString(), deposit.quote.depositAmount.toString())
        assertEquals(PerpsSourceScreen.PerpsHome, deposit.sourceScreen)
    }

    @Test
    fun aDepositQuotedAgainBelowItsMinimumSaysTheMinimum() = runTest {
        quoteFirstRelayedDeposit()
        val previewed = env.depositService.confirmRequest(WALLET_ID, env.source(TestAssets.eth.id), ETH_AMOUNT, PerpsSourceScreen.PerpsHome)
        env.answer(QUOTE_DEPOSIT) {
            env.refused(HTTP_BAD_REQUEST, code = "validation_error", reason = "amount_too_low", minUsdAmount = "3.72")
        }

        val failure = failureOf { env.depositService.requote(previewed) }

        assertEquals(ConfirmationError.DepositBelowMinimum(BigDecimal("3.72")), (failure as ConfirmationErrorCause).confirmationError)
    }

    @Test
    fun aDepositThatCannotBeQuotedAgainOfflineSaysTheNetworkFailed() = runTest {
        quoteFirstRelayedDeposit()
        val previewed = env.depositService.confirmRequest(WALLET_ID, env.source(TestAssets.eth.id), ETH_AMOUNT, PerpsSourceScreen.PerpsHome)
        env.answer(QUOTE_DEPOSIT) { throw IOException("offline") }

        val failure = failureOf { env.depositService.requote(previewed) }

        assertEquals(ConfirmationError.NetworkError, (failure as ConfirmationErrorCause).confirmationError)
    }

    @Test
    fun aDepositWhoseAccountCannotBeReadOfflineSaysTheNetworkFailed() = runTest {
        quoteFirstRelayedDeposit()
        val previewed = env.depositService.confirmRequest(WALLET_ID, env.source(TestAssets.eth.id), ETH_AMOUNT, PerpsSourceScreen.PerpsHome)
        env.answer(GET_ACCOUNT) { throw IOException("offline") }

        val failure = failureOf { env.depositService.requote(previewed) }

        assertEquals(ConfirmationError.NetworkError, (failure as ConfirmationErrorCause).confirmationError)
    }

    @Test
    fun aDepositQuotedAgainThatLeavesNothingForTheNetworkFeeSaysWhichCoinIsShort() = runTest {
        quoteRelayedDeposit()
        val quoted = env.depositService.confirmRequest(WALLET_ID, env.source(TestAssets.eth.id), ETH_AMOUNT, PerpsSourceScreen.PerpsHome)
        priceEveryPayloadAt(ONE_ETH)

        val failure = failureOf { env.depositService.requote(quoted) }

        assertEquals(
            ConfirmationError.InsufficientNetworkFee("ETH", BigDecimal("1.01")),
            (failure as ConfirmationErrorCause).confirmationError,
        )
    }

    @Test
    fun aPreviewQuotedAgainOnAnotherRouteIsNoQuote() = runTest {
        signAuthPayloads()
        quoteFirstRelayedDeposit()
        val previewed = env.depositService.confirmRequest(WALLET_ID, env.source(TestAssets.eth.id), ETH_AMOUNT, PerpsSourceScreen.PerpsHome)
        env.quoteDeposits(provider = "direct", credited = CREDITED, executionId = null) {
            listOf(env.evmPayload("pay", to = RELAY_DEPOSITORY, value = ETH_AMOUNT, data = env.nativeDepositData()))
        }

        val failure = failureOf { env.depositService.authorize(previewed, mockk<MnemonicWallet>()) }

        assertEquals(PerpsDepositQuoteError.QuoteFailed, failure.toDepositQuoteError())
    }

    @Test
    fun anAuthorizedPreviewSignsTheExecuteTheClientBuiltFromTheCheckedFields() = runTest {
        signAuthPayloads()
        quoteFirstRelayedDeposit(
            previewPayloads = {
                val document = env.hermesDocument(domainType = DOMAIN_TYPE)
                val message = JsonObject(document.getValue("message").jsonObject + ("note" to JsonPrimitive("unsigned")))
                listOf(
                    env.authorizationPayload("authorization"),
                    env.hermesPayload("execute", JsonObject(document + ("message" to message) + ("extra" to JsonPrimitive(1)))),
                )
            },
            payment = { listOf(arbitrumPayment()) },
        )
        val previewed = env.depositService.confirmRequest(WALLET_ID, arbitrumSource(), ETH_AMOUNT, PerpsSourceScreen.PerpsHome)

        env.depositService.authorize(previewed, mockk<MnemonicWallet>())

        val signed = mutableListOf<Payload>()
        coVerify { env.provider.signPayload(capture(signed), any(), any(), any(), any()) }
        val authorization = signed.single { it.type == Payload.Type.Eip7702Authorization }.meta as PayloadMeta.Evm.Authorization
        assertTrue(authorization.address.display.equals(HERMES_DELEGATE, ignoreCase = true))
        val execute = signed.single { it.type == Payload.Type.Eip7821HermesDelegate }
        val document = Json.parseToJsonElement((execute.meta as PayloadMeta.Evm.HermesDelegate).typedData).jsonObject
        assertEquals(setOf("types", "primaryType", "domain", "message"), document.keys)
        assertEquals(setOf("EIP712Domain", "Execute", "Call"), document.getValue("types").jsonObject.keys)
        val domain = document.getValue("domain").jsonObject
        assertEquals(L1_ADDRESS, domain.string("verifyingContract"))
        assertEquals(1, domain.getValue("chainId").jsonPrimitive.int)
        val message = document.getValue("message").jsonObject
        assertEquals(setOf("mode", "calls", "nonce", "deadline"), message.keys)
        assertEquals(HERMES_NONCE.toString(), message.string("nonce"))
        val call = message.getValue("calls").jsonArray.single().jsonObject
        assertEquals(CHANGE_PUB_KEY_CONTRACT, call.string("target"))
        assertEquals("0", call.string("value"))
        assertEquals(env.registrationCalldata(TRADING_PUBLIC_KEY), call.string("data"))
    }

    @Test
    fun aPreviewThatSignsAnythingButTheKeyRegistrationIsNoQuoteAndNothingIsSigned() = runTest {
        val now = { env.serverTime.currentTimeSeconds() }
        val tampered: Map<String, () -> List<JsonObject>> = mapOf(
            "a delegation to another contract" to {
                listOf(env.authorizationPayload("authorization", address = OTHER_DELEGATE), env.hermesPayload("execute"))
            },
            "a delegation on another chain" to {
                listOf(env.authorizationPayload("authorization", chainId = BSC_CHAIN_ID), env.hermesPayload("execute"))
            },
            "payloads on another chain" to {
                listOf(env.authorizationPayload("authorization", chain = "arb/mainnet"), env.hermesPayload("execute"))
            },
            "no delegation" to { listOf(env.hermesPayload("execute")) },
            "a payment beside the signatures" to {
                listOf(
                    env.authorizationPayload("authorization"),
                    env.hermesPayload("execute"),
                    env.evmPayload("pay", to = RELAY_DEPOSITORY, value = ETH_AMOUNT, data = env.nativeDepositData()),
                )
            },
            "a second call" to {
                previewWith(env.hermesDocument(calls = listOf(env.hermesCall(), env.hermesCall(target = USDC_TOKEN, data = env.approveData(RELAY_DEPOSITORY, USDC_AMOUNT)))))
            },
            "a call to another contract" to { previewWith(env.hermesDocument(calls = listOf(env.hermesCall(target = RELAY_DEPOSITORY)))) },
            "a call to the deposit gateway" to { previewWith(env.hermesDocument(calls = listOf(env.hermesCall(target = GATEWAY)))) },
            "a call that moves coins" to { previewWith(env.hermesDocument(calls = listOf(env.hermesCall(value = "1")))) },
            "another key" to {
                previewWith(env.hermesDocument(calls = listOf(env.hermesCall(data = env.registrationCalldata("0x" + "22".repeat(40))))))
            },
            "another account" to { previewWith(env.hermesDocument(owner = RELAY_DEPOSITORY)) },
            "another delegate's salt" to { previewWith(env.hermesDocument(salt = "0x" + "0".repeat(24) + OTHER_DELEGATE.removePrefix("0x"))) },
            "another mode" to { previewWith(env.hermesDocument(mode = PLAIN_BATCH_MODE)) },
            "an expired deadline" to { previewWith(env.hermesDocument(deadline = now() - 1)) },
            "a nonce past uint256" to { previewWith(env.hermesDocument(nonce = "9".repeat(78))) },
            "a deadline past a day" to { previewWith(env.hermesDocument(deadline = now() + DAY_MS / 1000 + 60)) },
            "a domain typed in another order" to { previewWith(env.hermesDocument(domainType = DOMAIN_TYPE.reversed())) },
            "a call typed with another field" to {
                previewWith(env.hermesDocument(callType = listOf("target" to "address", "value" to "uint256", "data" to "bytes", "gas" to "uint256")))
            },
        )

        tampered.forEach { (case, payloads) ->
            env = PerpsFundingTestEnv()
            signAuthPayloads()
            quoteFirstRelayedDeposit(previewPayloads = payloads, payment = { listOf(arbitrumPayment()) })

            val failure = failureOf {
                env.depositService.confirmRequest(WALLET_ID, arbitrumSource(), ETH_AMOUNT, PerpsSourceScreen.PerpsHome)
            }

            assertEquals(case, PerpsDepositQuoteError.QuoteFailed, failure.toDepositQuoteError())
            coVerify(exactly = 0) { env.provider.signPayload(any(), any(), any(), any(), any()) }
        }
    }

    @Test
    fun aPreviewPaidFromEthereumIsTheExecuteAloneForTheSendersOwnAccount() = runTest {
        val tampered: Map<String, Pair<() -> PerpsDepositSource, () -> List<JsonObject>>> = mapOf(
            "a delegation signed ahead of the payment" to Pair(
                { env.source(TestAssets.eth.id) },
                { previewWith(env.hermesDocument()) },
            ),
            "USDC on Ethereum, which registers in its own batch" to Pair(
                { env.source(USDC.id) },
                { listOf(env.hermesPayload("execute")) },
            ),
            "a sender that is not the account's owner" to Pair(
                {
                    env.accounts[TestAssets.usdtEth.id] = testAccount(TestAssets.usdtEth, balance = "100000000", address = RELAY_DEPOSITORY)
                    env.source(TestAssets.usdtEth.id)
                },
                { listOf(env.hermesPayload("execute")) },
            ),
            "a call to another contract" to Pair(
                { env.source(TestAssets.usdtEth.id) },
                { listOf(env.hermesPayload("execute", env.hermesDocument(calls = listOf(env.hermesCall(target = RELAY_DEPOSITORY))))) },
            ),
        )

        tampered.forEach { (case, preview) ->
            val (source, payloads) = preview
            env = PerpsFundingTestEnv()
            signAuthPayloads()
            quoteFirstRelayedDeposit(previewPayloads = payloads)

            val failure = failureOf {
                env.depositService.confirmRequest(WALLET_ID, source(), ETH_AMOUNT, PerpsSourceScreen.PerpsHome)
            }

            assertEquals(case, PerpsDepositQuoteError.QuoteFailed, failure.toDepositQuoteError())
            coVerify(exactly = 0) { env.provider.signPayload(any(), any(), any(), any(), any()) }
        }
    }

    @Test
    fun aPreviewOutlivedOnTheConfirmScreenIsNotSigned() = runTest {
        signAuthPayloads()
        quoteFirstRelayedDeposit()
        val previewed = env.depositService.confirmRequest(WALLET_ID, env.source(TestAssets.eth.id), ETH_AMOUNT, PerpsSourceScreen.PerpsHome)
        env.serverTime.offsetMs = 2 * HERMES_DEADLINE_SECONDS * 1000

        val failure = failureOf { env.depositService.authorize(previewed, mockk<MnemonicWallet>()) }

        assertEquals(PerpsDepositQuoteError.QuoteFailed, failure.toDepositQuoteError())
        coVerify(exactly = 0) { env.provider.signPayload(any(), any(), any(), any(), any()) }
        assertEquals(listOf(GET_ACCOUNT, QUOTE_DEPOSIT, QUOTE_DEPOSIT), env.endpoints)
    }

    @Test
    fun aPreviewIsSignedOnlyAgainstASecurityConfigThatVerifies() = runTest {
        signAuthPayloads()
        quoteFirstRelayedDeposit()
        val source = env.source(TestAssets.eth.id)

        env.servedSecurityBlock = null
        val unserved = failureOf { env.depositService.confirmRequest(WALLET_ID, source, ETH_AMOUNT, PerpsSourceScreen.PerpsHome) }
        env.servedSecurityBlock = env.securityBlock(delegate = OTHER_DELEGATE)
        val otherDelegate = failureOf { env.depositService.confirmRequest(WALLET_ID, source, ETH_AMOUNT, PerpsSourceScreen.PerpsHome) }

        assertEquals(PerpsDepositQuoteError.QuoteFailed, unserved.toDepositQuoteError())
        assertEquals(PerpsDepositQuoteError.QuoteFailed, otherDelegate.toDepositQuoteError())
        coVerify(exactly = 0) { env.provider.signPayload(any(), any(), any(), any(), any()) }
    }

    private fun previewWith(document: JsonObject): List<JsonObject> {
        return listOf(env.authorizationPayload("authorization"), env.hermesPayload("execute", document))
    }

    @Test
    fun aCoinDepositThatLeavesNothingForTheNetworkFeeIsRefusedOnReview() = runTest {
        quoteRelayedDeposit()
        priceEveryPayloadAt(ONE_ETH)

        val failure = failureOf { env.depositService.confirmRequest(WALLET_ID, env.source(TestAssets.eth.id), ETH_AMOUNT, PerpsSourceScreen.PerpsHome) }

        assertEquals(PerpsDepositQuoteError.InsufficientNetworkFee("ETH", BigDecimal("1.01")), failure.toDepositQuoteError())
    }

    @Test
    fun aTokenDepositNeedsTheChainCoinForItsNetworkFee() = runTest {
        env.accounts[TestAssets.eth.id] = testAccount(TestAssets.eth, balance = "1000", price = "2000")
        quoteDirectDeposit(USDC_AMOUNT)
        priceEveryPayloadAt(GWEI)

        val failure = failureOf { env.depositService.confirmRequest(WALLET_ID, env.source(USDC.id), USDC_AMOUNT, PerpsSourceScreen.PerpsHome) }

        assertEquals(PerpsDepositQuoteError.InsufficientNetworkFee("ETH", BigDecimal("0.000002")), failure.toDepositQuoteError())
    }

    @Test
    fun aLivePriceChecksTheNetworkFeeAgainstTheCachedBalance() = runTest {
        env.accounts[TestAssets.eth.id] = testAccount(TestAssets.eth, balance = ONE_ETH.toString(), price = "2000")
        quoteDirectDeposit(USDC_AMOUNT)
        priceEveryPayloadAt(GWEI)

        env.depositService.quote(WALLET_ID, env.source(USDC.id), USDC_AMOUNT, PerpsSourceScreen.PerpsHome)

        coVerify(exactly = 0) { env.mcAccountRepository.findAccount(WALLET_ID, TestAssets.eth.id, any(), true) }
    }

    @Test
    fun aShortCachedBalanceIsCheckedAgainstAFreshOneBeforeTheNetworkFeeError() = runTest {
        env.accounts[TestAssets.eth.id] = testAccount(TestAssets.eth, balance = "1000", price = "2000")
        coEvery { env.mcAccountRepository.findAccount(WALLET_ID, TestAssets.eth.id, any(), true) } returns
            testAccount(TestAssets.eth, balance = ONE_ETH.toString(), price = "2000")
        quoteDirectDeposit(USDC_AMOUNT)
        priceEveryPayloadAt(GWEI)

        val quote = env.depositService.quote(WALLET_ID, env.source(USDC.id), USDC_AMOUNT, PerpsSourceScreen.PerpsHome)

        assertEquals(DEPOSIT_OPERATION_ID, (quote.request.type as ConfirmType.PerpetualDeposit).operation.id)
    }

    @Test
    fun aDepositWhoseNetworkFeeCannotBePricedStillOpensTheConfirmScreen() = runTest {
        quoteRelayedDeposit()
        coEvery { env.provider.estimateFee(any<Payload>(), any(), any()) } throws IOException("no node")

        val request = env.depositService.confirmRequest(WALLET_ID, env.source(TestAssets.eth.id), ETH_AMOUNT, PerpsSourceScreen.PerpsHome)

        assertEquals(DEPOSIT_OPERATION_ID, (request.type as ConfirmType.PerpetualDeposit).operation.id)
    }

    @Test
    fun aDepositThatStatesNoProviderFeesCarriesZeroProviderFees() = runTest {
        quoteRelayedDepositWithFees(fees = emptyList())

        val deposit = confirmRelayedDeposit()

        assertEquals(ConfirmType.PerpetualDeposit.ProviderFees(ChainDecimal.ZERO, ChainDecimal.ZERO), deposit.providerFees)
    }

    @Test
    fun theConfirmRequestCarriesTheDestinationGasApartFromEveryOtherProviderFee() = runTest {
        quoteRelayedDepositWithFees(
            fees = listOf("destination_gas" to "0.226962", "provider" to "0.025370", "relayer_tip" to "0.01"),
        )

        val deposit = confirmRelayedDeposit()

        assertEquals(ChainDecimal.parseString("0.226962"), deposit.providerFees?.destinationGasUsd)
        assertEquals(ChainDecimal.parseString("0.03537"), deposit.providerFees?.providerUsd)
    }

    @Test
    fun providerFeesReadEveryDecimalNotation() = runTest {
        quoteRelayedDepositWithFees(fees = listOf("destination_gas" to " 0.50 ", "provider" to ".5", "relayer_tip" to "1E-2"))

        val deposit = confirmRelayedDeposit()

        assertEquals(ChainDecimal.parseString("0.5"), deposit.providerFees?.destinationGasUsd)
        assertEquals(ChainDecimal.parseString("0.51"), deposit.providerFees?.providerUsd)
    }

    @Test
    fun anUnreadableFeeLeavesTheProviderFeesUnknownAndTheDepositOpens() = runTest {
        quoteRelayedDepositWithFees(fees = listOf("destination_gas" to "0.2", "provider" to "n/a"))

        val deposit = confirmRelayedDeposit()

        assertEquals(DEPOSIT_OPERATION_ID, deposit.operation.id)
        assertNull(deposit.providerFees)
    }

    @Test
    fun aNegativeFeeLeavesTheProviderFeesUnknown() = runTest {
        quoteRelayedDepositWithFees(fees = listOf("provider" to "-0.01"))

        assertNull(confirmRelayedDeposit().providerFees)
    }

    @Test
    fun theMinimumIsProbedWithHalfADollarOfTheSourceAndReadFromTheRefusal() = runTest {
        env.answer(QUOTE_DEPOSIT) {
            env.refused(HTTP_BAD_REQUEST, code = "quote_refused", reason = "amount_too_low", minUsdAmount = "1.07")
        }

        val minimum = checkNotNull(env.depositService.minimum(WALLET_ID, env.source(TestAssets.eth.id)))

        assertEquals("250000000000000", env.quoteBodies.single().string("source_amount"))
        assertEquals(BigDecimal("1.07"), minimum.usd)
        assertEquals(BigInteger("535000000000000"), minimum.units)
    }

    @Test
    fun aDirectDepositsMinimumIsTheGatewaysOwnAndAsksNothing() = runTest {
        val minimum = env.depositService.minimum(WALLET_ID, env.source(USDC.id))

        assertEquals(PerpsFundingAmount.GATEWAY_MINIMUM_USDC_UNITS, minimum?.units)
        assertTrue(env.endpoints.isEmpty())
    }

    @Test
    fun aProbeTheProviderRefusesForTheSenderSaysTheAddressIsBlocked() = runTest {
        env.answer(QUOTE_DEPOSIT) { env.refused(HTTP_BAD_REQUEST, code = "validation_error", reason = "invalid_sender_address") }

        val blocked = try {
            env.depositService.minimum(WALLET_ID, env.source(TestAssets.eth.id))
            false
        } catch (_: PerpsDepositException.AddressBlocked) {
            true
        }

        assertTrue(blocked)
    }

    @Test
    fun aProbeRefusedForTheRegionIsThrownToTheForm() = runTest {
        env.answer(QUOTE_DEPOSIT) { env.refused(HTTP_BAD_REQUEST, code = "region_restricted", reason = null) }

        val thrown = try {
            env.depositService.minimum(WALLET_ID, env.source(TestAssets.eth.id))
            null
        } catch (e: Throwable) {
            e
        }

        assertTrue(checkNotNull(thrown).isRegionRestricted())
    }

    @Test
    fun aProbeRefusedForAnotherReasonNamesNoMinimum() = runTest {
        env.answer(QUOTE_DEPOSIT) { env.refused(HTTP_BAD_REQUEST, code = "quote_refused", reason = "no_route") }

        assertNull(env.depositService.minimum(WALLET_ID, env.source(TestAssets.eth.id)))
    }

    @Test
    fun maxOfAnEvmCoinKeepsAQuarterOverTheQuotedNetworkFee() = runTest {
        quoteRelayedDeposit()
        priceEveryPayloadAt(BigInteger("1000000000000000"))

        val max = env.depositService.maxAmount(WALLET_ID, env.source(TestAssets.eth.id))

        assertEquals(ONE_ETH.toString(), env.quoteBodies.single().string("source_amount"))
        assertEquals(BigInteger("998750000000000000"), max)
    }

    @Test
    fun maxOfAnEvmCoinIsTheWholeBalanceWhenTheBatteryPaysTheFee() = runTest {
        quoteRelayedDeposit()
        priceEveryPayloadAt(BigInteger("1000000000000000"))
        offerFeeMethods(batteryFirst = true)

        val max = env.depositService.maxAmount(WALLET_ID, env.source(TestAssets.eth.id))

        assertEquals(ONE_ETH, max)
    }

    @Test
    fun maxOfAnEvmCoinKeepsTheReserveWhenTheCoinPaysTheFee() = runTest {
        quoteRelayedDeposit()
        priceEveryPayloadAt(BigInteger("1000000000000000"))
        offerFeeMethods(batteryFirst = false)

        val max = env.depositService.maxAmount(WALLET_ID, env.source(TestAssets.eth.id))

        assertEquals(BigInteger("998750000000000000"), max)
    }

    @Test
    fun maxOfTronUsesThePaymentFeeAfterARegistrationPreview() = runTest {
        quoteTronRegistrationPreview()

        val max = env.depositService.maxAmount(WALLET_ID, env.source(TestAssets.trx.id))

        assertEquals(BigInteger("5130134"), max)
        assertEquals(listOf(true, false), env.quoteBodies.map { it.registersKey() })
        coVerify(exactly = 1) { env.provider.estimateFee(match<Payload> { it.id == "pay" }, any(), any()) }
        coVerify(exactly = 0) { env.provider.estimateFee(any<Transaction>()) }
    }

    @Test
    fun maxOfAPreviewKeepsTheWholeBalanceWhenTheSelectedRelayerPays() = runTest {
        quoteTronRegistrationPreview()
        offerFeeMethods(batteryFirst = true)

        val max = env.depositService.maxAmount(WALLET_ID, env.source(TestAssets.trx.id))

        assertEquals(TRX_BALANCE, max)
        assertEquals(listOf(true, false), env.quoteBodies.map { it.registersKey() })
    }

    @Test
    fun maxKeepsItsFallbackWhenThePaymentQuoteIsUnavailable() = runTest {
        quoteTronRegistrationPreview(paymentUnavailable = true)
        coEvery { env.provider.estimateFee(any<Transaction>()) } returns ChainRes.err(ChainError.BadResponse())

        val max = env.depositService.maxAmount(WALLET_ID, env.source(TestAssets.trx.id))

        assertEquals(TRX_BALANCE, max)
        assertEquals(listOf(true, false, true, false, true, false), env.quoteBodies.map { it.registersKey() })
        coVerify(exactly = 1) { env.provider.estimateFee(any<Transaction>()) }
    }

    @Test
    fun maxTriesASmallerPaymentAfterTheFullBalancePaymentIsRefused() = runTest {
        quoteTronRegistrationPreview(paymentLimit = TRX_BALANCE - BigInteger.valueOf(380_500L))

        val max = env.depositService.maxAmount(WALLET_ID, env.source(TestAssets.trx.id))

        assertEquals(BigInteger("5130134"), max)
        assertEquals(listOf(true, false, true, false), env.quoteBodies.map { it.registersKey() })
        coVerify(exactly = 0) { env.provider.estimateFee(any<Transaction>()) }
    }

    @Test
    fun aTronPaymentIsPricedAtTheWalletEstimateWhenTheQuotedFeeLeavesOutBandwidth() = runTest {
        quoteTronDeposit(walletEstimate = tronFee(TRON_ENERGY_FEE + TRON_BANDWIDTH_FEE))

        val quote = env.depositService.quote(WALLET_ID, env.source(TestAssets.trx.id), TRX_DEPOSIT, PerpsSourceScreen.PerpsHome)

        val payment = (quote.request.type as ConfirmType.PerpetualDeposit).operation
        val pricedFee = (TRON_ENERGY_FEE + TRON_BANDWIDTH_FEE).toString()
        assertEquals(pricedFee, payment.payloads.single().fee?.amount?.toString())
        assertEquals(pricedFee, Operation.parse(payment.id, payment.meta).getOrNull()?.payloads?.single()?.fee?.amount?.toString())
    }

    @Test
    fun aTronDepositLeavingOnlyTheQuotedFeeIsRefusedWhenTheWalletEstimatesMore() = runTest {
        quoteTronDeposit(walletEstimate = tronFee(TRON_ENERGY_FEE + TRON_BANDWIDTH_FEE), balance = TRX_DEPOSIT + TRON_QUOTED_FEE)

        val failure = failureOf {
            env.depositService.quote(WALLET_ID, env.source(TestAssets.trx.id), TRX_DEPOSIT, PerpsSourceScreen.PerpsHome)
        }

        assertEquals(PerpsDepositQuoteError.InsufficientNetworkFee("TRX", BigDecimal("3.5325")), failure.toDepositQuoteError())
    }

    @Test
    fun aTronPaymentKeepsItsQuotedFeeWhenTheWalletEstimatesLess() = runTest {
        quoteTronDeposit(walletEstimate = tronFee(TRON_ENERGY_FEE), balance = TRX_DEPOSIT + TRON_QUOTED_FEE)

        val quote = env.depositService.quote(WALLET_ID, env.source(TestAssets.trx.id), TRX_DEPOSIT, PerpsSourceScreen.PerpsHome)

        val payment = (quote.request.type as ConfirmType.PerpetualDeposit).operation
        assertEquals(TRON_QUOTED_FEE.toString(), payment.payloads.single().fee?.amount?.toString())
    }

    @Test
    fun aTronPaymentTheWalletCannotSimulateIsPricedAtItsQuotedFeeAndTheBandwidth() = runTest {
        quoteTronDeposit(walletEstimate = ChainRes.err(ChainError.BadResponse()), bandwidthEstimate = tronFee(TRON_BANDWIDTH_FEE))

        val quote = env.depositService.quote(WALLET_ID, env.source(TestAssets.trx.id), TRX_DEPOSIT, PerpsSourceScreen.PerpsHome)

        val payment = (quote.request.type as ConfirmType.PerpetualDeposit).operation
        assertEquals((TRON_QUOTED_FEE + TRON_BANDWIDTH_FEE).toString(), payment.payloads.single().fee?.amount?.toString())
    }

    @Test
    fun aTronDepositIsNotQuotedWhenTheWalletCannotEstimateItsFee() = runTest {
        quoteTronDeposit(walletEstimate = ChainRes.err(ChainError.BadResponse()), bandwidthEstimate = ChainRes.err(ChainError.BadResponse()))

        val failure = failureOf {
            env.depositService.quote(WALLET_ID, env.source(TestAssets.trx.id), TRX_DEPOSIT, PerpsSourceScreen.PerpsHome)
        }

        assertEquals(PerpsDepositQuoteError.ServiceUnavailable, failure.toDepositQuoteError())
        assertEquals(ConfirmationError.NetworkError, (failure as ConfirmationErrorCause).confirmationError)
    }

    @Test
    fun aTronDepositTheWalletCannotFundIsRefusedForTheNetworkFeeItNeeds() = runTest {
        quoteTronDeposit(
            walletEstimate = ChainRes.err(ChainError.InsufficientFunds("balance is not sufficient")),
            balance = TRX_DEPOSIT + TRON_QUOTED_FEE,
        )

        val failure = failureOf {
            env.depositService.quote(WALLET_ID, env.source(TestAssets.trx.id), TRX_DEPOSIT, PerpsSourceScreen.PerpsHome)
        }

        assertEquals(PerpsDepositQuoteError.InsufficientNetworkFee("TRX", BigDecimal("3.74425")), failure.toDepositQuoteError())
    }

    @Test
    fun bitcoinBisectionRequiresBothRegistrationAndPaymentQuotes() = runTest {
        for ((registrationLimit, paymentLimit) in listOf(4000L to 2450L, 2450L to 4000L)) {
            env = PerpsFundingTestEnv()
            env.withoutAccount()
            env.accounts[TestAssets.btc.id] = testAccount(TestAssets.btc, balance = "4270")
            env.quoteDeposits(
                provider = "relay",
                credited = CREDITED,
                executionId = DEPOSIT_EXECUTION_ID,
                refusal = { asked ->
                    val amount = BigInteger(asked.string("source_amount"))
                    val limit = if (asked.registersKey()) {
                        registrationLimit
                    } else {
                        paymentLimit
                    }
                    env.refused(HTTP_BAD_REQUEST, code = "validation_error", reason = "insufficient_balance")
                        .takeIf { amount > BigInteger.valueOf(limit) }
                },
            ) { asked ->
                if (asked.registersKey()) {
                    listOf(env.authorizationPayload("authorization"), env.hermesPayload("execute"))
                } else {
                    listOf(env.bitcoinPayload("pay", psbt = TestPsbt.deposit(asked.string("source_amount").toLong())))
                }
            }
            coEvery { env.provider.estimateFee(any<Payload>(), any(), any()) } returns
                ChainRes.ok(Fee.Value(Chain.Bitcoin.Mainnet.toAsset(), ChainInteger.fromInt(780)))
            env.bitcoinFeeRate(BITCOIN_FEE_RATE)

            val max = env.depositService.maxAmount(WALLET_ID, env.source(TestAssets.btc.id))

            assertEquals("registration limit $registrationLimit, payment limit $paymentLimit", BigInteger.valueOf(2_425L), max)
            val returnedAmountQuotes = env.quoteBodies.filter { it.string("source_amount") == max.toString() }
            assertTrue(returnedAmountQuotes.any { it.registersKey() })
            assertTrue(returnedAmountQuotes.any { !it.registersKey() })
        }
    }

    @Test
    fun maxOfAFirstDepositFromEthereumKeepsAQuarterOverItsPaymentsNetworkFee() = runTest {
        quoteFirstRelayedDeposit()
        priceEveryPayloadAt(GWEI)

        val max = env.depositService.maxAmount(WALLET_ID, env.source(TestAssets.eth.id))

        assertEquals(ONE_ETH - GWEI * 125.toBigInteger() / 100.toBigInteger(), max)
        assertEquals(listOf(true, false), env.quoteBodies.map { it.registersKey() })
        coVerify(exactly = 0) { env.provider.estimateFee(any<Transaction>()) }
    }

    @Test
    fun maxOfAFirstDepositFromEthereumIsNotPricedOnAPaymentThatDelegatesAnywhereButHermes() = runTest {
        quoteFirstRelayedDeposit(
            payment = {
                listOf(env.evmPayload("pay", to = RELAY_DEPOSITORY, value = ETH_AMOUNT, data = env.nativeDepositData(), authorizationNonce = EVM_NONCE + 1, authorizationAddress = OTHER_DELEGATE))
            },
        )
        priceEveryPayloadAt(GWEI)
        coEvery { env.provider.estimateFee(any<Transaction>()) } returns ChainRes.err(ChainError.BadResponse())

        val max = env.depositService.maxAmount(WALLET_ID, env.source(TestAssets.eth.id))

        assertEquals(ONE_ETH, max)
        coVerify(exactly = 1) { env.provider.estimateFee(any<Transaction>()) }
    }

    @Test
    fun maxOfAnEvmCoinIsNotPricedOnAPaymentThatDelegatesAnywhereButHermes() = runTest {
        env.quoteDeposits(provider = "relay", credited = CREDITED, executionId = DEPOSIT_EXECUTION_ID) {
            listOf(env.evmPayload("pay", to = RELAY_DEPOSITORY, value = ETH_AMOUNT, data = env.nativeDepositData(), authorizationNonce = EVM_NONCE + 1, authorizationAddress = OTHER_DELEGATE))
        }
        priceEveryPayloadAt(GWEI)
        coEvery { env.provider.estimateFee(any<Transaction>()) } returns ChainRes.err(ChainError.BadResponse())

        val max = env.depositService.maxAmount(WALLET_ID, env.source(TestAssets.eth.id))

        assertEquals(ONE_ETH, max)
        coVerify(exactly = 1) { env.provider.estimateFee(any<Transaction>()) }
    }

    @Test
    fun maxOfAFirstDepositFromEthereumAsksNoRelayerToPayItsFee() = runTest {
        quoteFirstRelayedDeposit()
        priceEveryPayloadAt(GWEI)
        offerFeeMethods(batteryFirst = true)

        env.depositService.maxAmount(WALLET_ID, env.source(TestAssets.eth.id))

        coVerify { env.feeMethods.build(match { !(it.type as ConfirmType.PerpetualDeposit).payableByRelayer }, any(), any(), any()) }
        coVerify(exactly = 0) { env.feeMethods.build(match { (it.type as ConfirmType.PerpetualDeposit).payableByRelayer }, any(), any(), any()) }
    }

    @Test
    fun aQuoteTheBatteryPaysForByDefaultIsPaidInCharges() = runTest {
        quoteRelayedDeposit()
        priceEveryPayloadAt(GWEI)
        offerFeeMethods(batteryFirst = true)

        val quote = env.depositService.quote(WALLET_ID, env.source(TestAssets.eth.id), ETH_AMOUNT, PerpsSourceScreen.PerpsHome)

        assertTrue(quote.paysFeeInCharges)
    }

    @Test
    fun anUnpricedBatteryLeftAsTheOnlyMethodIsNotPaidInCharges() = runTest {
        quoteRelayedDeposit()
        priceEveryPayloadAt(GWEI)
        coEvery { env.feeMethods.build(any(), any(), any(), any()) } answers {
            val unpriced = TxFee(
                account = FeeAccount.Keeper(balance = ChainInteger.fromInt(0), rate = null),
                fee = Fee.Value(Asset.Battery(Chain.Ethereum.Mainnet), ChainInteger.fromInt(0)),
            )
            TxFeeState(energy = thirdArg(), estimated = arg(3), options = listOf(unpriced))
        }

        val quote = env.depositService.quote(WALLET_ID, env.source(TestAssets.eth.id), ETH_AMOUNT, PerpsSourceScreen.PerpsHome)

        assertFalse(quote.paysFeeInCharges)
    }

    @Test
    fun aQuoteTheChainCoinPaysForByDefaultIsNotPaidInCharges() = runTest {
        quoteRelayedDeposit()
        priceEveryPayloadAt(GWEI)
        offerFeeMethods(batteryFirst = false)

        val quote = env.depositService.quote(WALLET_ID, env.source(TestAssets.eth.id), ETH_AMOUNT, PerpsSourceScreen.PerpsHome)

        assertFalse(quote.paysFeeInCharges)
    }

    @Test
    fun aFirstDepositFromEthereumShortOfGasForItsPaymentIsRefusedOnReview() = runTest {
        quoteFirstRelayedDeposit()
        priceEveryPayloadAt(ONE_ETH)

        val failure = failureOf {
            env.depositService.confirmRequest(WALLET_ID, env.source(TestAssets.eth.id), ETH_AMOUNT, PerpsSourceScreen.PerpsHome)
        }

        assertEquals(PerpsDepositQuoteError.InsufficientNetworkFee("ETH", BigDecimal("1.01")), failure.toDepositQuoteError())
        coVerify { env.feeMethods.canRelayShortfall(match { !(it.type as ConfirmType.PerpetualDeposit).payableByRelayer }, any(), any(), any()) }
    }

    @Test
    fun cancellationWhilePricingThePreviewPaymentDoesNotStartAFallback() = runTest {
        quoteTronRegistrationPreview()
        coEvery { env.provider.estimateFee(any<Payload>(), any(), any()) } throws CancellationException("cancelled")

        val failure = failureOf { env.depositService.maxAmount(WALLET_ID, env.source(TestAssets.trx.id)) }

        assertTrue(failure is CancellationException)
        coVerify(exactly = 0) { env.provider.estimateFee(any<Transaction>()) }
    }

    @Test
    fun maxOfACoinBelowTheMinimumIsTheWholeBalanceForReviewToRefuse() = runTest {
        env.answer(QUOTE_DEPOSIT) { env.refused(HTTP_BAD_REQUEST, code = "quote_refused", reason = "amount_too_low") }

        val max = env.depositService.maxAmount(WALLET_ID, env.source(TestAssets.eth.id))

        assertEquals(ONE_ETH, max)
    }

    @Test
    fun maxOfACoinWithAPerpsNetworkFeeKeepsItsSafeFeeInsteadOfTheQuotedOne() = runTest {
        quoteRelayedDeposit()
        priceEveryPayloadAt(BigInteger("1000000000000000"))
        offerFeeMethods(batteryFirst = false)
        answerNetworkFees(coinFee(TestAssets.eth.id, perpsSafe = ETH_PERPS_SAFE))

        val max = env.depositService.maxAmount(WALLET_ID, env.source(TestAssets.eth.id))

        assertEquals(ONE_ETH - ETH_PERPS_SAFE, max)
    }

    @Test
    fun maxOfACoinWithAPerpsNetworkFeeIsTheWholeBalanceWhenTheBatteryPaysTheFee() = runTest {
        quoteRelayedDeposit()
        priceEveryPayloadAt(BigInteger("1000000000000000"))
        offerFeeMethods(batteryFirst = true)
        answerNetworkFees(coinFee(TestAssets.eth.id, perpsSafe = ETH_PERPS_SAFE))

        val max = env.depositService.maxAmount(WALLET_ID, env.source(TestAssets.eth.id))

        assertEquals(ONE_ETH, max)
    }

    @Test
    fun maxOfACoinWithoutAPerpsNetworkFeeKeepsTheQuotedReserve() = runTest {
        quoteRelayedDeposit()
        priceEveryPayloadAt(BigInteger("1000000000000000"))
        answerNetworkFees(coinFee(TestAssets.eth.id, perpsSafe = null))

        val max = env.depositService.maxAmount(WALLET_ID, env.source(TestAssets.eth.id))

        assertEquals(BigInteger("998750000000000000"), max)
    }

    @Test
    fun maxOfACoinThePerpsNetworkFeeExceedsIsNothing() = runTest {
        quoteRelayedDeposit()
        priceEveryPayloadAt(BigInteger("1000000000000000"))
        offerFeeMethods(batteryFirst = false)
        answerNetworkFees(coinFee(TestAssets.eth.id, perpsSafe = ONE_ETH + BigInteger.ONE))

        val max = env.depositService.maxAmount(WALLET_ID, env.source(TestAssets.eth.id))

        assertEquals(BigInteger.ZERO, max)
    }

    @Test
    fun maxOfGramKeepsAFixedGramWhateverTheNetworkFeesSay() = runTest {
        env.accounts[TestAssets.ton.id] = testAccount(TestAssets.ton, balance = "5249769061")
        answerNetworkFees(coinFee(TestAssets.ton.id, perpsSafe = BigInteger.valueOf(1_463_800L)))

        val max = env.depositService.maxAmount(WALLET_ID, env.source(TestAssets.ton.id))

        assertEquals(BigInteger.valueOf(4_249_769_061L), max)
        assertTrue(env.quoteBodies.isEmpty())
    }

    @Test
    fun maxOfLessThanAGramIsNothing() = runTest {
        env.accounts[TestAssets.ton.id] = testAccount(TestAssets.ton, balance = "900000000")

        val max = env.depositService.maxAmount(WALLET_ID, env.source(TestAssets.ton.id))

        assertEquals(BigInteger.ZERO, max)
    }

    @Test
    fun maxOfATokenIgnoresTheCoinFeeItsEntryNames() = runTest {
        answerNetworkFees(
            NetworkFees.Entry(
                assetId = USDC.id,
                feeAssetId = TestAssets.eth.id,
                swapSafe = ETH_PERPS_SAFE,
                perpsSafe = ETH_PERPS_SAFE,
            ),
        )

        val max = env.depositService.maxAmount(WALLET_ID, env.source(USDC.id))

        assertEquals(BigInteger("100000000"), max)
        assertTrue(env.quoteBodies.isEmpty())
    }

    @Test
    fun maxOfBitcoinIsTheLargestAmountTheAggregatorBuildsATransactionFor() = runTest {
        env.accounts[TestAssets.btc.id] = testAccount(TestAssets.btc, balance = "4270")
        val largestBuilt = BigInteger.valueOf(2_450L)
        env.quoteDeposits(
            provider = "relay",
            credited = CREDITED,
            executionId = DEPOSIT_EXECUTION_ID,
            refusal = { asked ->
                env.refused(HTTP_BAD_REQUEST, code = "validation_error", reason = "insufficient_balance")
                    .takeIf { BigInteger(asked.string("source_amount")) > largestBuilt }
            },
        ) { asked ->
            listOf(env.bitcoinPayload("pay", psbt = TestPsbt.deposit(asked.string("source_amount").toLong())))
        }
        coEvery { env.provider.estimateFee(any<Payload>(), any(), any()) } returns
            ChainRes.ok(Fee.Value(Chain.Bitcoin.Mainnet.toAsset(), ChainInteger.fromInt(780)))
        env.bitcoinFeeRate(BITCOIN_FEE_RATE)

        val max = env.depositService.maxAmount(WALLET_ID, env.source(TestAssets.btc.id))

        assertEquals(
            listOf("4270", "3843", "2135", "3295", "2715", "2425", "2570"),
            env.quoteBodies.map { it.string("source_amount") },
        )
        assertEquals(BigInteger.valueOf(2_425L), max)
    }

    private fun answerNetworkFees(vararg entries: NetworkFees.Entry) {
        every { env.networkFees.fees } returns MutableStateFlow(NetworkFees(entries.toList()))
    }

    private fun coinFee(assetId: String, perpsSafe: BigInteger?) = NetworkFees.Entry(
        assetId = assetId,
        feeAssetId = assetId,
        swapSafe = ETH_PERPS_SAFE,
        perpsSafe = perpsSafe,
    )

    private fun offerFeeMethods(batteryFirst: Boolean) {
        coEvery { env.feeMethods.build(any(), any(), any(), any()) } answers {
            val battery = TxFee(
                account = FeeAccount.Keeper(balance = ChainInteger.fromInt(10), rate = null),
                fee = Fee.Value(Asset.Battery(Chain.Ethereum.Mainnet), ChainInteger.fromInt(2)),
            )
            val coin = TxFee(account = FeeAccount.Chain(thirdArg()), fee = arg(3))
            TxFeeState(
                energy = thirdArg(),
                estimated = arg(3),
                options = if (batteryFirst) {
                    listOf(battery, coin)
                } else {
                    listOf(coin, battery)
                },
            )
        }
    }

    private fun quoteRelayedDepositWithFees(fees: List<Pair<String, String>>) {
        env.quoteDeposits(
            provider = "relay",
            credited = BigInteger.valueOf(15_000_000L),
            executionId = DEPOSIT_EXECUTION_ID,
            fees = fees,
        ) {
            listOf(env.evmPayload("pay", to = RELAY_DEPOSITORY, value = ETH_AMOUNT, data = env.nativeDepositData()))
        }
        priceEveryPayloadAt(GWEI)
    }

    private suspend fun confirmRelayedDeposit(): ConfirmType.PerpetualDeposit {
        val request = env.depositService.confirmRequest(WALLET_ID, env.source(TestAssets.eth.id), ETH_AMOUNT, PerpsSourceScreen.PerpsHome)
        return request.type as ConfirmType.PerpetualDeposit
    }

    private fun priceEveryPayloadAt(fee: BigInteger) {
        coEvery { env.provider.estimateFee(any<Payload>(), any(), any()) } returns
            ChainRes.ok(Fee.Value(Chain.Ethereum.Mainnet.toAsset(), ChainInteger.parseString(fee.toString())))
    }

    private fun quoteFirstRelayedDeposit(
        credited: BigInteger = CREDITED,
        previewPayloads: () -> List<JsonObject> = { listOf(env.hermesPayload("execute")) },
        payment: (() -> List<JsonObject>)? = null,
        signedPayment: (() -> List<JsonObject>)? = payment,
    ) {
        env.withoutAccount()
        val previewed = { asked: JsonObject -> "operation" !in asked && asked.registersKey() }
        env.quoteDeposits(
            provider = "relay",
            credited = credited,
            executionId = DEPOSIT_EXECUTION_ID,
            accountUninitialized = true,
            preview = previewed,
        ) { asked ->
            when {
                previewed(asked) -> previewPayloads()
                "operation" in asked -> signedPayment?.invoke() ?: listOf(relayedPayment(asked))
                else -> payment?.invoke() ?: listOf(relayedPayment(asked))
            }
        }
    }

    private fun foreignPayments(): Map<String, () -> List<JsonObject>> {
        return mapOf(
            "a delegation to another contract" to {
                listOf(env.evmPayload("pay", to = RELAY_DEPOSITORY, value = ETH_AMOUNT, data = env.nativeDepositData(), authorizationNonce = EVM_NONCE + 1, authorizationAddress = OTHER_DELEGATE))
            },
            "a delegation on another chain" to {
                listOf(env.evmPayload("pay", to = RELAY_DEPOSITORY, value = ETH_AMOUNT, data = env.nativeDepositData(), authorizationNonce = EVM_NONCE + 1, authorizationChainId = BSC_CHAIN_ID))
            },
            "a delegating payment on another chain" to {
                listOf(env.evmPayload("pay", to = RELAY_DEPOSITORY, value = ETH_AMOUNT, data = env.nativeDepositData(), authorizationNonce = EVM_NONCE + 1, chain = "bsc/mainnet"))
            },
            "a delegation for the nonce the payment spends" to {
                listOf(env.evmPayload("pay", to = RELAY_DEPOSITORY, value = ETH_AMOUNT, data = env.nativeDepositData(), authorizationNonce = 0))
            },
            "a delegation to sign apart" to {
                listOf(env.authorizationPayload("authorization"), env.evmPayload("pay", to = RELAY_DEPOSITORY, value = ETH_AMOUNT, data = env.nativeDepositData()))
            },
            "an Execute to sign beside the payment" to {
                listOf(env.hermesPayload("pay-execute"), env.evmPayload("pay", to = RELAY_DEPOSITORY, value = ETH_AMOUNT, data = env.nativeDepositData()))
            },
        )
    }

    private fun arbitrumSource(): PerpsDepositSource {
        val arbitrumEth = testAsset("arb/mainnet/coin", "ETH", "Arbitrum", decimals = 18)
        env.accounts[arbitrumEth.id] = testAccount(arbitrumEth, balance = ONE_ETH.toString(), price = "2000")
        return env.source(arbitrumEth.id)
    }

    private fun arbitrumBatch(authorizationChainId: Long): JsonObject = env.evmPayload(
        "batch",
        to = TestWallet.ETH_ADDRESS,
        data = env.batchData(Triple(RELAY_DEPOSITORY, ETH_AMOUNT, env.nativeDepositData()), chain = Chain.Arbitrum.Mainnet),
        authorizationNonce = EVM_NONCE + 1,
        authorizationChainId = authorizationChainId,
        chain = "arb/mainnet",
    )

    private fun arbitrumPayment(): JsonObject {
        return env.evmPayload("pay", to = RELAY_DEPOSITORY, value = ETH_AMOUNT, data = env.nativeDepositData(), chain = "arb/mainnet")
    }

    private fun quoteTronDeposit(
        walletEstimate: ChainRes<Fee>,
        bandwidthEstimate: ChainRes<Fee> = tronFee(TRON_BANDWIDTH_FEE),
        balance: BigInteger = TRX_BALANCE,
    ) {
        env.accounts[TestAssets.trx.id] = testAccount(TestAssets.trx, balance = balance.toString())
        env.quoteDeposits(provider = "relay", credited = CREDITED, executionId = DEPOSIT_EXECUTION_ID, accountUninitialized = false) { asked ->
            val sent = BigInteger(asked.string("source_amount"))
            listOf(env.tronPayload("pay", to = TRON_RELAY_DEPOSITORY, data = env.tronNativeDepositData(), value = sent, fee = TRON_QUOTED_FEE))
        }
        coEvery { env.provider.estimateFee(any<Payload>(), any(), any()) } answers {
            val payload = firstArg<Payload>()
            when {
                payload.fee != null -> ChainRes.ok(checkNotNull(payload.fee))
                payload.id == "pay" -> walletEstimate
                else -> bandwidthEstimate
            }
        }
    }

    private fun tronFee(amount: BigInteger): ChainRes<Fee> {
        return ChainRes.ok(Fee.Value(Chain.Tron.Mainnet.toAsset(), ChainInteger.parseString(amount.toString())))
    }

    private fun quoteTronRegistrationPreview(paymentUnavailable: Boolean = false, paymentLimit: BigInteger = TRX_BALANCE) {
        env.withoutAccount()
        env.accounts[TestAssets.trx.id] = testAccount(TestAssets.trx, balance = TRX_BALANCE.toString())
        env.quoteDeposits(
            provider = "relay",
            credited = CREDITED,
            executionId = DEPOSIT_EXECUTION_ID,
            refusal = { asked ->
                when {
                    asked.registersKey() -> null
                    paymentUnavailable -> env.refused(HTTP_UNAVAILABLE, code = "unavailable", reason = null, retryable = true)
                    BigInteger(asked.string("source_amount")) > paymentLimit ->
                        env.refused(HTTP_BAD_REQUEST, code = "validation_error", reason = "insufficient_balance")
                    else -> null
                }
            },
        ) { asked ->
            if (asked.registersKey()) {
                listOf(env.authorizationPayload("authorization"), env.hermesPayload("execute"))
            } else {
                val sent = BigInteger(asked.string("source_amount"))
                listOf(env.tronPayload("pay", to = TRON_RELAY_DEPOSITORY, data = env.tronNativeDepositData(), value = sent))
            }
        }
        coEvery { env.provider.estimateFee(any<Payload>(), any(), any()) } returns
            ChainRes.ok(Fee.Value(Chain.Tron.Mainnet.toAsset(), ChainInteger.fromInt(380_500)))
    }

    private fun signAuthPayloads() {
        coEvery { env.provider.signPayload(any(), any(), any(), any(), any()) } answers {
            val payload = firstArg<Payload>()
            val data = when (payload.type) {
                Payload.Type.Eip7702Authorization -> buildJsonObject {
                    put("y_parity", AUTHORIZATION_Y_PARITY)
                    put("r", AUTHORIZATION_R)
                    put("s", AUTHORIZATION_S)
                }
                Payload.Type.Eip7821HermesDelegate -> JsonPrimitive(EXECUTE_SIGNATURE)
                else -> error("unexpected ${payload.type}")
            }
            SignRes.ok(SignedPayload(payload.id, SignedPayload.Type.of(payload.type), payload.chain, data))
        }
    }

    private fun quoteRelayedDeposit(
        expiresAtMs: Long = System.currentTimeMillis() + QUOTE_TTL_MS,
        estimatedDurationSeconds: Int? = ESTIMATED_DURATION_SECONDS,
    ) {
        env.quoteDeposits(
            provider = "relay",
            credited = CREDITED,
            executionId = DEPOSIT_EXECUTION_ID,
            expiresAtMs = expiresAtMs,
            estimatedDurationSeconds = estimatedDurationSeconds,
            accountUninitialized = false,
        ) { asked ->
            listOf(relayedPayment(asked))
        }
    }

    private fun relayedPayment(asked: JsonObject): JsonObject {
        return env.evmPayload("pay", to = RELAY_DEPOSITORY, value = BigInteger(asked.string("source_amount")), data = env.nativeDepositData())
    }

    private fun quoteDirectDeposit(amount: BigInteger) {
        env.quoteDeposits(provider = "direct", credited = amount, executionId = null) {
            listOf(
                env.evmPayload("approve", to = USDC_TOKEN, data = env.approveData(GATEWAY, amount)),
                env.evmPayload("deposit", to = GATEWAY, data = env.gatewayDepositData(L1_ADDRESS, amount)),
            )
        }
    }

    private fun quoteBatchedDeposit(authorizationNonce: Long?, accountUninitialized: Boolean? = null) {
        env.quoteDeposits(
            provider = "direct",
            credited = USDC_AMOUNT,
            executionId = null,
            accountUninitialized = accountUninitialized,
        ) {
            listOf(env.evmPayload("batch", to = TestWallet.ETH_ADDRESS, data = directBatchData(), authorizationNonce = authorizationNonce))
        }
    }

    private fun directBatchData(): String = env.batchData(
        Triple(USDC_TOKEN, BigInteger.ZERO, env.approveData(GATEWAY, USDC_AMOUNT)),
        Triple(GATEWAY, BigInteger.ZERO, env.gatewayDepositData(L1_ADDRESS, USDC_AMOUNT)),
    )

    private suspend fun failureOf(block: suspend () -> Unit): Throwable {
        val failure = try {
            block()
            null
        } catch (e: Throwable) {
            e
        }
        assertNotNull("expected the call to fail", failure)
        return checkNotNull(failure)
    }

    private companion object {
        val ETH_AMOUNT: BigInteger = BigInteger("10000000000000000")
        const val BITCOIN_FEE_RATE = 3L
        const val MINUTE_MS = 60_000L
        const val DAY_MS = 24L * 60L * MINUTE_MS
        val USDC_AMOUNT: BigInteger = BigInteger.valueOf(5_000_000L)
        val TRX_BALANCE: BigInteger = BigInteger("5605759")
        val TRX_DEPOSIT: BigInteger = BigInteger.valueOf(3_000_000L)
        val TRON_QUOTED_FEE: BigInteger = BigInteger.valueOf(399_250L)
        val TRON_ENERGY_FEE: BigInteger = BigInteger.valueOf(187_500L)
        val TRON_BANDWIDTH_FEE: BigInteger = BigInteger.valueOf(345_000L)
        val ONE_ETH: BigInteger = BigInteger("1000000000000000000")
        val ETH_PERPS_SAFE: BigInteger = BigInteger("1177800000000000")
        val GWEI: BigInteger = BigInteger("1000000000000")
        val CREDITED: BigInteger = BigInteger.valueOf(24_900_000L)
        const val ESTIMATED_DURATION_SECONDS = 115
        const val AUTHORIZATION_Y_PARITY = 1
        val AUTHORIZATION_R = "0x" + "ab".repeat(32)
        val AUTHORIZATION_S = "0x" + "cd".repeat(32)
        val EXECUTE_SIGNATURE = "0x" + "ef".repeat(64) + "1b"
        const val OTHER_DELEGATE = "0xd1c44e466b70002ac54fb727ed7cbe8f92c782f1"
        const val PLAIN_BATCH_MODE = "0x0100000000000000000000000000000000000000000000000000000000000000"
        const val BSC_CHAIN_ID = 56L
        const val ARBITRUM_CHAIN_ID = 42161L
        val DOMAIN_TYPE = listOf(
            "name" to "string",
            "version" to "string",
            "chainId" to "uint256",
            "verifyingContract" to "address",
            "salt" to "bytes32",
        )
    }
}
