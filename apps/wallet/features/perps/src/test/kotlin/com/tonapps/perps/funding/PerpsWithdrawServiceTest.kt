package com.tonapps.perps.funding

import com.tonapps.chainkit.core.chain.model.account.CryptoWallet
import com.tonapps.extensions.toHex
import com.tonapps.perps.data.PerpsError
import com.tonapps.perps.data.PerpsLoadException
import com.tonapps.perps.data.PerpsSendReason
import com.tonapps.perps.funding.PerpsFundingTestEnv.Companion.ACCOUNT_INDEX
import com.tonapps.perps.funding.PerpsFundingTestEnv.Companion.AGGREGATOR_ACCOUNT_INDEX
import com.tonapps.perps.funding.PerpsFundingTestEnv.Companion.FIRST_LIGHTER_NONCE
import com.tonapps.perps.funding.PerpsFundingTestEnv.Companion.GET_ACCOUNT
import com.tonapps.perps.funding.PerpsFundingTestEnv.Companion.GET_NEXT_NONCE
import com.tonapps.perps.funding.PerpsFundingTestEnv.Companion.GET_PORTFOLIO
import com.tonapps.perps.funding.PerpsFundingTestEnv.Companion.GET_WITHDRAW_STATUS
import com.tonapps.perps.funding.PerpsFundingTestEnv.Companion.HTTP_BAD_REQUEST
import com.tonapps.perps.funding.PerpsFundingTestEnv.Companion.L1_ADDRESS
import com.tonapps.perps.funding.PerpsFundingTestEnv.Companion.QUOTE_WITHDRAW
import com.tonapps.perps.funding.PerpsFundingTestEnv.Companion.SEND_TRANSACTIONS
import com.tonapps.perps.funding.PerpsFundingTestEnv.Companion.TRANSFER_FEE_UNITS
import com.tonapps.perps.funding.PerpsFundingTestEnv.Companion.TRANSFER_MEMO
import com.tonapps.perps.funding.PerpsFundingTestEnv.Companion.TX_TYPE_TRANSFER
import com.tonapps.perps.funding.PerpsFundingTestEnv.Companion.TX_TYPE_WITHDRAW
import com.tonapps.perps.funding.PerpsFundingTestEnv.Companion.WALLET_ID
import com.tonapps.perps.funding.PerpsFundingTestEnv.Companion.WITHDRAWAL_DELAY_SECONDS
import com.tonapps.perps.funding.PerpsFundingTestEnv.Companion.WITHDRAW_EXECUTION_ID
import com.tonapps.wallet.data.multichain.perps.PerpsDepositRoute
import com.tonapps.wallet.data.multichain.perps.PerpsDepositStage
import com.tonapps.wallet.data.multichain.perps.PerpsFundingKind
import com.tonapps.wallet.testing.BaseUnitTest
import com.tonapps.wallet.testing.TestAssets
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.math.BigDecimal
import java.math.BigInteger

/**
 * A withdrawal as the perps backend sees it. It never touches a deposit's endpoints: a withdrawal
 * to Ethereum is not quoted at all, one into another asset is quoted on its own endpoint, and both
 * are signed for Lighter and relayed through `/transactions/send`. The backend is
 * [PerpsFundingTestEnv]'s fake; the service, the tracker and the journal are real.
 */
class PerpsWithdrawServiceTest : BaseUnitTest() {

    private val wallet = mockk<CryptoWallet>()
    private lateinit var env: PerpsFundingTestEnv

    @Before
    fun setUp() {
        env = PerpsFundingTestEnv()
    }

    @Test
    fun aWithdrawalToEthereumIsNotQuoted() = runTest {
        val plan = env.withdrawService.prepare(WALLET_ID, AMOUNT, PerpsFundingAssetId.USDC_ETHEREUM)

        assertEquals(listOf(GET_ACCOUNT, GET_PORTFOLIO), env.endpoints.sorted())
        assertNull(plan.quote)
        // It lands on the address the perps account is bound to.
        assertEquals(L1_ADDRESS, plan.destination.recipient)
        assertEquals(WITHDRAWAL_DELAY_SECONDS, plan.limits.delaySeconds)
    }

