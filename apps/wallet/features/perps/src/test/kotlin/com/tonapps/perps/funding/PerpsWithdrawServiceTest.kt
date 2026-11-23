package com.tonapps.perps.funding

import com.tonapps.chainkit.core.chain.model.account.MnemonicWallet
import com.tonapps.extensions.toHex
import com.tonapps.perps.data.PerpsError
import com.tonapps.perps.data.PerpsLoadException
import com.tonapps.perps.data.PerpsSendReason
import com.tonapps.perps.data.USD_CURRENCY
import com.tonapps.perps.funding.PerpsFundingTestEnv.Companion.ACCOUNT_INDEX
import com.tonapps.perps.funding.PerpsFundingTestEnv.Companion.AGGREGATOR_ACCOUNT_INDEX
import com.tonapps.perps.funding.PerpsFundingTestEnv.Companion.FIRST_LIGHTER_NONCE
import com.tonapps.perps.funding.PerpsFundingTestEnv.Companion.GET_ACCOUNT
import com.tonapps.perps.funding.PerpsFundingTestEnv.Companion.GET_NEXT_NONCE
import com.tonapps.perps.funding.PerpsFundingTestEnv.Companion.GET_PORTFOLIO
import com.tonapps.perps.funding.PerpsFundingTestEnv.Companion.GET_WITHDRAW_STATUS
import com.tonapps.perps.funding.PerpsFundingTestEnv.Companion.HTTP_BAD_REQUEST
import com.tonapps.perps.funding.PerpsFundingTestEnv.Companion.L1_ADDRESS
import com.tonapps.perps.funding.PerpsFundingTestEnv.Companion.OTHER_LIGHTER_ACCOUNT
import com.tonapps.perps.funding.PerpsFundingTestEnv.Companion.QUOTE_WITHDRAW
import com.tonapps.perps.funding.PerpsFundingTestEnv.Companion.SEND_TRANSACTIONS
import com.tonapps.perps.funding.PerpsFundingTestEnv.Companion.TRANSFER_FEE_UNITS
import com.tonapps.perps.funding.PerpsFundingTestEnv.Companion.TRANSFER_MEMO
import com.tonapps.perps.funding.PerpsFundingTestEnv.Companion.TX_TYPE_TRANSFER
import com.tonapps.perps.funding.PerpsFundingTestEnv.Companion.WALLET_ID
import com.tonapps.perps.funding.PerpsFundingTestEnv.Companion.WITHDRAW_EXECUTION_ID
import com.tonapps.perps.security.PerpsSecurityBlocks
import com.tonapps.perps.trade.PerpsActivation
import com.tonapps.wallet.data.multichain.perps.PerpsDepositRoute
import com.tonapps.wallet.data.multichain.perps.PerpsDepositStage
import com.tonapps.wallet.data.multichain.perps.PerpsFundingKind
import com.tonapps.wallet.data.multichain.perps.PerpsFundingRecord
import com.tonapps.wallet.testing.BaseUnitTest
import com.tonapps.wallet.testing.TestAssets
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
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
 * A withdrawal as the perps backend sees it. It never touches a deposit's endpoints: every withdrawal,
 * USDC on Ethereum included, is quoted on its own endpoint, signed as a Lighter transfer to the
 * aggregator and relayed through `/transactions/send`. The backend is [PerpsFundingTestEnv]'s fake;
 * the service, the tracker and the journal are real.
 */
class PerpsWithdrawServiceTest : BaseUnitTest() {

    private val wallet = mockk<MnemonicWallet>()
    private lateinit var env: PerpsFundingTestEnv

    @Before
    fun setUp() {
        env = PerpsFundingTestEnv()
        env.quoteWithdrawals(received = RECEIVED)
    }

    @Test
    fun aWithdrawalToUsdcOnEthereumIsQuotedLikeAnyOtherAsset() = runTest {
        val plan = env.withdrawService.prepare(WALLET_ID, AMOUNT, PerpsFundingAssetId.USDC_ETHEREUM)

        assertEquals(listOf(GET_ACCOUNT, GET_PORTFOLIO, QUOTE_WITHDRAW), env.endpoints.sorted())
        val asked = checkNotNull(env.requests.single { it.endpoint == QUOTE_WITHDRAW }.body)
        assertEquals(PerpsFundingAssetId.USDC_ETHEREUM, asked.string("destination_asset"))
        assertEquals(L1_ADDRESS, asked.string("recipient_address"))
        assertEquals(L1_ADDRESS, plan.destination.recipient)
        assertEquals(RECEIVED, plan.quote.outputUnits)
        assertEquals(AGGREGATOR_ACCOUNT_INDEX, plan.quote.transfer.toAccountIndex)
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

        val quote = plan.quote
        assertEquals(WITHDRAW_EXECUTION_ID, quote.executionId)
        assertEquals(RECEIVED, quote.outputUnits)
        assertEquals(60L, quote.estimatedDurationSeconds)
        // What the quote has the account sign: a Lighter transfer to the aggregator, fee on top.
        assertEquals(AGGREGATOR_ACCOUNT_INDEX, quote.transfer.toAccountIndex)
        assertEquals(AMOUNT, quote.transfer.amountUnits + quote.transfer.usdcFeeUnits)
    }

