package com.tonapps.deposit.multicoin.moonpay.domain

import com.tonapps.deposit.multicoin.moonpay.MoonPayCheckout
import com.tonapps.deposit.multicoin.moonpay.MoonPayCheckoutError
import com.tonapps.deposit.multicoin.moonpay.MoonPayCheckoutEvent
import com.tonapps.deposit.multicoin.moonpay.MoonPayConnectionException
import com.tonapps.deposit.multicoin.moonpay.MoonPayConnectionRepository
import com.tonapps.deposit.multicoin.moonpay.MoonPayTransactionRepository
import com.tonapps.deposit.multicoin.moonpay.MoonPayWidgetLookup
import com.tonapps.deposit.multicoin.moonpay.api.MoonPayApiException
import com.tonapps.deposit.multicoin.moonpay.api.MoonPayBuyQuote
import com.tonapps.deposit.multicoin.moonpay.api.MoonPayQuoteSignature
import com.tonapps.deposit.multicoin.moonpay.frame.MoonPayConnectionStatus
import com.tonapps.deposit.multicoin.moonpay.frame.MoonPayFrameCommand
import com.tonapps.deposit.multicoin.moonpay.frame.MoonPayFrameException
import com.tonapps.deposit.multicoin.moonpay.frame.MoonPayTheme
import com.tonapps.deposit.multicoin.moonpay.frame.MoonPayTransaction
import com.tonapps.log.L
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