    @Test
    fun theWithdrawMaxIsTheTransferableBalanceNotTheCollateral() = runTest {
        env.answer(GET_PORTFOLIO) {
            env.ok(
                buildJsonObject {
                    putJsonObject("balance") {
                        put("collateral", "150")
                        put("available_balance", "120")
                        put("transferable_balance", "100")
                    }
                },
            )
        }

        assertEquals(BigDecimal("100"), env.withdrawService.available(WALLET_ID))
    }

    @Test
    fun theWithdrawMaxFallsBackToTheAvailableBalance() = runTest {
        env.answer(GET_PORTFOLIO) {
            env.ok(
                buildJsonObject {
                    putJsonObject("balance") {
                        put("collateral", "150")
                        put("available_balance", "120")
                    }
                },
            )
        }

        assertEquals(BigDecimal("120"), env.withdrawService.available(WALLET_ID))
    }

    @Test
    fun aMissingReadOnlyTokenIsReissuedBeforeTheWithdrawMaxIsRead() = runTest {
        var reads = 0
        env.answer(GET_PORTFOLIO) {
            reads++
            if (reads == 1) {
                env.refused(HTTP_BAD_REQUEST, code = "readonly_token_unavailable", reason = null)
            } else {
                env.ok(buildJsonObject { putJsonObject("balance") { put("transferable_balance", "100") } })
            }
        }

        assertEquals(BigDecimal("100"), env.withdrawService.available(WALLET_ID))
        assertEquals(listOf(GET_PORTFOLIO, GET_PORTFOLIO), env.endpoints)
    }

    @Test
    fun aWithdrawMaxWithoutAReadOnlyTokenIsNotGuessedFromTheCollateral() = runTest {
        env.answer(GET_PORTFOLIO) { env.refused(HTTP_BAD_REQUEST, code = "readonly_token_unavailable", reason = null) }

        val failure = failureOf { env.withdrawService.available(WALLET_ID) }

        assertEquals(PerpsError.RoTokenUnavailable, (failure as? PerpsLoadException)?.error)
        assertEquals(listOf(GET_PORTFOLIO, GET_PORTFOLIO), env.endpoints)
    }

    @Test
    fun aWithdrawMaxWithoutAReadOnlyTokenActivatesWithTheKeptKeyFirst() = runTest {
        coEvery { env.accountService.activateWithKeptKey(WALLET_ID) } returns true
        var reads = 0
        env.answer(GET_PORTFOLIO) {
            reads++
            if (reads <= 2) {
                env.refused(HTTP_BAD_REQUEST, code = "readonly_token_unavailable", reason = null)
            } else {
                env.ok(buildJsonObject { putJsonObject("balance") { put("transferable_balance", "100") } })
            }
        }

        assertEquals(BigDecimal("100"), env.withdrawService.available(WALLET_ID))
        assertEquals(listOf(GET_PORTFOLIO, GET_PORTFOLIO, GET_PORTFOLIO), env.endpoints)
    }

    @Test
    fun aWithdrawalIntoAnotherAssetIsQuotedOnItsOwnEndpoint() = runTest {
        env.quoteWithdrawals(received = RECEIVED, estimatedDurationSeconds = 60)

        val plan = env.withdrawService.prepare(WALLET_ID, AMOUNT, TestAssets.usdtEth.id)

        assertEquals(listOf(GET_ACCOUNT, GET_PORTFOLIO, QUOTE_WITHDRAW), env.endpoints.sorted())
        val asked = checkNotNull(env.requests.single { it.endpoint == QUOTE_WITHDRAW }.body)
        assertEquals(TestAssets.usdtEth.id, asked.string("destination_asset"))
        assertEquals(AMOUNT.toString(), asked.string("amount"))
        assertEquals(L1_ADDRESS, asked.string("recipient_address"))

        val quote = checkNotNull(plan.quote)
        assertEquals(WITHDRAW_EXECUTION_ID, quote.executionId)
        assertEquals(RECEIVED, quote.outputUnits)
        assertEquals(60L, quote.estimatedDurationSeconds)
        // What the quote has the account sign: a Lighter transfer to the aggregator, fee on top.
        assertEquals(AGGREGATOR_ACCOUNT_INDEX, quote.transfer.toAccountIndex)
        assertEquals(AMOUNT, quote.transfer.amountUnits + quote.transfer.usdcFeeUnits)
    }