    @Test
    fun aWithdrawalToUsdcOnEthereumIsSentAsALighterTransferToTheAggregator() = runTest {
        val plan = env.withdrawService.prepare(WALLET_ID, AMOUNT, PerpsFundingAssetId.USDC_ETHEREUM)
        env.requests.clear()

        val record = env.withdrawService.submit(plan, wallet)

        assertEquals(listOf(GET_PORTFOLIO, GET_NEXT_NONCE, SEND_TRANSACTIONS), env.endpoints)
        val signed = env.signedTransfers.single()
        assertEquals(AGGREGATOR_ACCOUNT_INDEX, signed.request.toAccountIndex)
        assertEquals(AMOUNT.toLong(), signed.request.amount + signed.request.usdcFee)
        assertEquals(FIRST_LIGHTER_NONCE, signed.nonce)
        assertEquals(TX_TYPE_TRANSFER, sentTransaction().getValue("tx_type").jsonPrimitive.int)

        assertEquals(PerpsFundingKind.WITHDRAW, record.kind)
        assertEquals(PerpsDepositRoute.CROSSCHAIN_SWAP, record.route)
        assertEquals(PerpsDepositStage.ACCEPTED, record.stage)
        assertEquals(WITHDRAW_EXECUTION_ID, record.executionId)
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
        assertEquals(TX_TYPE_TRANSFER, sentTransaction().getValue("tx_type").jsonPrimitive.int)

        assertEquals(PerpsDepositRoute.CROSSCHAIN_SWAP, record.route)
        assertEquals(PerpsDepositStage.ACCEPTED, record.stage)
        assertEquals(WITHDRAW_EXECUTION_ID, record.executionId)
        assertEquals(RECEIVED.toString(), record.targetAmount)
    }

    @Test
    fun aSendThatHasToBeSignedAgainKeepsItsNonce() = runTest {
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

        assertEquals(listOf(GET_PORTFOLIO, GET_NEXT_NONCE, SEND_TRANSACTIONS, SEND_TRANSACTIONS), env.endpoints)
        assertEquals(listOf(FIRST_LIGHTER_NONCE, FIRST_LIGHTER_NONCE), env.signedTransfers.map { it.nonce })
        assertEquals(PerpsDepositStage.ACCEPTED, record.stage)
        assertEquals(2, record.steps.size)
        assertEquals(record.steps.last().txHash, record.sourceTxHash)
        assertEquals(1, env.journal.list(WALLET_ID).size)
    }

    @Test
    fun aRetryRefusedAgainSettlesOnTheAttemptLighterHolds() = runTest {
        env.answer(SEND_TRANSACTIONS) { env.refused(HTTP_BAD_REQUEST, code = "bad_request", reason = "resign_required") }
        lighterSees(LighterTransactionPresence.KNOWN)
        val plan = env.withdrawService.prepare(WALLET_ID, AMOUNT, PerpsFundingAssetId.USDC_ETHEREUM)

        val record = env.withdrawService.submit(plan, wallet)

        assertEquals(listOf(FIRST_LIGHTER_NONCE, FIRST_LIGHTER_NONCE), env.signedTransfers.map { it.nonce })
        assertEquals(PerpsDepositStage.ACCEPTED, record.stage)
        assertEquals(record.steps.first().txHash, record.sourceTxHash)
    }

    @Test
    fun aRetryRefusedAgainStaysTrackedUntilItExpires() = runTest {
        env.answer(SEND_TRANSACTIONS) { env.refused(HTTP_BAD_REQUEST, code = "bad_request", reason = "resign_required") }
        lighterSees(LighterTransactionPresence.UNKNOWN)
        val plan = env.withdrawService.prepare(WALLET_ID, AMOUNT, PerpsFundingAssetId.USDC_ETHEREUM)
        env.requests.clear()

        val unknown = failureOf { env.withdrawService.submit(plan, wallet) }

        assertTrue(unknown is PerpsWithdrawException.SendUnknown)
        assertEquals(listOf(GET_PORTFOLIO, GET_NEXT_NONCE, SEND_TRANSACTIONS, SEND_TRANSACTIONS), env.endpoints)
        val pending = env.journal.list(WALLET_ID).single()
        assertEquals(PerpsDepositStage.SOURCE_UNKNOWN, pending.stage)
        assertEquals(2, pending.steps.size)
        assertEquals(PerpsDepositStage.SOURCE_UNKNOWN, checkNotNull(env.tracker.advance(pending)).stage)

        val expired = checkNotNull(env.tracker.advance(expire(env.journal.list(WALLET_ID).single())))

        assertEquals(PerpsDepositStage.FAILED, expired.stage)
        assertEquals("expired", expired.failureReason)
        assertEquals(1, env.endpoints.count { it == GET_NEXT_NONCE })
    }