// One call is one attempt; a retry is another call with a fresh quote and a new `externalTransactionId`,
// as on iOS. The flow emits the payment's state, ends with Completed, Unknown or Failed, and never fails.
// Stopping the collection closes MoonPay's frames and the polling, so the caller keeps collecting while
// the app is in the background: the customer may be confirming the payment in a bank app. The
// caller never starts another attempt on its own: after Started the customer may already be charged.
class MoonPayPaymentInteractor(
    private val connections: MoonPayConnectionRepository,
    private val checkout: MoonPayCheckout,
    private val transactions: MoonPayTransactionRepository,
    // Emits the current state on collection, as a StateFlow does.
    private val foreground: Flow<Boolean>,
    private val wallClockMs: () -> Long = System::currentTimeMillis,
) {

    fun pay(
        quote: MoonPayBuyQuote,
        theme: MoonPayTheme?,
        externalTransactionId: String,
        // False for a payment nobody tapped yet (MoonPay's buy button): a lapsed sign-in fails it instead of asking.
        interactive: Boolean,
        commands: Flow<MoonPayFrameCommand>,
    ): Flow<MoonPayPaymentState> = flow {
        emit(MoonPayPaymentState.Starting)
        if (!quote.isFresh(Duration.ZERO)) {
            emit(MoonPayPaymentState.Failed(MoonPayPaymentFailure.QuoteExpired, transactionId = null))
            return@flow
        }
        if (!isUsable(quote)) {
            emit(MoonPayPaymentState.Failed(MoonPayPaymentFailure.QuoteRejected, transactionId = null))
            return@flow
        }
        val connection = connections.ensureConnected(theme, interactive).getOrElse { error ->
            L.w("MoonPay pay: connection failed: ${error.message}")
            emit(MoonPayPaymentState.Failed(MoonPayPaymentFailures.connection(error), transactionId = null))
            return@flow
        }
        var started = false
        var transactionId: String? = null
        val final = checkout.run(connection, quote, theme, externalTransactionId, commands)
            .catch { e ->
                if (e !is CancellationException) {
                    throw e
                }
                currentCoroutineContext().ensureActive()
                L.w("MoonPay pay: checkout was cancelled from outside: ${e.message}")
            }
            .onEach { event ->
                if (event is MoonPayCheckoutEvent.Started) {
                    started = true
                    transactionId = event.transaction?.id
                    emit(MoonPayPaymentState.Started(transactionId))
                }
                if (event is MoonPayCheckoutEvent.WidgetOpened) {
                    emit(MoonPayPaymentState.WidgetOpened(event.openedAt))
                }
                if (event is MoonPayCheckoutEvent.Checking) {
                    emit(MoonPayPaymentState.Checking)
                }
                if (event is MoonPayCheckoutEvent.ButtonReady) {
                    emit(MoonPayPaymentState.ButtonReady)
                }
                if (event is MoonPayCheckoutEvent.ButtonPressed) {
                    emit(MoonPayPaymentState.ButtonPressed)
                }
            }
            .filterIsInstance<MoonPayCheckoutEvent.Final>()
            .firstOrNull()
        val error = when (final) {
            is MoonPayCheckoutEvent.Finished -> {
                if (final.sessionUpgraded) {
                    connections.invalidate()
                }
                val transaction = final.transaction
                settle(transaction.copy(id = transaction.id ?: transactionId), theme)
                return@flow
            }
            is MoonPayCheckoutEvent.Failed -> {
                transactionId = final.transactionId ?: transactionId
                final.error
            }
            null -> null
        }
        val id = transactionId
        log(error, id)
        when {
            id != null && error == MoonPayCheckoutError.ChallengeClosed -> checkClosedChallenge(id, theme)
            // Once MoonPay named the transaction, its status is the outcome, whatever the frames said.
            id != null -> {
                emit(MoonPayPaymentState.Verifying(id))
                poll(id, theme)
            }
            // A started purchase may already be charged, so an unknown outcome is never a retryable failure.
            error is MoonPayCheckoutError.NoOutcome || (error == null && started) -> {
                emit(MoonPayPaymentState.Unknown(null))
            }
            else -> {
                val failure = error?.let(MoonPayPaymentFailures::checkout) ?: MoonPayPaymentFailure.Unavailable
                emit(MoonPayPaymentState.Failed(failure, transactionId = null))
            }
        }
    }

    // For a screen restored while the widget was open or looked up: the payment it may have made is found by time.
    fun lookUpWidget(openedAt: Long, theme: MoonPayTheme?): Flow<MoonPayPaymentState> = flow {
        emit(MoonPayPaymentState.Checking)
        when (val found = checkout.findWidgetTransaction(openedAt, known = null, theme)) {
            is MoonPayWidgetLookup.Found -> {
                emit(MoonPayPaymentState.Verifying(found.id))
                poll(found.id, theme)
            }
            // The customer may have been paying in another app when the process died: no transaction yet is no answer.
            is MoonPayWidgetLookup.None -> emit(MoonPayPaymentState.Unknown(transactionId = null))
            is MoonPayWidgetLookup.Failed -> emit(MoonPayPaymentState.Unknown(transactionId = null))
        }
    }

    fun verify(transactionId: String, theme: MoonPayTheme?): Flow<MoonPayPaymentState> = flow {
        emit(MoonPayPaymentState.Verifying(transactionId))
        poll(transactionId, theme)
    }

    fun follow(transactionId: String, theme: MoonPayTheme?): Flow<MoonPayPaymentState> = flow {
        emit(MoonPayPaymentState.Processing(transactionId))
        poll(transactionId, theme)
    }

    // Goes on with the slow polls of a payment that was still processing, until the same deadline.
    fun followUntil(transactionId: String, theme: MoonPayTheme?, pollsUntil: Long): Flow<MoonPayPaymentState> = flow {
        emit(MoonPayPaymentState.StillProcessing(transactionId, pollsUntil))
        emit(pollSlowly(transactionId, theme, pollsUntil))
    }

    private fun log(error: MoonPayCheckoutError?, transactionId: String?) {
        val message = "MoonPay pay: ended with $error, transaction $transactionId"
        if (error is MoonPayCheckoutError.Cancelled || error is MoonPayCheckoutError.ChallengeClosed) {
            L.i(message)
        } else {
            L.w(message)
        }
    }

    private fun isUsable(quote: MoonPayBuyQuote): Boolean {
        val payload = MoonPayQuoteSignature.payload(quote.signature)
        val missing = payload?.let(MoonPayQuoteSignature::missingPaymentFrameFields).orEmpty()
        if (missing.isNotEmpty()) {
            L.w("MoonPay pay: quote lacks $missing")
        }
        return quote.executable && quote.isFresh(Duration.ZERO) && payload != null && missing.isEmpty()
    }

    private suspend fun FlowCollector<MoonPayPaymentState>.settle(transaction: MoonPayTransaction, theme: MoonPayTheme?) {
        val id = transaction.id
        when {
            transaction.isFailed -> {
                L.w("MoonPay pay: $id failed: ${transaction.failureCode} ${transaction.failureReason}")
                emit(MoonPayPaymentState.Failed(MoonPayPaymentFailures.transaction(transaction.failureCode), id))
            }
            transaction.isCompleted -> emit(MoonPayPaymentState.Completed(id))
            id == null -> emit(MoonPayPaymentState.Unknown(null))
            else -> {
                emit(MoonPayPaymentState.Processing(id))
                poll(id, theme)
            }
        }
    }

    // A `pending` transaction is created and its payment in progress; the final status exists only
    // on MoonPay's side, so it is asked for until the transaction settles. After the quick polls the
    // customer is told it is still processing, and slower ones go on: delivering the crypto can take minutes.
    private suspend fun FlowCollector<MoonPayPaymentState>.poll(id: String, theme: MoonPayTheme?) {
        for (interval in QUICK_POLL_INTERVALS) {
            pause(interval)
            when (val check = check(id, theme, slow = false)) {
                is Check.Settled -> return emit(check.state)
                is Check.Stop -> return emit(MoonPayPaymentState.Unknown(id))
                is Check.Answered,
                is Check.Unanswered,
                is Check.ConnectFailed,
                -> Unit
            }
        }
        L.i("MoonPay poll: $id is still processing, polling slower")
        val until = wallClockMs() + SLOW_POLL_WINDOW.inWholeMilliseconds
        emit(MoonPayPaymentState.StillProcessing(id, until))
        emit(pollSlowly(id, theme, until))
    }

    // The deadline is by the clock from the first slow poll, so time in the background counts and a sheet left open
    // does not poll for ever. Past it MoonPay still gets a few tries to answer: a deadline that passed in the
    // background is checked on return, and a network not up yet must not end it with no answer.
    private suspend fun pollSlowly(id: String, theme: MoonPayTheme?, until: Long): MoonPayPaymentState {
        var lateChecks = 0
        while (lateChecks < LATE_CHECKS) {
            val left = (until - wallClockMs()).milliseconds
            val interval = when {
                left.isPositive() -> SLOW_POLL_INTERVAL.coerceAtMost(left)
                lateChecks == 0 -> Duration.ZERO
                else -> LATE_CHECK_INTERVAL
            }
            pause(interval)
            val late = wallClockMs() >= until
            if (late) {
                lateChecks++
            }
            when (val check = check(id, theme, slow = true)) {
                is Check.Settled -> return check.state
                is Check.Stop -> break
                // Read after the request: a silent connect may have taken it past the deadline.
                is Check.Answered -> if (wallClockMs() >= until) {
                    break
                }
                is Check.Unanswered -> Unit
                // A failed silent connect caches nothing, so every poll would run it again; it may clear, so wait longer.
                is Check.ConnectFailed -> if (!late) {
                    pause(CONNECT_BACKOFF.coerceAtMost((until - wallClockMs()).coerceAtLeast(0).milliseconds))
                }
            }
        }
        L.w("MoonPay poll: gave up on $id")
        return MoonPayPaymentState.Unknown(id)
    }

    // Android 15 refuses requests a backgrounded app starts, so none goes out there; coming back asks at once.
    private suspend fun pause(interval: Duration) {
        withTimeoutOrNull(interval) { foreground.first { !it } }
        foreground.first { it }
    }

    // The customer closed the 3DS page, so the transaction gets one look instead of the polls: a payment MoonPay settled
    // in that moment is shown as it is. Pending may still be a paid 3DS, so it is unknown and offers no retry.
    private suspend fun FlowCollector<MoonPayPaymentState>.checkClosedChallenge(id: String, theme: MoonPayTheme?) {
        emit(MoonPayPaymentState.Verifying(id))
        pause(Duration.ZERO)
        val settled = (check(id, theme, slow = false) as? Check.Settled)?.state
        emit(settled ?: MoonPayPaymentState.Unknown(id))
    }

    private suspend fun check(id: String, theme: MoonPayTheme?, slow: Boolean): Check {
        val result = transactions.transaction(id, theme)
        val details = result.getOrElse { error ->
            L.w("MoonPay poll: ${error.message}")
            return when {
                slow && error.isConnectFailure() -> Check.ConnectFailed
                error.isConnectFailure() -> Check.Unanswered
                // Without signing in nothing can be learned, so the rest of the schedule would be idle.
                error.needsSignIn() -> Check.Stop
                // A failed or unavailable connection may still clear in the first minute, but not after it.
                slow && error is MoonPayConnectionException.Rejected -> Check.Stop
                // Unlike iOS, the first minute asks again: MoonPay may not serve a transaction the frame has just named.
                !slow && error is MoonPayApiException.Http && error.status == NOT_FOUND -> Check.Unanswered
                // A bad request, a token refused after a fresh connect or a missing transaction does not get better.
                error is MoonPayApiException.Http && error.status in FINAL_HTTP_STATUSES -> Check.Stop
                else -> Check.Unanswered
            }
        }
        if (details.isCompleted) {
            return Check.Settled(MoonPayPaymentState.Completed(id))
        }
        if (details.isFailed) {
            val stage = details.failedStage
            L.w("MoonPay poll: $id failed at ${stage?.name}: ${stage?.failureReason}")
            return Check.Settled(MoonPayPaymentState.Failed(MoonPayPaymentFailures.stage(stage?.failureReason), id))
        }
        return Check.Answered
    }

    // A connect that broke on the way, or one MoonPay is still setting up, may clear. A cancelled one came from
    // another flow's sign-in this silent connect joined.
    private fun Throwable.isConnectFailure(): Boolean {
        return this is MoonPayConnectionException.Remote ||
            this is MoonPayConnectionException.TimedOut ||
            this is MoonPayConnectionException.Session ||
            this is MoonPayConnectionException.Cancelled ||
            this is MoonPayFrameException ||
            (this is MoonPayConnectionException.Rejected && status == MoonPayConnectionStatus.Pending)
    }

    private fun Throwable.needsSignIn(): Boolean {
        return this is MoonPayConnectionException.Rejected && status in SIGN_IN_STATUSES
    }

    private sealed interface Check {
        data class Settled(val state: MoonPayPaymentState) : Check
        data object Answered : Check
        data object Unanswered : Check
        data object ConnectFailed : Check
        data object Stop : Check
    }

    private companion object {
        val QUICK_POLL_INTERVALS = List(5) { 3.seconds } + List(9) { 5.seconds }
        val SLOW_POLL_INTERVAL = 15.seconds
        val SLOW_POLL_WINDOW = 30.minutes
        val CONNECT_BACKOFF = 45.seconds
        const val LATE_CHECKS = 3
        val LATE_CHECK_INTERVAL = 5.seconds
        const val NOT_FOUND = 404
        val FINAL_HTTP_STATUSES = setOf(400, 401, 403, NOT_FOUND)
        val SIGN_IN_STATUSES = setOf(
            MoonPayConnectionStatus.ConnectionRequired,
            MoonPayConnectionStatus.TermsAcceptanceRequired,
        )
    }
}