    @Test
    fun aWithdrawalToEthereumIsSignedForLighterAndRelayedByTheBackend() = runTest {
        val plan = env.withdrawService.prepare(WALLET_ID, AMOUNT, PerpsFundingAssetId.USDC_ETHEREUM)
        env.requests.clear()

        val record = env.withdrawService.submit(plan, wallet)

        assertEquals(listOf(GET_PORTFOLIO, GET_NEXT_NONCE, SEND_TRANSACTIONS), env.endpoints)
        val signed = env.signedWithdrawals.single()
        assertEquals(AMOUNT.toLong(), signed.amountUnits)
        assertEquals(FIRST_LIGHTER_NONCE, signed.nonce)
        assertTrue(env.signedTransfers.isEmpty())
        assertEquals(TX_TYPE_WITHDRAW, sentTransaction().getValue("tx_type").jsonPrimitive.int)

        assertEquals(PerpsFundingKind.WITHDRAW, record.kind)
        assertEquals(PerpsDepositRoute.DIRECT_GATEWAY, record.route)
        assertEquals(PerpsDepositStage.ACCEPTED, record.stage)
        assertNull(record.executionId)
        assertEquals(listOf(record), env.journal.list(WALLET_ID))
    }

    @Test
    fun aQuotedWithdrawalIsSentAsALighterTransferToTheAggregator() = runTest {
        env.quoteWithdrawals(received = RECEIVED)
        val plan = env.withdrawService.prepare(WALLET_ID, AMOUNT, TestAssets.usdtEth.id)
        env.requests.clear()

        val record = env.withdrawService.submit(plan, wallet)

        // The same relay as a plain withdrawal, carrying a transfer instead.
        assertEquals(listOf(GET_PORTFOLIO, GET_NEXT_NONCE, SEND_TRANSACTIONS), env.endpoints)
        val signed = env.signedTransfers.single()
        assertEquals(AGGREGATOR_ACCOUNT_INDEX, signed.request.toAccountIndex)
        assertEquals((AMOUNT - TRANSFER_FEE_UNITS).toLong(), signed.request.amount)
        assertEquals(TRANSFER_FEE_UNITS.toLong(), signed.request.usdcFee)
        assertEquals(TRANSFER_MEMO.removePrefix("0x"), signed.request.memo.toHex())
        assertEquals(FIRST_LIGHTER_NONCE, signed.nonce)
        assertTrue(env.signedWithdrawals.isEmpty())
        assertEquals(TX_TYPE_TRANSFER, sentTransaction().getValue("tx_type").jsonPrimitive.int)

        assertEquals(PerpsDepositRoute.CROSSCHAIN_SWAP, record.route)
        assertEquals(PerpsDepositStage.ACCEPTED, record.stage)
        assertEquals(WITHDRAW_EXECUTION_ID, record.executionId)
        assertEquals(RECEIVED.toString(), record.targetAmount)
    }

    @Test
    fun aSendThatHasToBeSignedAgainTakesAFreshNonce() = runTest {
        var sends = 0
        env.answer(SEND_TRANSACTIONS) { request ->
            if (sends++ == 0) {
                env.refused(HTTP_BAD_REQUEST, code = "bad_request", reason = "resign_required")
            } else {
                env.ok(env.accepted(request))
            }
        }
        val plan = env.withdrawService.prepare(WALLET_ID, AMOUNT, PerpsFundingAssetId.USDC_ETHEREUM)
        env.requests.clear()

        val record = env.withdrawService.submit(plan, wallet)

        assertEquals(
            listOf(GET_PORTFOLIO, GET_NEXT_NONCE, SEND_TRANSACTIONS, GET_NEXT_NONCE, SEND_TRANSACTIONS),
            env.endpoints,
        )
        assertEquals(listOf(FIRST_LIGHTER_NONCE, FIRST_LIGHTER_NONCE + 1), env.signedWithdrawals.map { it.nonce })
        assertEquals(PerpsDepositStage.ACCEPTED, record.stage)
        // One withdrawal, however many times it was signed.
        assertEquals(1, env.journal.list(WALLET_ID).size)
    }