    @Test
    fun aSendStillAliveOnTheServersClockIsNotExpiredByADeviceClockAhead() = runTest {
        env.serverTime.offsetMs = -DEVICE_CLOCK_SKEW_MS
        env.answer(SEND_TRANSACTIONS) { env.refused(HTTP_BAD_REQUEST, code = "bad_request", reason = "resign_required") }
        lighterSees(LighterTransactionPresence.UNKNOWN)
        val plan = env.withdrawService.prepare(WALLET_ID, AMOUNT, PerpsFundingAssetId.USDC_ETHEREUM)
        failureOf { env.withdrawService.submit(plan, wallet) }

        val pending = env.journal.list(WALLET_ID).single()

        assertEquals(PerpsDepositStage.SOURCE_UNKNOWN, checkNotNull(env.tracker.advance(pending)).stage)
    }

    @Test
    fun aRetryRefusedForAnotherReasonKeepsBothAttemptsTracked() = runTest {
        var sends = 0
        env.answer(SEND_TRANSACTIONS) {
            if (sends++ == 0) {
                env.refused(HTTP_BAD_REQUEST, code = "bad_request", reason = "resign_required")
            } else {
                env.refused(HTTP_BAD_REQUEST, code = "validation_error", reason = "lighter_rejected")
            }
        }
        lighterSees(LighterTransactionPresence.UNKNOWN)
        val plan = env.withdrawService.prepare(WALLET_ID, AMOUNT, PerpsFundingAssetId.USDC_ETHEREUM)

        val unknown = failureOf { env.withdrawService.submit(plan, wallet) }

        assertTrue(unknown is PerpsWithdrawException.SendUnknown)
        val pending = env.journal.list(WALLET_ID).single()
        assertEquals(PerpsDepositStage.SOURCE_UNKNOWN, pending.stage)
        assertEquals(2, pending.steps.size)
    }

