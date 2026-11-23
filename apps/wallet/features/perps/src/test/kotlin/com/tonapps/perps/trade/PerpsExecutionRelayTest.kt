package com.tonapps.perps.trade

import com.tonapps.chainkit.lighter.trading.PerpsExecutionErrorKind
import com.tonapps.chainkit.lighter.trading.PerpsExecutionException
import com.tonapps.chainkit.lighter.trading.PerpsSignedStep
import com.tonapps.chainkit.lighter.trading.PerpsSubmissionOutcome
import com.tonapps.chainkit.perpetual.PerpsScope
import com.tonapps.perps.funding.PerpsFundingTestEnv
import com.tonapps.perps.funding.PerpsFundingTestEnv.Companion.ACCOUNT_INDEX
import com.tonapps.perps.funding.PerpsFundingTestEnv.Companion.GET_NEXT_NONCE
import com.tonapps.perps.funding.PerpsFundingTestEnv.Companion.HTTP_BAD_REQUEST
import com.tonapps.perps.funding.PerpsFundingTestEnv.Companion.HTTP_UNAVAILABLE
import com.tonapps.perps.funding.PerpsFundingTestEnv.Companion.SEND_TRANSACTIONS
import com.tonapps.perps.funding.PerpsFundingTestEnv.Companion.WALLET_ID
import com.tonapps.wallet.data.multichain.perps.PerpsOperationJournal
import com.tonapps.wallet.testing.BaseUnitTest
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class PerpsExecutionRelayTest : BaseUnitTest() {

    private lateinit var env: PerpsFundingTestEnv
    private lateinit var relay: PerpsExecutionRelay
    private var backendNonce = FIRST_NONCE
    private var nonceUnavailable = false

    @Before
    fun setUp() {
        env = PerpsFundingTestEnv()
        env.answer(GET_NEXT_NONCE) {
            if (nonceUnavailable) {
                return@answer PerpsFundingTestEnv.Answer(HTTP_UNAVAILABLE, "{}")
            }
            env.ok(
                buildJsonObject {
                    put("account_index", ACCOUNT_INDEX)
                    put("nonce", backendNonce)
                },
            )
        }
        env.answer(SEND_TRANSACTIONS) {
            env.refused(HTTP_BAD_REQUEST, code = "bad_request", reason = "resign_required")
        }
        relay = PerpsExecutionRelay(env.repository, mockk<PerpsOperationJournal>(), PerpsNonceCoordinator())
    }

    @Test
    fun aRefusedStepIsSignedAgainWithItsOwnNonce() = runTest {
        assertEquals(FIRST_NONCE, relay.nextNonce(SCOPE).value)
        backendNonce = LATER_BACKEND_NONCE

        assertEquals(PerpsSubmissionOutcome.ResignRequired, relay.submit(step()))
        relay.resyncNonce(SCOPE)

        assertEquals(FIRST_NONCE, relay.nextNonce(SCOPE).value)
        assertEquals(1, env.endpoints.count { it == GET_NEXT_NONCE })
    }

    @Test
    fun aRetryRefusedAgainIsReconciledInsteadOfSignedWithANewNonce() = runTest {
        refuseFirstAttempt()

        val failure = submitFailure(step(txHash = RETRY_HASH))

        assertEquals(PerpsExecutionErrorKind.SubmitUnknown, failure.kind)
    }

    @Test
    fun aRetryRefusedForAnotherReasonIsReconciledToo() = runTest {
        refuseFirstAttempt()
        env.answer(SEND_TRANSACTIONS) {
            env.refused(HTTP_BAD_REQUEST, code = "validation_error", reason = "lighter_rejected")
        }

        val failure = submitFailure(step(txHash = RETRY_HASH))

        assertEquals(PerpsExecutionErrorKind.SubmitUnknown, failure.kind)
    }

    @Test
    fun theNextOperationAfterASecondRefusalReadsTheBackendNonce() = runTest {
        refuseFirstAttempt()
        submitFailure(step(txHash = RETRY_HASH))
        backendNonce = LATER_BACKEND_NONCE

        assertEquals(LATER_BACKEND_NONCE, relay.nextNonce(SCOPE).value)
    }

    @Test
    fun aRefusalOfAnotherStepIsNotTakenForARetry() = runTest {
        relay.nextNonce(SCOPE)
        assertEquals(PerpsSubmissionOutcome.ResignRequired, relay.submit(step()))

        val next = step(stepId = "step-2", txHash = RETRY_HASH)

        assertEquals(PerpsSubmissionOutcome.ResignRequired, relay.submit(next))
    }

    @Test
    fun aRetryThatGoesThroughLeavesLaterResyncsToTheBackend() = runTest {
        refuseFirstAttempt()
        env.answer(SEND_TRANSACTIONS) { request -> env.ok(env.accepted(request)) }
        assertEquals(PerpsSubmissionOutcome.Accepted, relay.submit(step(txHash = RETRY_HASH)))
        backendNonce = LATER_BACKEND_NONCE

        relay.resyncNonce(SCOPE)

        assertEquals(LATER_BACKEND_NONCE, relay.nextNonce(SCOPE).value)
    }

    @Test
    fun aRefusedNonceIsReusedOnceOnly() = runTest {
        refuseFirstAttempt()
        backendNonce = LATER_BACKEND_NONCE

        relay.resyncNonce(SCOPE)

        assertEquals(LATER_BACKEND_NONCE, relay.nextNonce(SCOPE).value)
    }

    @Test
    fun anotherWalletOnTheSameAccountDoesNotTakeTheRefusedNonce() = runTest {
        relay.nextNonce(SCOPE)
        assertEquals(PerpsSubmissionOutcome.ResignRequired, relay.submit(step()))
        val otherWallet = step(stepId = "step-2", txHash = RETRY_HASH, scope = OTHER_WALLET_SCOPE, nonce = LATER_BACKEND_NONCE)
        assertEquals(PerpsSubmissionOutcome.ResignRequired, relay.submit(otherWallet))

        relay.resyncNonce(SCOPE)

        assertEquals(FIRST_NONCE, relay.nextNonce(SCOPE).value)
    }

    @Test
    fun aRetryDoesNotRewindTheNonceAnotherWalletAlreadyTook() = runTest {
        assertEquals(FIRST_NONCE, relay.nextNonce(SCOPE).value)
        assertEquals(PerpsSubmissionOutcome.ResignRequired, relay.submit(step()))
        assertEquals(FIRST_NONCE + 1, relay.nextNonce(OTHER_WALLET_SCOPE).value)

        relay.resyncNonce(SCOPE)

        assertEquals(FIRST_NONCE, relay.nextNonce(SCOPE).value)
        assertEquals(FIRST_NONCE + 2, relay.nextNonce(OTHER_WALLET_SCOPE).value)
    }

    @Test
    fun aResyncWithoutARefusedStepReadsTheBackend() = runTest {
        assertEquals(FIRST_NONCE, relay.nextNonce(SCOPE).value)
        backendNonce = LATER_BACKEND_NONCE

        relay.resyncNonce(SCOPE)

        assertEquals(LATER_BACKEND_NONCE, relay.nextNonce(SCOPE).value)
    }

    @Test
    fun aSendRefusedForAnotherReasonHandsItsNonceToTheNextStep() = runTest {
        assertEquals(FIRST_NONCE, relay.nextNonce(SCOPE).value)
        env.answer(SEND_TRANSACTIONS) {
            env.refused(HTTP_BAD_REQUEST, code = "validation_error", reason = "insufficient_funds")
        }

        assertEquals(PerpsExecutionErrorKind.ServerRejected, submitFailure(step()).kind)

        assertEquals(FIRST_NONCE, relay.nextNonce(SCOPE).value)
    }

    @Test
    fun tooManyRequestsIsARefusalThatHandsItsNonceToTheNextStep() = runTest {
        assertEquals(FIRST_NONCE, relay.nextNonce(SCOPE).value)
        env.answer(SEND_TRANSACTIONS) { PerpsFundingTestEnv.Answer(429, """{"code":"rate_limited"}""") }

        assertEquals(PerpsExecutionErrorKind.ServerRejected, submitFailure(step()).kind)

        assertEquals(FIRST_NONCE, relay.nextNonce(SCOPE).value)
    }

    @Test
    fun aCancelOfAnOrderAlreadyGoneHandsItsNonceToTheNextStep() = runTest {
        assertEquals(FIRST_NONCE, relay.nextNonce(SCOPE).value)
        env.answer(SEND_TRANSACTIONS) {
            env.refused(HTTP_BAD_REQUEST, code = "validation_error", reason = "order_gone")
        }

        assertEquals(PerpsExecutionErrorKind.Unknown, submitFailure(step()).kind)

        assertEquals(FIRST_NONCE, relay.nextNonce(SCOPE).value)
    }

    @Test
    fun aSendTheBackendDidNotAuthorizeHandsItsNonceToTheNextStep() = runTest {
        assertEquals(FIRST_NONCE, relay.nextNonce(SCOPE).value)
        env.answer(SEND_TRANSACTIONS) { PerpsFundingTestEnv.Answer(HTTP_UNAUTHORIZED, "{}") }

        submitFailure(step())

        assertEquals(FIRST_NONCE, relay.nextNonce(SCOPE).value)
    }

    @Test
    fun catchingUpDropsARetryNonceAnAbandonedRetryLeftBehind() = runTest {
        relay.nextNonce(SCOPE)
        assertEquals(PerpsSubmissionOutcome.ResignRequired, relay.submit(step()))
        relay.resyncNonce(SCOPE)
        backendNonce = LATER_BACKEND_NONCE

        relay.catchUpNonce(SCOPE)

        assertEquals(LATER_BACKEND_NONCE, relay.nextNonce(SCOPE).value)
    }

    @Test
    fun aSendWithAnUnknownOutcomeKeepsItsNonce() = runTest {
        assertEquals(FIRST_NONCE, relay.nextNonce(SCOPE).value)
        env.answer(SEND_TRANSACTIONS) { PerpsFundingTestEnv.Answer(HTTP_UNAVAILABLE, "{}") }

        submitFailure(step())

        assertEquals(FIRST_NONCE + 1, relay.nextNonce(SCOPE).value)
    }

    @Test
    fun aRefusedNonceIsNotHandedBackOnceALaterOneWasIssued() = runTest {
        assertEquals(FIRST_NONCE, relay.nextNonce(SCOPE).value)
        assertEquals(FIRST_NONCE + 1, relay.nextNonce(OTHER_WALLET_SCOPE).value)
        env.answer(SEND_TRANSACTIONS) {
            env.refused(HTTP_BAD_REQUEST, code = "validation_error", reason = "lighter_rejected")
        }

        submitFailure(step())

        assertEquals(FIRST_NONCE + 2, relay.nextNonce(SCOPE).value)
    }

    @Test
    fun aRetryRefusedForAnotherReasonDoesNotHandItsNonceBack() = runTest {
        refuseFirstAttempt()
        env.answer(SEND_TRANSACTIONS) {
            env.refused(HTTP_BAD_REQUEST, code = "validation_error", reason = "lighter_rejected")
        }
        submitFailure(step(txHash = RETRY_HASH))
        backendNonce = LATER_BACKEND_NONCE

        assertEquals(LATER_BACKEND_NONCE, relay.nextNonce(SCOPE).value)
    }

    @Test
    fun catchingUpTakesTheBackendNonceUsedElsewhere() = runTest {
        assertEquals(FIRST_NONCE, relay.nextNonce(SCOPE).value)
        backendNonce = LATER_BACKEND_NONCE

        relay.catchUpNonce(SCOPE)

        assertEquals(LATER_BACKEND_NONCE, relay.nextNonce(SCOPE).value)
    }

    @Test
    fun catchingUpDoesNotRewindPastAnAcceptedSendTheBackendHasNotCountedYet() = runTest {
        assertEquals(FIRST_NONCE, relay.nextNonce(SCOPE).value)
        env.answer(SEND_TRANSACTIONS) { request -> env.ok(env.accepted(request)) }
        assertEquals(PerpsSubmissionOutcome.Accepted, relay.submit(step()))

        relay.catchUpNonce(SCOPE)

        assertEquals(FIRST_NONCE + 1, relay.nextNonce(SCOPE).value)
    }

    @Test
    fun signingAfterAFailedCatchUpStaysAboveAnAcceptedSendTheBackendHasNotCountedYet() = runTest {
        assertEquals(FIRST_NONCE, relay.nextNonce(SCOPE).value)
        env.answer(SEND_TRANSACTIONS) { request -> env.ok(env.accepted(request)) }
        assertEquals(PerpsSubmissionOutcome.Accepted, relay.submit(step()))
        nonceUnavailable = true
        try {
            relay.catchUpNonce(SCOPE)
        } catch (_: PerpsExecutionException) {
        }
        nonceUnavailable = false

        assertEquals(FIRST_NONCE + 1, relay.nextNonce(SCOPE).value)
    }

    @Test
    fun aResyncStaysAboveAnAcceptedSendTheBackendHasNotCountedYet() = runTest {
        assertEquals(FIRST_NONCE, relay.nextNonce(SCOPE).value)
        env.answer(SEND_TRANSACTIONS) { request -> env.ok(env.accepted(request)) }
        assertEquals(PerpsSubmissionOutcome.Accepted, relay.submit(step()))

        relay.resyncNonce(SCOPE)

        assertEquals(FIRST_NONCE + 1, relay.nextNonce(SCOPE).value)
    }

    @Test
    fun catchingUpTakesBackANonceWhoseSendNeverReachedTheVenue() = runTest {
        assertEquals(FIRST_NONCE, relay.nextNonce(SCOPE).value)
        env.answer(SEND_TRANSACTIONS) { PerpsFundingTestEnv.Answer(HTTP_UNAVAILABLE, "{}") }
        submitFailure(step())

        relay.catchUpNonce(SCOPE)

        assertEquals(FIRST_NONCE, relay.nextNonce(SCOPE).value)
    }

    @Test
    fun catchingUpKeepsANonceAnotherWalletIssuedWhileTheBackendWasRead() = runTest {
        assertEquals(FIRST_NONCE, relay.nextNonce(SCOPE).value)
        env.answer(GET_NEXT_NONCE) {
            kotlinx.coroutines.runBlocking { relay.nextNonce(OTHER_WALLET_SCOPE) }
            env.ok(
                buildJsonObject {
                    put("account_index", ACCOUNT_INDEX)
                    put("nonce", FIRST_NONCE)
                },
            )
        }

        relay.catchUpNonce(SCOPE)

        assertEquals(FIRST_NONCE + 2, relay.nextNonce(SCOPE).value)
    }

    @Test
    fun aCatchUpTheBackendCouldNotAnswerDropsTheCursorSoSigningReadsItAgain() = runTest {
        assertEquals(FIRST_NONCE, relay.nextNonce(SCOPE).value)
        nonceUnavailable = true

        val failure = try {
            relay.catchUpNonce(SCOPE)
            null
        } catch (e: PerpsExecutionException) {
            e
        }
        nonceUnavailable = false
        backendNonce = LATER_BACKEND_NONCE

        assertEquals(true, failure != null)
        assertEquals(LATER_BACKEND_NONCE, relay.nextNonce(SCOPE).value)
    }

    @Test
    fun aNonceHandedBackWhileTheBackendIsReadIsNotTakenForOneIssued() = runTest {
        val nonces = PerpsNonceCoordinator()
        assertEquals(FIRST_NONCE, nonces.next(SCOPE) { FIRST_NONCE })
        assertEquals(FIRST_NONCE + 1, nonces.next(OTHER_WALLET_SCOPE) { FIRST_NONCE })
        nonces.returnUnconsumed(SCOPE, FIRST_NONCE)

        nonces.catchUp(SCOPE) {
            nonces.returnUnconsumed(OTHER_WALLET_SCOPE, FIRST_NONCE + 1)
            FIRST_NONCE
        }

        assertEquals(FIRST_NONCE, nonces.next(SCOPE) { FIRST_NONCE })
    }

    @Test
    fun aNonceHandedBackAndIssuedAgainDuringTheReadIsKept() = runTest {
        val nonces = PerpsNonceCoordinator()
        assertEquals(FIRST_NONCE, nonces.next(SCOPE) { FIRST_NONCE })

        nonces.catchUp(SCOPE) {
            nonces.returnUnconsumed(OTHER_WALLET_SCOPE, FIRST_NONCE)
            assertEquals(FIRST_NONCE, nonces.next(OTHER_WALLET_SCOPE) { FIRST_NONCE })
            FIRST_NONCE
        }

        assertEquals(FIRST_NONCE + 1, nonces.next(SCOPE) { FIRST_NONCE })
    }

    @Test
    fun aCursorReseededLowerDuringTheReadKeepsTheNonceIssuedFromIt() = runTest {
        val nonces = PerpsNonceCoordinator()
        assertEquals(FIRST_NONCE, nonces.next(SCOPE) { FIRST_NONCE })
        assertEquals(FIRST_NONCE + 1, nonces.next(SCOPE) { FIRST_NONCE })

        nonces.catchUp(SCOPE) {
            nonces.forget(OTHER_WALLET_SCOPE)
            assertEquals(FIRST_NONCE, nonces.next(OTHER_WALLET_SCOPE) { FIRST_NONCE })
            FIRST_NONCE
        }

        assertEquals(FIRST_NONCE + 1, nonces.next(SCOPE) { FIRST_NONCE })
    }

    @Test
    fun aSlowCatchUpDoesNotLowerACursorAFasterOneRaisedDuringItsRead() = runTest {
        val nonces = PerpsNonceCoordinator()
        assertEquals(FIRST_NONCE, nonces.next(SCOPE) { FIRST_NONCE })

        nonces.catchUp(SCOPE) {
            nonces.catchUp(OTHER_WALLET_SCOPE) { LATER_BACKEND_NONCE }
            FIRST_NONCE
        }

        assertEquals(LATER_BACKEND_NONCE, nonces.next(SCOPE) { FIRST_NONCE })
    }

    @Test
    fun catchingUpTakesBackNoncesIssuedButNeverSent() = runTest {
        assertEquals(FIRST_NONCE, relay.nextNonce(SCOPE).value)
        assertEquals(FIRST_NONCE + 1, relay.nextNonce(SCOPE).value)

        relay.catchUpNonce(SCOPE)

        assertEquals(FIRST_NONCE, relay.nextNonce(SCOPE).value)
    }

    @Test
    fun catchingUpWithoutACursorStartsFromTheBackend() = runTest {
        relay.catchUpNonce(SCOPE)

        assertEquals(FIRST_NONCE, relay.nextNonce(SCOPE).value)
        assertEquals(1, env.endpoints.count { it == GET_NEXT_NONCE })
    }

    @Test
    fun theNextOperationAfterARefusedSendSignsWithTheNonceTheVenueStillExpects() = runTest {
        assertEquals(FIRST_NONCE, relay.nextNonce(SCOPE).value)
        env.answer(SEND_TRANSACTIONS) {
            env.refused(HTTP_BAD_REQUEST, code = "validation_error", reason = "insufficient_funds")
        }
        submitFailure(step())

        relay.catchUpNonce(SCOPE)

        assertEquals(FIRST_NONCE, relay.nextNonce(SCOPE).value)
    }

    private suspend fun refuseFirstAttempt() {
        relay.nextNonce(SCOPE)
        assertEquals(PerpsSubmissionOutcome.ResignRequired, relay.submit(step()))
        relay.resyncNonce(SCOPE)
        assertEquals(FIRST_NONCE, relay.nextNonce(SCOPE).value)
    }

    private suspend fun submitFailure(step: PerpsSignedStep): PerpsExecutionException {
        return try {
            relay.submit(step)
            throw AssertionError("the refusal was not reconciled")
        } catch (e: PerpsExecutionException) {
            e
        }
    }

    private fun step(
        stepId: String = STEP_ID,
        txHash: String = TX_HASH,
        scope: PerpsScope = SCOPE,
        nonce: Long = FIRST_NONCE,
    ): PerpsSignedStep = mockk {
        every { operationId } returns OPERATION_ID
        every { this@mockk.stepId } returns stepId
        every { this@mockk.scope } returns scope
        every { this@mockk.nonce } returns nonce
        every { txType } returns TX_TYPE_CREATE_ORDER
        every { txInfo } returns """{"Hash":"$txHash"}"""
        every { this@mockk.txHash } returns txHash
    }

    private companion object {
        const val FIRST_NONCE = 41L
        const val LATER_BACKEND_NONCE = 57L
        const val TX_TYPE_CREATE_ORDER = 14
        const val TX_HASH = "0x01"
        const val RETRY_HASH = "0x02"
        const val HTTP_UNAUTHORIZED = 401
        const val OPERATION_ID = "operation-1"
        const val STEP_ID = "step-1"

        val SCOPE = PerpsScope(
            walletId = WALLET_ID,
            venue = "lighter",
            chainId = 304,
            environment = "mainnet",
            accountIndex = ACCOUNT_INDEX,
            keyBindingId = "binding",
            expectedPublicKey = "0xkey",
        )

        val OTHER_WALLET_SCOPE = PerpsScope(
            walletId = "$WALLET_ID-other",
            venue = "lighter",
            chainId = 304,
            environment = "mainnet",
            accountIndex = ACCOUNT_INDEX,
            keyBindingId = "binding",
            expectedPublicKey = "0xkey",
        )
    }
}