    @Test
    fun aSendTheBackendRefusesFailsTheWithdrawal() = runTest {
        env.answer(SEND_TRANSACTIONS) { env.refused(HTTP_BAD_REQUEST, code = "21701", reason = "lighter_rejected") }
        val plan = env.withdrawService.prepare(WALLET_ID, AMOUNT, PerpsFundingAssetId.USDC_ETHEREUM)

        val failure = failureOf { env.withdrawService.submit(plan, wallet) }

        val refused = failure as PerpsWithdrawException.Refused
        assertEquals(PerpsSendReason.LIGHTER_REJECTED, refused.reason)
        assertEquals("21701", refused.code)
        assertEquals(PerpsDepositStage.FAILED, env.journal.list(WALLET_ID).single().stage)
    }

    @Test
    fun aSendThatNeverAnswersStaysUnresolvedAndBlocksTheNextWithdrawal() = runTest {
        env.answer(SEND_TRANSACTIONS) { throw IOException("timeout") }
        val plan = env.withdrawService.prepare(WALLET_ID, AMOUNT, PerpsFundingAssetId.USDC_ETHEREUM)

        val unknown = failureOf { env.withdrawService.submit(plan, wallet) }

        assertTrue(unknown is PerpsWithdrawException.SendUnknown)
        // Left in the journal for recovery to settle: the transaction may have reached Lighter.
        val pending = env.journal.list(WALLET_ID).single()
        assertEquals(PerpsDepositStage.SOURCE_UNKNOWN, pending.stage)

        env.requests.clear()
        val blocked = failureOf { env.withdrawService.submit(plan, wallet) }

        assertEquals(pending.operationId, (blocked as PerpsWithdrawException.WithdrawInFlight).operationId)
        assertTrue(env.endpoints.isEmpty())
    }

    @Test
    fun anExpiredWithdrawQuoteIsNeverSigned() = runTest {
        env.quoteWithdrawals(received = RECEIVED)
        val plan = env.withdrawService.prepare(WALLET_ID, AMOUNT, TestAssets.usdtEth.id)
        val expired = plan.copy(quote = checkNotNull(plan.quote).copy(expiresAtMs = System.currentTimeMillis() - 1L))
        env.requests.clear()

        val failure = failureOf { env.withdrawService.submit(expired, wallet) }

        assertTrue(failure is PerpsWithdrawException.QuoteExpired)
        assertTrue(env.endpoints.isEmpty())
        assertTrue(env.signedTransfers.isEmpty())
    }

    @Test
    fun aTransferWhoseLighterFeeChangedSinceTheQuoteIsNeverSigned() = runTest {
        env.quoteWithdrawals(received = RECEIVED)
        val plan = env.withdrawService.prepare(WALLET_ID, AMOUNT, TestAssets.usdtEth.id)
        coEvery { env.lighterApi.transferFeeUnits(ACCOUNT_INDEX, AGGREGATOR_ACCOUNT_INDEX) } returns
            TRANSFER_FEE_UNITS + BigInteger.ONE
        env.requests.clear()

        val failure = failureOf { env.withdrawService.submit(plan, wallet) }

        assertTrue(failure is PerpsWithdrawException.FeeChanged)
        assertTrue(env.signedTransfers.isEmpty())
        assertTrue(SEND_TRANSACTIONS !in env.endpoints)
        assertTrue(env.journal.list(WALLET_ID).isEmpty())
    }