    @Test
    fun aRetryLeftUnresolvedIsRecoveredByEitherAttempt() = runTest {
        var sends = 0
        env.answer(SEND_TRANSACTIONS) {
            if (sends++ == 0) {
                env.refused(HTTP_BAD_REQUEST, code = "bad_request", reason = "resign_required")
            } else {
                throw IOException("timeout")
            }
        }
        lighterSees(LighterTransactionPresence.KNOWN)
        val plan = env.withdrawService.prepare(WALLET_ID, AMOUNT, PerpsFundingAssetId.USDC_ETHEREUM)

        val unknown = failureOf { env.withdrawService.submit(plan, wallet) }

        assertTrue(unknown is PerpsWithdrawException.SendUnknown)
        val pending = env.journal.list(WALLET_ID).single()
        assertEquals(PerpsDepositStage.SOURCE_UNKNOWN, pending.stage)
        assertEquals(2, pending.steps.size)

        val settled = checkNotNull(env.tracker.advance(pending))

        assertEquals(PerpsDepositStage.ACCEPTED, settled.stage)
        assertEquals(pending.steps.first().txHash, settled.sourceTxHash)
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
    fun aWithdrawalRateLimitIsARefusalAndKeepsItsReason() = runTest {
        env.answer(SEND_TRANSACTIONS) { env.refused(429, code = "validation_error", reason = "withdrawal_rate_limited") }
        val plan = env.withdrawService.prepare(WALLET_ID, AMOUNT, PerpsFundingAssetId.USDC_ETHEREUM)

        val failure = failureOf { env.withdrawService.submit(plan, wallet) } as PerpsWithdrawException.Refused

        assertEquals(PerpsSendReason.WITHDRAWAL_RATE_LIMITED, failure.reason)
        assertEquals(PerpsDepositStage.FAILED, env.journal.list(WALLET_ID).single().stage)
        assertEquals(1, env.requests.count { it.endpoint == SEND_TRANSACTIONS })
    }

    @Test
    fun aWithdrawalRateLimitOnReplayDoesNotRejectAnEarlierUnknownSend() = runTest {
        env.answer(SEND_TRANSACTIONS) { throw IOException("timeout") }
        val plan = env.withdrawService.prepare(WALLET_ID, AMOUNT, PerpsFundingAssetId.USDC_ETHEREUM)
        assertTrue(failureOf { env.withdrawService.submit(plan, wallet) } is PerpsWithdrawException.SendUnknown)
        val pending = env.journal.list(WALLET_ID).single()
        env.answer(SEND_TRANSACTIONS) { env.refused(429, code = "withdrawal_rate_limited", reason = null) }

        val replay = env.tracker.send(pending, firstAttempt = false)

        assertEquals(PerpsDepositStage.SOURCE_UNKNOWN, replay.record.stage)
        assertEquals(PerpsDepositStage.SOURCE_UNKNOWN, env.journal.list(WALLET_ID).single().stage)
    }

    @Test
    fun tooManyRequestsFailsTheWithdrawalWithoutSendingItAgain() = runTest {
        env.answer(SEND_TRANSACTIONS) { PerpsFundingTestEnv.Answer(429, """{"code":"rate_limited"}""") }
        val plan = env.withdrawService.prepare(WALLET_ID, AMOUNT, PerpsFundingAssetId.USDC_ETHEREUM)

        val failure = failureOf { env.withdrawService.submit(plan, wallet) } as PerpsWithdrawException.Refused

        assertEquals(PerpsSendReason.RATE_LIMITED, failure.reason)
        val failed = env.journal.list(WALLET_ID).single()
        assertEquals(PerpsDepositStage.FAILED, failed.stage)
        assertNull(env.tracker.advance(failed))
        assertEquals(1, env.requests.count { it.endpoint == SEND_TRANSACTIONS })
    }

    @Test
    fun tooManyRequestsOnReplayDoesNotRejectAnEarlierUnknownSend() = runTest {
        env.answer(SEND_TRANSACTIONS) { throw IOException("timeout") }
        val plan = env.withdrawService.prepare(WALLET_ID, AMOUNT, PerpsFundingAssetId.USDC_ETHEREUM)
        assertTrue(failureOf { env.withdrawService.submit(plan, wallet) } is PerpsWithdrawException.SendUnknown)
        val pending = env.journal.list(WALLET_ID).single()
        env.answer(SEND_TRANSACTIONS) { PerpsFundingTestEnv.Answer(429, """{"code":"rate_limited"}""") }

        val replay = env.tracker.send(pending, firstAttempt = false)

        assertEquals(PerpsDepositStage.SOURCE_UNKNOWN, replay.record.stage)
        assertEquals(PerpsDepositStage.SOURCE_UNKNOWN, env.journal.list(WALLET_ID).single().stage)
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
    fun aQuoteAboutToExpireIsRenewedAndSentWhenItGivesMore() = runTest {
        env.quoteWithdrawals(received = RECEIVED)
        val plan = env.withdrawService.prepare(WALLET_ID, AMOUNT, TestAssets.usdtEth.id)
        val aging = plan.withQuoteExpiringIn(5_000L)
        env.quoteWithdrawals(received = RECEIVED + BigInteger.ONE)
        env.requests.clear()

        val record = env.withdrawService.submit(aging, wallet)

        assertEquals(listOf(GET_PORTFOLIO, QUOTE_WITHDRAW, GET_NEXT_NONCE, SEND_TRANSACTIONS), env.endpoints)
        assertEquals(1, env.signedTransfers.size)
        assertEquals((RECEIVED + BigInteger.ONE).toString(), record.targetAmount)
        assertEquals(PerpsDepositStage.ACCEPTED, record.stage)
    }

    @Test
    fun anExpiredQuoteIsRenewedAndSentWhenItGivesTheSame() = runTest {
        env.quoteWithdrawals(received = RECEIVED)
        val plan = env.withdrawService.prepare(WALLET_ID, AMOUNT, TestAssets.usdtEth.id)
        val expired = plan.withQuoteExpiringIn(-1L)
        env.requests.clear()

        val record = env.withdrawService.submit(expired, wallet)

        assertEquals(listOf(GET_PORTFOLIO, QUOTE_WITHDRAW, GET_NEXT_NONCE, SEND_TRANSACTIONS), env.endpoints)
        assertEquals(RECEIVED.toString(), record.targetAmount)
        assertEquals(PerpsDepositStage.ACCEPTED, record.stage)
    }

    @Test
    fun aRenewedQuoteWithinTheToleranceIsSent() = runTest {
        env.quoteWithdrawals(received = RECEIVED)
        val plan = env.withdrawService.prepare(WALLET_ID, AMOUNT, TestAssets.usdtEth.id)
        env.quoteWithdrawals(received = LOWEST_ACCEPTED)
        env.requests.clear()

        val record = env.withdrawService.submit(plan.withQuoteExpiringIn(5_000L), wallet)

        assertEquals(listOf(GET_PORTFOLIO, QUOTE_WITHDRAW, GET_NEXT_NONCE, SEND_TRANSACTIONS), env.endpoints)
        assertEquals(LOWEST_ACCEPTED.toString(), record.targetAmount)
        assertEquals(PerpsDepositStage.ACCEPTED, record.stage)
    }

    @Test
    fun aRenewedQuoteBelowTheToleranceIsHandedBackUnsignedAndSentOnTheNextConfirm() = runTest {
        env.quoteWithdrawals(received = RECEIVED)
        val plan = env.withdrawService.prepare(WALLET_ID, AMOUNT, TestAssets.usdtEth.id)
        val aging = plan.withQuoteExpiringIn(5_000L)
        val less = LOWEST_ACCEPTED - BigInteger.ONE
        env.quoteWithdrawals(received = less)

        val failure = failureOf { env.withdrawService.submit(aging, wallet) }

        val renewed = (failure as PerpsWithdrawException.QuoteChanged).plan
        assertEquals(less, renewed.quote.outputUnits)
        assertEquals(AMOUNT, renewed.amountUnits)
        assertNotNull(renewed.cost)
        assertTrue(env.signedTransfers.isEmpty())
        assertTrue(SEND_TRANSACTIONS !in env.endpoints)
        assertTrue(env.journal.list(WALLET_ID).isEmpty())

        env.requests.clear()
        val record = env.withdrawService.submit(renewed, wallet)

        assertEquals(listOf(GET_PORTFOLIO, GET_NEXT_NONCE, SEND_TRANSACTIONS), env.endpoints)
        assertEquals(less.toString(), record.targetAmount)
        assertEquals(listOf(record), env.journal.list(WALLET_ID))
    }

    @Test
    fun aQuoteTooShortToSignInIsNeverSigned() = runTest {
        env.quoteWithdrawals(received = RECEIVED)
        val plan = env.withdrawService.prepare(WALLET_ID, AMOUNT, TestAssets.usdtEth.id)
        env.quoteWithdrawals(received = RECEIVED, lifetimeMs = 3_000L)

        val failure = failureOf { env.withdrawService.submit(plan.withQuoteExpiringIn(-1L), wallet) }

        assertTrue(failure is PerpsWithdrawException.QuoteExpired)
        assertTrue(env.signedTransfers.isEmpty())
        assertTrue(SEND_TRANSACTIONS !in env.endpoints)
        assertTrue(env.journal.list(WALLET_ID).isEmpty())
    }

    @Test
    fun aQuoteLastsItsLifetimeFromTheRequestOnTheServersClock() = runTest {
        env.serverTime.offsetMs = -DEVICE_CLOCK_SKEW_MS
        // Whatever the clock the quote's own timestamps are on.
        listOf(-10 * 60_000L, 10 * 60_000L).forEach { quoteClockOffsetMs ->
            val quotedAtMs = env.serverTime.currentTimeMillis() + quoteClockOffsetMs
            env.quoteWithdrawals(received = RECEIVED, expiresAtMs = quotedAtMs + 30_000L, lifetimeMs = 30_000L)
            val askedAtMs = env.serverTime.currentTimeMillis()

            val plan = env.withdrawService.prepare(WALLET_ID, AMOUNT, TestAssets.usdtEth.id)

            val expiresAtMs = plan.quote.expiresAtMs
            assertTrue("$quoteClockOffsetMs", expiresAtMs in askedAtMs + 30_000L..env.serverTime.currentTimeMillis() + 30_000L)
        }
    }

    @Test
    fun aQuoteWithoutALifetimeIsExpired() = runTest {
        env.quoteWithdrawals(received = RECEIVED, lifetimeMs = 0L)

        val failure = failureOf { env.withdrawService.prepare(WALLET_ID, AMOUNT, TestAssets.usdtEth.id) }

        assertTrue(failure is PerpsWithdrawQuoteException.Expired)
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
    fun aTransferWhoseLighterFeeRoseIsRenewedAndHandedBackUnsignedAndSentOnTheNextConfirm() = runTest {
        env.quoteWithdrawals(received = RECEIVED)
        val plan = env.withdrawService.prepare(WALLET_ID, AMOUNT, TestAssets.usdtEth.id)
        val currentFee = TRANSFER_FEE_UNITS + BigInteger.ONE
        coEvery { env.lighterApi.transferFeeUnits(ACCOUNT_INDEX, AGGREGATOR_ACCOUNT_INDEX) } returns currentFee
        env.quoteWithdrawals(received = RECEIVED, transferFeeUnits = currentFee)

        val failure = failureOf { env.withdrawService.submit(plan, wallet) }

        val renewed = (failure as PerpsWithdrawException.QuoteChanged).plan
        assertEquals(currentFee, renewed.quote.transfer.usdcFeeUnits)
        assertTrue(env.signedTransfers.isEmpty())
        assertTrue(SEND_TRANSACTIONS !in env.endpoints)
        assertTrue(env.journal.list(WALLET_ID).isEmpty())

        env.requests.clear()
        val record = env.withdrawService.submit(renewed, wallet)

        assertEquals(listOf(GET_PORTFOLIO, GET_NEXT_NONCE, SEND_TRANSACTIONS), env.endpoints)
        assertEquals(currentFee.toLong(), env.signedTransfers.single().request.usdcFee)
        assertEquals(PerpsDepositStage.ACCEPTED, record.stage)
    }

    @Test
    fun aTransferWhoseLighterFeeFellIsRenewedAndSentWithTheCurrentFee() = runTest {
        env.quoteWithdrawals(received = RECEIVED)
        val plan = env.withdrawService.prepare(WALLET_ID, AMOUNT, TestAssets.usdtEth.id)
        val currentFee = TRANSFER_FEE_UNITS - BigInteger.ONE
        coEvery { env.lighterApi.transferFeeUnits(ACCOUNT_INDEX, AGGREGATOR_ACCOUNT_INDEX) } returns currentFee
        env.quoteWithdrawals(received = RECEIVED, transferFeeUnits = currentFee)
        env.requests.clear()

        val record = env.withdrawService.submit(plan, wallet)

        assertEquals(listOf(GET_PORTFOLIO, QUOTE_WITHDRAW, GET_NEXT_NONCE, SEND_TRANSACTIONS), env.endpoints)
        assertEquals(currentFee.toLong(), env.signedTransfers.single().request.usdcFee)
        assertEquals(PerpsDepositStage.ACCEPTED, record.stage)
    }

    @Test
    fun aRenewedQuoteWithAHigherTransferFeeIsHandedBackUnsigned() = runTest {
        env.quoteWithdrawals(received = RECEIVED)
        val plan = env.withdrawService.prepare(WALLET_ID, AMOUNT, TestAssets.usdtEth.id)
        val higherFee = TRANSFER_FEE_UNITS + BigInteger.ONE
        coEvery { env.lighterApi.transferFeeUnits(ACCOUNT_INDEX, AGGREGATOR_ACCOUNT_INDEX) } returns higherFee
        env.quoteWithdrawals(received = RECEIVED + BigInteger.ONE, transferFeeUnits = higherFee)

        val failure = failureOf { env.withdrawService.submit(plan.withQuoteExpiringIn(5_000L), wallet) }

        val renewed = (failure as PerpsWithdrawException.QuoteChanged).plan
        assertEquals(higherFee, renewed.quote.transfer.usdcFeeUnits)
        assertTrue(env.signedTransfers.isEmpty())
        assertTrue(SEND_TRANSACTIONS !in env.endpoints)
        assertTrue(env.journal.list(WALLET_ID).isEmpty())
    }

    @Test
    fun aRenewedQuoteThatSpendsMoreThanTheShownOneIsHandedBackUnsigned() = runTest {
        env.quoteWithdrawals(received = RECEIVED, unspentUnits = UNSPENT)
        val plan = env.withdrawService.prepare(WALLET_ID, AMOUNT, TestAssets.usdtEth.id)
        env.quoteWithdrawals(received = RECEIVED)

        val failure = failureOf { env.withdrawService.submit(plan.withQuoteExpiringIn(5_000L), wallet) }

        val renewed = (failure as PerpsWithdrawException.QuoteChanged).plan
        assertEquals(AMOUNT, renewed.quote.transfer.spentUnits)
        assertTrue(env.signedTransfers.isEmpty())
        assertTrue(SEND_TRANSACTIONS !in env.endpoints)
        assertTrue(env.journal.list(WALLET_ID).isEmpty())
    }

    @Test
    fun aRenewedQuoteThatSpendsLessIsSent() = runTest {
        env.quoteWithdrawals(received = RECEIVED)
        val plan = env.withdrawService.prepare(WALLET_ID, AMOUNT, TestAssets.usdtEth.id)
        env.quoteWithdrawals(received = RECEIVED, unspentUnits = UNSPENT)
        env.requests.clear()

        val record = env.withdrawService.submit(plan.withQuoteExpiringIn(5_000L), wallet)

        assertEquals(listOf(GET_PORTFOLIO, QUOTE_WITHDRAW, GET_NEXT_NONCE, SEND_TRANSACTIONS), env.endpoints)
        val signed = env.signedTransfers.single().request
        assertEquals((AMOUNT - UNSPENT).toLong(), signed.amount + signed.usdcFee)
        assertEquals(PerpsDepositStage.ACCEPTED, record.stage)
    }

    @Test
    fun aQuoteThatTransfersToAnAccountOtherThanRelaysIsRefused() = runTest {
        env.quoteWithdrawals(received = RECEIVED, toAccountIndex = OTHER_LIGHTER_ACCOUNT)

        val failure = failureOf { env.withdrawService.prepare(WALLET_ID, AMOUNT, TestAssets.usdtEth.id) }

        assertTrue(failure is PerpsWithdrawQuoteException.NotToRelay)
    }

    @Test
    fun aRenewedQuoteThatTransfersToAnAccountOtherThanRelaysIsNeverSigned() = runTest {
        env.quoteWithdrawals(received = RECEIVED)
        val plan = env.withdrawService.prepare(WALLET_ID, AMOUNT, TestAssets.usdtEth.id)
        env.quoteWithdrawals(received = RECEIVED + BigInteger.ONE, toAccountIndex = OTHER_LIGHTER_ACCOUNT)

        val failure = failureOf { env.withdrawService.submit(plan.withQuoteExpiringIn(5_000L), wallet) }

        assertTrue(failure is PerpsWithdrawQuoteException.NotToRelay)
        assertTrue(env.signedTransfers.isEmpty())
        assertTrue(SEND_TRANSACTIONS !in env.endpoints)
        assertTrue(env.journal.list(WALLET_ID).isEmpty())
    }

    @Test
    fun withoutAVerifiedRelayAccountNoWithdrawalIsQuoted() = runTest {
        val unverified = listOf(
            null,
            env.securityBlock(relayDepositories = null),
            env.securityBlock(relayDepositories = listOf("eth" to JsonPrimitive(PerpsSecurityBlocks.RELAY_EVM_DEPOSITORY))),
            PerpsSecurityBlocks.signed(PerpsSecurityBlocks.newKey()),
        )
        unverified.forEach { block ->
            env = PerpsFundingTestEnv()
            env.quoteWithdrawals(received = RECEIVED)
            env.servedSecurityBlock = block

            val failure = failureOf { env.withdrawService.prepare(WALLET_ID, AMOUNT, TestAssets.usdtEth.id) }

            assertTrue("$block", failure is PerpsWithdrawException.RelayUnverified)
            assertTrue("$block", QUOTE_WITHDRAW !in env.endpoints)
        }
    }

    @Test
    fun aShownQuoteToAnAccountThatLeftTheSecurityConfigIsQuotedAgainBeforeAnythingIsSigned() = runTest {
        env.quoteWithdrawals(received = RECEIVED)
        val plan = env.withdrawService.prepare(WALLET_ID, AMOUNT, TestAssets.usdtEth.id)
        env.servedSecurityBlock = env.securityBlock(
            relayDepositories = listOf("lighter" to JsonPrimitive(OTHER_LIGHTER_ACCOUNT.toString())),
        )
        env.requests.clear()

        val failure = failureOf { env.withdrawService.submit(plan, wallet) }

        assertTrue(failure is PerpsWithdrawException.QuoteExpired)
        coVerify(exactly = 0) { env.accountService.ensureActivated(any(), any()) }
        assertEquals(listOf(GET_PORTFOLIO), env.endpoints)
        assertTrue(env.signedTransfers.isEmpty())
        assertTrue(SEND_TRANSACTIONS !in env.endpoints)
        assertTrue(env.journal.list(WALLET_ID).isEmpty())
    }

    @Test
    fun aRelayAccountDroppedWhileTheAccountActivatesIsNeverSigned() = runTest {
        env.quoteWithdrawals(received = RECEIVED)
        val plan = env.withdrawService.prepare(WALLET_ID, AMOUNT, TestAssets.usdtEth.id)
        val credentials = (env.accountService.ensureActivated(WALLET_ID) { wallet } as PerpsActivation.Ready).credentials
        coEvery { env.accountService.ensureActivated(WALLET_ID, any()) } coAnswers {
            env.servedSecurityBlock = env.securityBlock(
                relayDepositories = listOf("lighter" to JsonPrimitive(OTHER_LIGHTER_ACCOUNT.toString())),
            )
            PerpsActivation.Ready(credentials)
        }

        val failure = failureOf { env.withdrawService.submit(plan, wallet) }

        assertTrue(failure is PerpsWithdrawException.QuoteExpired)
        assertTrue(env.signedTransfers.isEmpty())
        assertTrue(SEND_TRANSACTIONS !in env.endpoints)
    }

    @Test
    fun anAmountOverTheBalanceIsRefusedAsSuchEvenWithoutARelayAccount() = runTest {
        env.quoteWithdrawals(received = RECEIVED)
        val plan = env.withdrawService.prepare(WALLET_ID, AMOUNT, TestAssets.usdtEth.id)
        env.servedSecurityBlock = env.securityBlock(relayDepositories = emptyList())
        env.answer(GET_PORTFOLIO) {
            env.ok(buildJsonObject { putJsonObject("balance") { put("transferable_balance", "1") } })
        }

        val failure = failureOf { env.withdrawService.submit(plan, wallet) }

        assertTrue(failure is PerpsWithdrawException.InsufficientBalance)
    }

    @Test
    fun aShownQuoteIsNeverSignedOnceTheSecurityConfigNamesNoRelayAccount() = runTest {
        env.quoteWithdrawals(received = RECEIVED)
        val plan = env.withdrawService.prepare(WALLET_ID, AMOUNT, TestAssets.usdtEth.id)
        env.servedSecurityBlock = env.securityBlock(relayDepositories = emptyList())

        val failure = failureOf { env.withdrawService.submit(plan, wallet) }

        assertTrue(failure is PerpsWithdrawException.RelayUnverified)
        coVerify(exactly = 0) { env.accountService.ensureActivated(any(), any()) }
        assertTrue(env.signedTransfers.isEmpty())
        assertTrue(SEND_TRANSACTIONS !in env.endpoints)
    }

    @Test
    fun anExpiredQuoteWhoseAccountLeftTheSecurityConfigIsQuotedAgainUnsigned() = runTest {
        env.quoteWithdrawals(received = RECEIVED)
        val plan = env.withdrawService.prepare(WALLET_ID, AMOUNT, TestAssets.usdtEth.id)
        env.servedSecurityBlock = env.securityBlock(
            relayDepositories = listOf("lighter" to JsonPrimitive(OTHER_LIGHTER_ACCOUNT.toString())),
        )
        env.requests.clear()

        val failure = failureOf { env.withdrawService.submit(plan.withQuoteExpiringIn(-1L), wallet) }

        assertTrue(failure is PerpsWithdrawException.QuoteExpired)
        assertTrue(env.signedTransfers.isEmpty())
        assertTrue(SEND_TRANSACTIONS !in env.endpoints)
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
    fun destinationsFailToLoadWhenTheWalletCannotFindUsdcOnEthereum() = runTest {
        env.accounts.remove(PerpsFundingTestEnv.USDC.id)

        val failure = failureOf { env.withdrawService.destinations(WALLET_ID) }

        assertTrue(failure is PerpsWithdrawException.DestinationUnavailable)
    }

    @Test
    fun withoutAPerpsAccountThereAreNoDestinations() = runTest {
        env.answer(GET_ACCOUNT) { env.refused(HTTP_BAD_REQUEST, code = "no_lighter_account", reason = null) }

        val failure = failureOf { env.withdrawService.destinations(WALLET_ID) }

        assertTrue(failure is PerpsWithdrawException.AccountUnavailable)
    }

    @Test
    fun destinationsAreOrderedByTheirDollarValueLargestFirst() = runTest {
        val destinations = env.withdrawService.destinations(WALLET_ID)

        assertEquals(
            listOf(TestAssets.eth.id, PerpsFundingAssetId.USDC_ETHEREUM, TestAssets.usdtEth.id).map { it.lowercase() },
            destinations.map { it.assetId.lowercase() },
        )
        coVerify { env.mcAccountRepository.findAccount(WALLET_ID, PerpsFundingAssetId.USDC_ETHEREUM, USD_CURRENCY, any()) }
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
    fun aLighterWithdrawalAcceptedBeforeTheUpdateStillWaitsForThePayoutWithoutAReport() = runTest {
        val plan = env.withdrawService.prepare(WALLET_ID, AMOUNT, PerpsFundingAssetId.USDC_ETHEREUM)
        val sent = env.withdrawService.submit(plan, wallet)
        val record = env.journal.update(sent.operationId, sent.revision) {
            it.copy(route = PerpsDepositRoute.DIRECT_GATEWAY, quoteId = null, executionId = null)
        }
        env.requests.clear()

        val awaiting = checkNotNull(env.tracker.advance(record))

        // /transactions/send already relayed it: nothing goes to /transactions.
        assertTrue(env.endpoints.isEmpty())
        assertEquals(PerpsDepositStage.AWAITING_L1, awaiting.stage)
        assertNull(awaiting.failureReason)
    }

    private fun lighterSees(presence: LighterTransactionPresence) {
        coEvery { env.lighterApi.transaction(any()) } returns LighterTransaction(presence, executedAtMs = null)
    }

    private suspend fun expire(record: PerpsFundingRecord): PerpsFundingRecord {
        val expiredAt = env.serverTime.currentTimeMillis() - EXPIRED_AGO_MS
        return env.journal.update(record.operationId, record.revision) { current ->
            current.copy(
                steps = current.steps.map {
                    it.copy(signedData = it.signedData.replace(Regex("\"ExpiredAt\":\\d+"), "\"ExpiredAt\":$expiredAt"))
                },
            )
        }
    }

    private fun sentTransaction(): JsonObject {
        val sent = checkNotNull(env.requests.last { it.endpoint == SEND_TRANSACTIONS }.body)
        return sent.getValue("transactions").jsonArray.single().jsonObject
    }

    private fun PerpsWithdrawPlan.withQuoteExpiringIn(ms: Long): PerpsWithdrawPlan {
        return copy(quote = quote.copy(expiresAtMs = env.serverTime.currentTimeMillis() + ms))
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
        const val EXPIRED_AGO_MS = 120_000L
        const val DEVICE_CLOCK_SKEW_MS = 60L * 60L * 1_000L
        val AMOUNT: BigInteger = BigInteger.valueOf(10_000_000L)
        val RECEIVED: BigInteger = BigInteger.valueOf(9_800_000L)
        val LOWEST_ACCEPTED: BigInteger = BigInteger.valueOf(9_775_500L)
        val UNSPENT: BigInteger = BigInteger.valueOf(100_000L)
    }
}
