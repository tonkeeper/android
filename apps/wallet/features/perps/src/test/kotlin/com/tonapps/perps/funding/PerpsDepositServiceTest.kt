package com.tonapps.perps.funding

import com.ionspin.kotlin.bignum.integer.BigInteger as ChainInteger
import com.tonapps.blockchain.model.ConfirmRequest
import com.tonapps.blockchain.model.ConfirmType
import com.tonapps.chainkit.core.chain.api.models.ChainRes
import com.tonapps.chainkit.core.chain.api.models.SignRes
import com.tonapps.chainkit.core.chain.model.account.Chain
import com.tonapps.chainkit.core.chain.model.account.CryptoWallet
import com.tonapps.chainkit.core.chain.model.transaction.Fee
import com.tonapps.chainkit.core.op.Payload
import com.tonapps.chainkit.core.op.PayloadMeta
import com.tonapps.chainkit.core.op.SignedPayload
import com.tonapps.perps.data.LIGHTER_API_KEY_INDEX
import com.tonapps.perps.data.LIGHTER_NIL_ACCOUNT_INDEX
import com.tonapps.perps.funding.PerpsFundingTestEnv.Companion.DEPOSIT_EXECUTION_ID
import com.tonapps.perps.funding.PerpsFundingTestEnv.Companion.DEPOSIT_OPERATION_ID
import com.tonapps.perps.funding.PerpsFundingTestEnv.Companion.EVM_NONCE
import com.tonapps.perps.funding.PerpsFundingTestEnv.Companion.GATEWAY
import com.tonapps.perps.funding.PerpsFundingTestEnv.Companion.HERMES_DELEGATE
import com.tonapps.perps.funding.PerpsFundingTestEnv.Companion.HTTP_BAD_REQUEST
import com.tonapps.perps.funding.PerpsFundingTestEnv.Companion.HTTP_UNAVAILABLE
import com.tonapps.perps.funding.PerpsFundingTestEnv.Companion.L1_ADDRESS
import com.tonapps.perps.funding.PerpsFundingTestEnv.Companion.QUOTE_DEPOSIT
import com.tonapps.perps.funding.PerpsFundingTestEnv.Companion.QUOTE_TTL_MS
import com.tonapps.perps.funding.PerpsFundingTestEnv.Companion.RELAY_DEPOSITORY
import com.tonapps.perps.funding.PerpsFundingTestEnv.Companion.TRADING_PUBLIC_KEY
import com.tonapps.perps.funding.PerpsFundingTestEnv.Companion.USDC
import com.tonapps.perps.funding.PerpsFundingTestEnv.Companion.USDC_TOKEN
import com.tonapps.perps.funding.PerpsFundingTestEnv.Companion.WALLET_ID
import com.tonapps.wallet.testing.BaseUnitTest
import com.tonapps.wallet.testing.TestAssets
import com.tonapps.wallet.testing.TestWallet
import com.tonapps.wallet.testing.testAccount
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
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

        env.depositService.confirmRequest(WALLET_ID, env.source(TestAssets.eth.id), ETH_AMOUNT)

        assertEquals(listOf(QUOTE_DEPOSIT), env.endpoints)
        val asked = checkNotNull(env.requests.single().body)
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
        val quotedAt = System.currentTimeMillis()

        val request = env.depositService.confirmRequest(WALLET_ID, env.source(TestAssets.eth.id), ETH_AMOUNT)

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
        // TK-4608: a minute to sign from when it is quoted, not the quote's own deadline.
        assertTrue(checkNotNull(request.data.expiresAt) in quotedAt + MINUTE_MS..System.currentTimeMillis() + MINUTE_MS)
        // A deposit that swaps on the way shows what the quote credits.
        assertEquals(CREDITED.toString(), deposit.minReceive.toString())
        assertEquals(ESTIMATED_DURATION_SECONDS.toLong(), deposit.estimatedDurationSeconds)
    }

    @Test
    fun aQuoteWithoutAnEstimateOpensWithNoArrivalTime() = runTest {
        quoteRelayedDeposit(estimatedDurationSeconds = null)

        val request = env.depositService.confirmRequest(WALLET_ID, env.source(TestAssets.eth.id), ETH_AMOUNT)

        assertNull((request.type as ConfirmType.PerpetualDeposit).estimatedDurationSeconds)
    }

    @Test
    fun theRequestComesBackFromNavigationWithTheBackendsOperation() = runTest {
        quoteRelayedDeposit()
        val request = env.depositService.confirmRequest(WALLET_ID, env.source(TestAssets.eth.id), ETH_AMOUNT)

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

        val request = env.depositService.confirmRequest(WALLET_ID, env.source(USDC.id), USDC_AMOUNT)

        val deposit = request.type as ConfirmType.PerpetualDeposit
        assertEquals(listOf("approve", "deposit"), deposit.operation.payloads.map { it.id })
        assertEquals(USDC_AMOUNT.toString(), deposit.amount.toString())
        // Credited one to one: there is nothing a minimum would tell.
        assertNull(deposit.minReceive)
        assertEquals(listOf(QUOTE_DEPOSIT), env.endpoints)
    }

    @Test
    fun aTokenDepositOverTheBalanceIsRefusedAfterTheQuote() = runTest {
        val overBalance = BigInteger.valueOf(200_000_000L)
        quoteDirectDeposit(overBalance)

        val failure = failureOf { env.depositService.confirmRequest(WALLET_ID, env.source(USDC.id), overBalance) }

        assertTrue(failure is PerpsDepositException.InsufficientBalance)
        assertEquals(PerpsDepositQuoteError.InsufficientBalance, failure.toDepositQuoteError())
    }

    @Test
    fun aBatchedDepositIsSignedAsTheBackendBuiltIt() = runTest {
        quoteBatchedDeposit(authorizationNonce = EVM_NONCE + 1)

        val request = env.depositService.confirmRequest(WALLET_ID, env.source(USDC.id), USDC_AMOUNT)

        val payload = (request.type as ConfirmType.PerpetualDeposit).operation.payloads.single()
        val meta = payload.meta as PayloadMeta.Evm.Main
        assertTrue(meta.to.display.equals(TestWallet.ETH_ADDRESS, ignoreCase = true))
        assertEquals(BATCH_DATA, meta.data)
        val authorization = checkNotNull(meta.authorization)
        assertTrue(authorization.address.display.equals(HERMES_DELEGATE, ignoreCase = true))
        assertEquals((EVM_NONCE + 1).toString(), authorization.nonce.toString())
    }

    @Test
    fun aQuoteWithoutAnOperationIdCannotBeSigned() = runTest {
        env.quoteDeposits(provider = "relay", credited = CREDITED, executionId = DEPOSIT_EXECUTION_ID, operationId = null) {
            listOf(env.evmPayload("pay", to = RELAY_DEPOSITORY, value = ETH_AMOUNT))
        }

        val failure = failureOf { env.depositService.confirmRequest(WALLET_ID, env.source(TestAssets.eth.id), ETH_AMOUNT) }

        assertEquals(PerpsDepositQuoteError.QuoteFailed, failure.toDepositQuoteError())
    }

    @Test
    fun aQuoteTheBackendRefusesTellsTheAmountScreenWhy() = runTest {
        val source = env.source(TestAssets.eth.id)

        env.answer(QUOTE_DEPOSIT) { env.refused(HTTP_BAD_REQUEST, code = "quote_refused", reason = "amount_too_low") }
        val tooLow = failureOf { env.depositService.confirmRequest(WALLET_ID, source, ETH_AMOUNT) }
        assertEquals(PerpsDepositQuoteError.BelowMinimum(minUsd = null), tooLow.toDepositQuoteError())

        env.answer(QUOTE_DEPOSIT) {
            env.refused(HTTP_BAD_REQUEST, code = "quote_refused", reason = "amount_too_low", minUsdAmount = "2.53")
        }
        val tooLowPriced = failureOf { env.depositService.confirmRequest(WALLET_ID, source, ETH_AMOUNT) }
        assertEquals(PerpsDepositQuoteError.BelowMinimum(minUsd = BigDecimal("2.53")), tooLowPriced.toDepositQuoteError())

        env.answer(QUOTE_DEPOSIT) { env.refused(HTTP_BAD_REQUEST, code = "quote_refused", reason = "no_route") }
        val noRoute = failureOf { env.depositService.confirmRequest(WALLET_ID, source, ETH_AMOUNT) }
        assertEquals(PerpsDepositQuoteError.QuoteFailed, noRoute.toDepositQuoteError())

        env.answer(QUOTE_DEPOSIT) { env.refused(HTTP_BAD_REQUEST, code = "validation_error", reason = "insufficient_balance") }
        val unfunded = failureOf { env.depositService.confirmRequest(WALLET_ID, source, ETH_AMOUNT) }
        assertEquals(PerpsDepositQuoteError.InsufficientBalance, unfunded.toDepositQuoteError())

        env.answer(QUOTE_DEPOSIT) { env.refused(HTTP_UNAVAILABLE, code = "unavailable", reason = null, retryable = true) }
        val unavailable = failureOf { env.depositService.confirmRequest(WALLET_ID, source, ETH_AMOUNT) }
        assertEquals(PerpsDepositQuoteError.ServiceUnavailable, unavailable.toDepositQuoteError())

        env.answer(QUOTE_DEPOSIT) { throw IOException("offline") }
        val offline = failureOf { env.depositService.confirmRequest(WALLET_ID, source, ETH_AMOUNT) }
        assertEquals(PerpsDepositQuoteError.ServiceUnavailable, offline.toDepositQuoteError())
    }

    @Test
    fun aFirstRelayedDepositOpensAsItsPreviewWithoutThePasscode() = runTest {
        quoteFirstRelayedDeposit()

        val request = env.depositService.confirmRequest(WALLET_ID, env.source(TestAssets.eth.id), ETH_AMOUNT)

        assertEquals(listOf(QUOTE_DEPOSIT), env.endpoints)
        val body = env.quoteBodies.single()
        val asked = body.getValue("change_pub_key").jsonObject
        assertEquals(LIGHTER_NIL_ACCOUNT_INDEX, asked.getValue("account_index").jsonPrimitive.long)
        assertEquals(LIGHTER_API_KEY_INDEX, asked.getValue("api_key_index").jsonPrimitive.int)
        // The key kept at the wallet's creation, read without the passcode.
        assertEquals(TRADING_PUBLIC_KEY, asked.string("pub_key"))
        assertNull(body["operation"])
        val deposit = request.type as ConfirmType.PerpetualDeposit
        assertTrue(request.data.preview)
        assertEquals(listOf("authorization", "execute"), deposit.operation.payloads.map { it.id })
        assertEquals(ETH_AMOUNT.toString(), deposit.amount.toString())
        assertEquals(CREDITED.toString(), deposit.minReceive.toString())
    }

    @Test
    fun aKeyTheVenueHoldsIsNamedAllTheSameAndTheDepositOpensAsIs() = runTest {
        quoteRelayedDeposit()

        val request = env.depositService.confirmRequest(WALLET_ID, env.source(TestAssets.eth.id), ETH_AMOUNT)

        // Whether the venue lacks the key is the backend's to tell: answered with the deposit, nothing registers.
        val asked = env.quoteBodies.single().getValue("change_pub_key").jsonObject
        assertEquals(TRADING_PUBLIC_KEY, asked.string("pub_key"))
        assertFalse(request.data.preview)
    }

    @Test
    fun aWalletWithoutAKeptKeyIsNoQuoteAndNeverUnlocks() = runTest {
        env.withoutKeptKey()
        quoteFirstRelayedDeposit()

        val failure = failureOf {
            env.depositService.confirmRequest(WALLET_ID, env.source(TestAssets.eth.id), ETH_AMOUNT)
        }

        assertTrue(failure is IllegalStateException)
        assertTrue(env.quoteBodies.isEmpty())
    }

    @Test
    fun anAuthorizedPreviewIsQuotedAgainWithTheOwnersSignatures() = runTest {
        signAuthPayloads()
        quoteFirstRelayedDeposit()
        val previewed = env.depositService.confirmRequest(WALLET_ID, env.source(TestAssets.eth.id), ETH_AMOUNT)

        val request = env.depositService.authorize(previewed, mockk<CryptoWallet>())

        assertEquals(listOf(QUOTE_DEPOSIT, QUOTE_DEPOSIT), env.endpoints)
        val (preview, signed) = env.quoteBodies
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
    }

    @Test
    fun aPreviewAnsweredWithAnotherPreviewIsNoQuote() = runTest {
        signAuthPayloads()
        env.quoteDeposits(provider = "relay", credited = CREDITED, executionId = null) {
            listOf(env.authorizationPayload("authorization"), env.hermesPayload("execute"))
        }
        val previewed = env.depositService.confirmRequest(WALLET_ID, env.source(TestAssets.eth.id), ETH_AMOUNT)

        val failure = failureOf { env.depositService.authorize(previewed, mockk<CryptoWallet>()) }

        assertEquals(PerpsDepositQuoteError.QuoteFailed, failure.toDepositQuoteError())
        assertEquals(listOf(QUOTE_DEPOSIT, QUOTE_DEPOSIT), env.endpoints)
    }

    @Test
    fun aPreviewQuotedAgainForUpToOnePercentLessIsTheDepositToSend() = runTest {
        signAuthPayloads()
        quoteFirstRelayedDeposit()
        val previewed = env.depositService.confirmRequest(WALLET_ID, env.source(TestAssets.eth.id), ETH_AMOUNT)
        quoteFirstRelayedDeposit(credited = CREDITED * 99.toBigInteger() / 100.toBigInteger())

        val request = env.depositService.authorize(previewed, mockk<CryptoWallet>())

        assertFalse(request.data.preview)
    }

    @Test
    fun aPreviewQuotedAgainForMoreThanOnePercentLessIsNoQuote() = runTest {
        signAuthPayloads()
        quoteFirstRelayedDeposit()
        val previewed = env.depositService.confirmRequest(WALLET_ID, env.source(TestAssets.eth.id), ETH_AMOUNT)
        quoteFirstRelayedDeposit(credited = CREDITED * 98.toBigInteger() / 100.toBigInteger())

        val failure = failureOf { env.depositService.authorize(previewed, mockk<CryptoWallet>()) }

        assertEquals(PerpsDepositQuoteError.QuoteFailed, failure.toDepositQuoteError())
    }

    @Test
    fun aCoinDepositThatLeavesNothingForTheNetworkFeeIsRefusedOnReview() = runTest {
        quoteRelayedDeposit()
        priceEveryPayloadAt(ONE_ETH)

        val failure = failureOf { env.depositService.confirmRequest(WALLET_ID, env.source(TestAssets.eth.id), ETH_AMOUNT) }

        assertEquals(PerpsDepositQuoteError.InsufficientNetworkFee("ETH"), failure.toDepositQuoteError())
    }

    @Test
    fun aTokenDepositNeedsTheChainCoinForItsNetworkFee() = runTest {
        env.accounts[TestAssets.eth.id] = testAccount(TestAssets.eth, balance = "1000", price = "2000")
        quoteDirectDeposit(USDC_AMOUNT)
        priceEveryPayloadAt(GWEI)

        val failure = failureOf { env.depositService.confirmRequest(WALLET_ID, env.source(USDC.id), USDC_AMOUNT) }

        assertEquals(PerpsDepositQuoteError.InsufficientNetworkFee("ETH"), failure.toDepositQuoteError())
    }

    @Test
    fun aDepositWhoseNetworkFeeCannotBePricedStillOpensTheConfirmScreen() = runTest {
        quoteRelayedDeposit()
        coEvery { env.provider.estimateFee(any<Payload>(), any(), any()) } throws IOException("no node")

        val request = env.depositService.confirmRequest(WALLET_ID, env.source(TestAssets.eth.id), ETH_AMOUNT)

        assertEquals(DEPOSIT_OPERATION_ID, (request.type as ConfirmType.PerpetualDeposit).operation.id)
    }

    @Test
    fun theConfirmRequestTellsWhatShareOfTheDepositTheFeesTake() = runTest {
        env.quoteDeposits(provider = "relay", credited = BigInteger.valueOf(15_000_000L), executionId = DEPOSIT_EXECUTION_ID) {
            listOf(env.evmPayload("pay", to = RELAY_DEPOSITORY, value = ETH_AMOUNT))
        }
        priceEveryPayloadAt(BigInteger("500000000000000"))

        val request = env.depositService.confirmRequest(WALLET_ID, env.source(TestAssets.eth.id), ETH_AMOUNT)

        assertEquals(3_000, (request.type as ConfirmType.PerpetualDeposit).feeShareBps)
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
    fun maxOfACoinBelowTheMinimumIsTheWholeBalanceForReviewToRefuse() = runTest {
        env.answer(QUOTE_DEPOSIT) { env.refused(HTTP_BAD_REQUEST, code = "quote_refused", reason = "amount_too_low") }

        val max = env.depositService.maxAmount(WALLET_ID, env.source(TestAssets.eth.id))

        assertEquals(ONE_ETH, max)
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
        ) {
            listOf(env.bitcoinPayload("pay", psbt = "cHNidP8BAA=="))
        }
        coEvery { env.provider.estimateFee(any<Payload>(), any(), any()) } returns
            ChainRes.ok(Fee.Value(Chain.Bitcoin.Mainnet.toAsset(), ChainInteger.fromInt(780)))

        val max = env.depositService.maxAmount(WALLET_ID, env.source(TestAssets.btc.id))

        assertEquals(
            listOf("4270", "3843", "2135", "3295", "2715", "2425", "2570"),
            env.quoteBodies.map { it.string("source_amount") },
        )
        assertEquals(BigInteger.valueOf(2_425L), max)
    }

    private fun priceEveryPayloadAt(fee: BigInteger) {
        coEvery { env.provider.estimateFee(any<Payload>(), any(), any()) } returns
            ChainRes.ok(Fee.Value(Chain.Ethereum.Mainnet.toAsset(), ChainInteger.parseString(fee.toString())))
    }

    private fun quoteFirstRelayedDeposit(credited: BigInteger = CREDITED) {
        val previewed = { asked: JsonObject -> "operation" !in asked }
        env.quoteDeposits(
            provider = "relay",
            credited = credited,
            executionId = DEPOSIT_EXECUTION_ID,
        ) { asked ->
            if (previewed(asked)) {
                listOf(env.authorizationPayload("authorization"), env.hermesPayload("execute"))
            } else {
                listOf(env.evmPayload("pay", to = RELAY_DEPOSITORY, value = ETH_AMOUNT))
            }
        }
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
        ) {
            listOf(env.evmPayload("pay", to = RELAY_DEPOSITORY, value = ETH_AMOUNT))
        }
    }

    private fun quoteDirectDeposit(amount: BigInteger) {
        env.quoteDeposits(provider = "direct", credited = amount, executionId = null) {
            listOf(
                env.evmPayload("approve", to = USDC_TOKEN, data = env.approveData(GATEWAY, amount)),
                env.evmPayload("deposit", to = GATEWAY, data = env.gatewayDepositData(L1_ADDRESS, amount)),
            )
        }
    }

    private fun quoteBatchedDeposit(authorizationNonce: Long?) {
        env.quoteDeposits(provider = "direct", credited = USDC_AMOUNT, executionId = null) {
            listOf(env.evmPayload("batch", to = TestWallet.ETH_ADDRESS, data = BATCH_DATA, authorizationNonce = authorizationNonce))
        }
    }

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
        const val MINUTE_MS = 60_000L
        val USDC_AMOUNT: BigInteger = BigInteger.valueOf(5_000_000L)
        val ONE_ETH: BigInteger = BigInteger("1000000000000000000")
        val GWEI: BigInteger = BigInteger("1000000000000")
        val CREDITED: BigInteger = BigInteger.valueOf(24_900_000L)
        const val ESTIMATED_DURATION_SECONDS = 115
        const val BATCH_DATA = "0xe9ae5c53"
        const val AUTHORIZATION_Y_PARITY = 1
        val AUTHORIZATION_R = "0x" + "ab".repeat(32)
        val AUTHORIZATION_S = "0x" + "cd".repeat(32)
        val EXECUTE_SIGNATURE = "0x" + "ef".repeat(64) + "1b"
    }
}