    @Test
    fun aTransferIsSignedWhenLighterCannotTellItsCurrentFee() = runTest {
        env.quoteWithdrawals(received = RECEIVED)
        val plan = env.withdrawService.prepare(WALLET_ID, AMOUNT, TestAssets.usdtEth.id)
        coEvery { env.lighterApi.transferFeeUnits(ACCOUNT_INDEX, AGGREGATOR_ACCOUNT_INDEX) } throws IOException("lighter is down")

        val record = env.withdrawService.submit(plan, wallet)

        assertEquals(TRANSFER_FEE_UNITS.toLong(), env.signedTransfers.single().request.usdcFee)
        assertEquals(PerpsDepositStage.ACCEPTED, record.stage)
    }

    @Test
    fun anAssetHiddenInTheWalletIsStillAWithdrawalDestination() = runTest {
        val usdt = checkNotNull(env.accounts[TestAssets.usdtEth.id])
        env.accounts[TestAssets.usdtEth.id] = usdt.copy(isHidden = true)

        val destinations = env.withdrawService.destinations(WALLET_ID)

        assertTrue(destinations.any { it.assetId.equals(TestAssets.usdtEth.id, ignoreCase = true) })
    }

    @Test
    fun anAmountOutsideTheLimitsIsRefusedBeforeAnyQuote() = runTest {
        env.quoteWithdrawals(received = RECEIVED)

        val tooLittle = failureOf { env.withdrawService.prepare(WALLET_ID, BigInteger.valueOf(500_000L), TestAssets.usdtEth.id) }
        // A transfer has to cover Lighter's own minimum and its fee.
        val minimum = BigInteger.valueOf(1_000_000L) + TRANSFER_FEE_UNITS
        assertEquals(minimum, (tooLittle as PerpsWithdrawException.BelowMinimum).minUnits)

        val tooMuch = failureOf { env.withdrawService.prepare(WALLET_ID, BigInteger.valueOf(200_000_000L), TestAssets.usdtEth.id) }
        assertTrue(tooMuch is PerpsWithdrawException.InsufficientBalance)

        assertTrue(QUOTE_WITHDRAW !in env.endpoints)
    }

    @Test
    fun anAcceptedTransferIsFollowedOnTheFundingStatusEndpoint() = runTest {
        env.quoteWithdrawals(received = RECEIVED)
        val plan = env.withdrawService.prepare(WALLET_ID, AMOUNT, TestAssets.usdtEth.id)
        val record = env.withdrawService.submit(plan, wallet)
        env.answer(GET_WITHDRAW_STATUS) {
            env.ok(
                buildJsonObject {
                    put("execution_id", WITHDRAW_EXECUTION_ID)
                    put("status", "success")
                    putJsonArray("tx_hashes") { add(DELIVERY_HASH) }
                },
            )
        }
        env.requests.clear()

        val settled = checkNotNull(env.tracker.advance(record))

        // The one endpoint a withdrawal shares with a deposit paid from another chain.
        assertEquals(listOf(GET_WITHDRAW_STATUS), env.endpoints)
        assertEquals(PerpsDepositStage.SETTLED, settled.stage)
        assertEquals(DELIVERY_HASH, settled.l1TxHash)
    }

    @Test
    fun anAcceptedWithdrawalWaitsForThePayoutWithoutAReport() = runTest {
        val plan = env.withdrawService.prepare(WALLET_ID, AMOUNT, PerpsFundingAssetId.USDC_ETHEREUM)
        val record = env.withdrawService.submit(plan, wallet)
        env.requests.clear()

        val awaiting = checkNotNull(env.tracker.advance(record))

        // /transactions/send already relayed it: nothing goes to /transactions.
        assertTrue(env.endpoints.isEmpty())
        assertEquals(PerpsDepositStage.AWAITING_L1, awaiting.stage)
        assertNull(awaiting.failureReason)
    }

    private fun sentTransaction(): JsonObject {
        val sent = checkNotNull(env.requests.last { it.endpoint == SEND_TRANSACTIONS }.body)
        return sent.getValue("transactions").jsonArray.single().jsonObject
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
        const val DELIVERY_HASH = "0xfeedfacefeedfacefeedfacefeedfacefeedfacefeedfacefeedfacefeedface"
        val AMOUNT: BigInteger = BigInteger.valueOf(10_000_000L)
        val RECEIVED: BigInteger = BigInteger.valueOf(9_800_000L)
    }
}
