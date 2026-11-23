package com.tonapps.deposit.multicoin.moonpay

import com.tonapps.deposit.multicoin.moonpay.api.MOONPAY_CARD_TYPE
import com.tonapps.deposit.multicoin.moonpay.api.MoonPayBuyQuote
import com.tonapps.deposit.multicoin.moonpay.frame.MoonPayFrame
import com.tonapps.deposit.multicoin.moonpay.frame.MoonPayFrameCompletion
import com.tonapps.deposit.multicoin.moonpay.frame.MoonPayFrameError
import com.tonapps.deposit.multicoin.moonpay.frame.MoonPayFrameCommand
import com.tonapps.deposit.multicoin.moonpay.frame.MoonPayFrameEvent
import com.tonapps.deposit.multicoin.moonpay.frame.MoonPayFrames
import com.tonapps.deposit.multicoin.moonpay.frame.MoonPayTheme
import com.tonapps.deposit.multicoin.moonpay.frame.MoonPayTransaction
import com.tonapps.log.L
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

sealed interface MoonPayCheckoutEvent {

    // The purchase is running and the customer may be charged from now on. The buy frame starts
    // it on `ready`, so a transaction it names later comes as another start; the widget on `transactionCreated`.
    data class Started(val transaction: MoonPayTransaction?) : MoonPayCheckoutEvent

    // The widget opened at `openedAt`, on MoonPay's clock; a restored screen looks its transaction up from it.
    data class WidgetOpened(val openedAt: Long) : MoonPayCheckoutEvent

    // The widget ended before it named a transaction, and MoonPay is asked whether one was made.
    data object Checking : MoonPayCheckoutEvent

    // MoonPay's buy button is drawn, and pays only on a tap.
    data object ButtonReady : MoonPayCheckoutEvent

    // The customer tapped the buy button: the wallet's payment sheet opens.
    data object ButtonPressed : MoonPayCheckoutEvent

    sealed interface Final : MoonPayCheckoutEvent

    data class Finished(val transaction: MoonPayTransaction, val sessionUpgraded: Boolean) : Final

    // `transactionId` is the last transaction MoonPay named during the attempt.
    data class Failed(val error: MoonPayCheckoutError, val transactionId: String?) : Final
}

sealed interface MoonPayCheckoutError {

    enum class Source { Payment, Challenge }

    data class Remote(val source: Source, val error: MoonPayFrameError) : MoonPayCheckoutError

    // The challenge settled something other than the purchase, such as identity or a limit upgrade.
    data class ChallengeCompleted(val completion: MoonPayFrameCompletion) : MoonPayCheckoutError

    // MoonPay's `cancelled`, or a frame closed before the purchase started.
    data object Cancelled : MoonPayCheckoutError

    // The customer closed the challenge (3DS) page.
    data object ChallengeClosed : MoonPayCheckoutError

    // A frame failed before the purchase started.
    data class FrameFailed(val cause: Throwable) : MoonPayCheckoutError

    // The buy frame never started the purchase.
    data object NoResponse : MoonPayCheckoutError

    // The purchase may have started and MoonPay never told its outcome: the frames were closed or lost, or
    // stayed silent, or the transaction a widget made could not be looked up. `cause` is what failed, if anything.
    data class NoOutcome(val cause: Throwable?) : MoonPayCheckoutError
}

sealed interface MoonPayWidgetLookup {
    data class Found(val id: String) : MoonPayWidgetLookup
    data object None : MoonPayWidgetLookup
    data class Failed(val cause: Throwable?) : MoonPayWidgetLookup
}

// One purchase attempt in MoonPay's frames. The flow emits Started once, and again for each transaction
// MoonPay names later; it ends with its first Final event, never fails, and its frames live while it is collected.
class MoonPayCheckout(
    private val frames: MoonPayFrames,
    private val transactions: MoonPayTransactionRepository,
    // Emits the current state on collection, as a StateFlow does.
    private val foreground: Flow<Boolean>,
    private val wallClockMs: () -> Long = System::currentTimeMillis,
) {

    // A new instance restarts the wait even with the same values.
    private class Deadline(val after: Duration, val error: MoonPayCheckoutError)

    private sealed interface ChallengeEnd {
        data class Finished(val event: MoonPayCheckoutEvent.Finished) : ChallengeEnd

        // `resolved`: the wallet payment is to be paid on its button again, so this attempt ends as a cancel.
        data object Resolved : ChallengeEnd
        data class Failed(val error: MoonPayCheckoutError, val transactionId: String?) : ChallengeEnd
    }

    // `externalTransactionId` names one attempt: every Continue starts a new one, as on iOS.
    fun run(
        connection: MoonPayConnection,
        quote: MoonPayBuyQuote,
        theme: MoonPayTheme?,
        externalTransactionId: String,
        commands: Flow<MoonPayFrameCommand>,
    ): Flow<MoonPayCheckoutEvent> {
        return events(connection, quote, theme, externalTransactionId, commands).transformWhile { event ->
            emit(event)
            event !is MoonPayCheckoutEvent.Final
        }
    }

    private fun events(
        connection: MoonPayConnection,
        quote: MoonPayBuyQuote,
        theme: MoonPayTheme?,
        externalTransactionId: String,
        commands: Flow<MoonPayFrameCommand>,
    ): Flow<MoonPayCheckoutEvent> = channelFlow {
        val clientToken = connection.credentials.clientToken
        val payment = when (quote.paymentMethod?.type) {
            MOONPAY_CARD_TYPE -> MoonPayFrame.Buy(clientToken, quote.signature, externalTransactionId)
            MOONPAY_GOOGLE_PAY_TYPE -> MoonPayFrame.BuyButton(
                clientToken = clientToken,
                signature = quote.signature,
                externalTransactionId = externalTransactionId,
                theme = theme,
                googlePayButtonSettings = googlePayButtonSettings(theme),
            )
            else -> MoonPayFrame.Widget(clientToken, quote.signature, externalTransactionId, theme)
        }
        val started = AtomicBoolean(false)
        val startSent = AtomicBoolean(false)
        val challengeOpen = AtomicBoolean(false)
        val transactionId = AtomicReference<String?>(null)
        // Only the buy frame has a deadline to start: in the widget and on the buy button the customer is in charge.
        // Once started, the buy frame and the buy button wait for the outcome as long, then MoonPay is asked.
        val deadline = MutableStateFlow<Deadline?>(
            Deadline(START_TIMEOUT, MoonPayCheckoutError.NoResponse).takeIf { payment is MoonPayFrame.Buy },
        )

        // The widget may not report a payment made inside it (seen on iOS in sandbox), so its close is looked up.
        // Without MoonPay's clock the device's may run ahead of it, so the open is moved back for the match: a lookup
        // may then take another fresh transaction for this one.
        // MoonPay's buy button is open the same way from its tap: the wallet's sheet may pay while the frame is lost.
        fun openedAt(now: Long): Long = quote.serverClockOffsetMs?.let { offset -> now + offset } ?: (now - CLOCK_SKEW_MS)

        val openedNow = wallClockMs()
        val widgetOpenedAt = AtomicReference((payment as? MoonPayFrame.Widget)?.let { openedAt(openedNow) })

        // The snapshot keeps every transaction made before the open, the device's own clock taken as it is when
        // MoonPay's is unknown, so an attempt of the last minutes is not taken for this one. With that clock ahead,
        // a snapshot answering after a very quick payment could list it too: rare, and only without MoonPay's clock.
        fun knownBefore(now: Long): Long = quote.serverClockOffsetMs?.let { now + it } ?: now
        val known = AtomicReference(
            widgetOpenedAt.get()?.let { openedAt -> async { knownTransactions(openedAt, knownBefore(openedNow), theme) } },
        )
        // The host may show the widget without hearing from it, so it counts as open from the start.
        widgetOpenedAt.get()?.let { send(MoonPayCheckoutEvent.WidgetOpened(it)) }

        suspend fun fail(error: MoonPayCheckoutError) {
            send(MoonPayCheckoutEvent.Failed(error, transactionId.get()))
        }

        // Taking the deadline over decides between a start and a timeout that fire together.
        suspend fun start(transaction: MoonPayTransaction?) {
            val named = transaction?.id?.let { transactionId.getAndSet(it) != it } ?: false
            val waiting = deadline.value
            val outcome = Deadline(OUTCOME_TIMEOUT, MoonPayCheckoutError.NoOutcome(cause = null))
            if (started.compareAndSet(false, true) && deadline.compareAndSet(waiting, outcome)) {
                startSent.set(true)
                send(MoonPayCheckoutEvent.Started(transaction))
            } else if (named && startSent.get()) {
                send(MoonPayCheckoutEvent.Started(transaction))
            }
        }

        if (payment is MoonPayFrame.Buy || payment is MoonPayFrame.BuyButton) {
            launch {
                deadline.collectLatest { current ->
                    if (current != null) {
                        delay(current.after)
                        if (deadline.compareAndSet(current, null)) {
                            fail(current.error)
                        }
                    }
                }
            }
        }

        // The buy button pays on a tap; MoonPay may report the payment before the tap itself, which counts as one.
        suspend fun pressed() {
            if (payment !is MoonPayFrame.BuyButton || widgetOpenedAt.get() != null) {
                return
            }
            val now = wallClockMs()
            val opened = openedAt(now)
            known.set(async { knownTransactions(opened, knownBefore(now), theme) })
            widgetOpenedAt.set(opened)
            send(MoonPayCheckoutEvent.ButtonPressed)
            send(MoonPayCheckoutEvent.WidgetOpened(opened))
        }

        suspend fun onPayment(event: MoonPayFrameEvent) {
            when (event) {
                is MoonPayFrameEvent.Ready -> when (payment) {
                    is MoonPayFrame.Buy -> start(transaction = null)
                    is MoonPayFrame.BuyButton -> send(MoonPayCheckoutEvent.ButtonReady)
                    else -> Unit
                }
                is MoonPayFrameEvent.ButtonPressed -> pressed()
                is MoonPayFrameEvent.TransactionCreated -> {
                    pressed()
                    start(event.transaction)
                }
                // The deadline keeps running until the challenge frame shows up.
                is MoonPayFrameEvent.Challenge -> {
                    pressed()
                    start(transaction = null)
                    if (challengeOpen.compareAndSet(false, true)) {
                        // Before its transaction, a wallet payment goes on on the button, which stays mounted.
                        val resume = payment is MoonPayFrame.BuyButton && transactionId.get() == null
                        val frame = MoonPayFrame.Challenge(event.url, clientToken, theme, resume)
                        launch {
                            when (val end = challenge(frame, connection) { deadline.value = null }) {
                                is ChallengeEnd.Finished -> send(end.event)
                                is ChallengeEnd.Resolved -> fail(MoonPayCheckoutError.Cancelled)
                                is ChallengeEnd.Failed -> {
                                    end.transactionId?.let(transactionId::set)
                                    fail(end.error)
                                }
                            }
                        }
                    }
                }
                is MoonPayFrameEvent.Complete -> {
                    pressed()
                    finished(event.completion)?.let {
                        deadline.value = null
                        send(it)
                    }
                }
                is MoonPayFrameEvent.Cancelled -> fail(MoonPayCheckoutError.Cancelled)
                is MoonPayFrameEvent.Error -> fail(MoonPayCheckoutError.Remote(PAYMENT, event.error))
                is MoonPayFrameEvent.Handshake,
                is MoonPayFrameEvent.Resolved,
                is MoonPayFrameEvent.Unknown,
                -> Unit
            }
        }

        val cause = try {
            frames.run(payment, connection.environment, commands).collect { onPayment(it) }
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            e
        }
        // An open challenge carries the purchase on, so its end is the attempt's.
        if (!challengeOpen.get()) {
            val error = when {
                started.get() -> MoonPayCheckoutError.NoOutcome(cause)
                // Closed or failed, the widget may have taken a payment it did not report.
                widgetOpenedAt.get() != null -> {
                    send(MoonPayCheckoutEvent.Checking)
                    when (val found = findWidgetTransaction(checkNotNull(widgetOpenedAt.get()), known.get()?.await(), theme)) {
                        is MoonPayWidgetLookup.Found -> {
                            transactionId.set(found.id)
                            MoonPayCheckoutError.NoOutcome(cause)
                        }
                        is MoonPayWidgetLookup.None -> cause?.let(MoonPayCheckoutError::FrameFailed)
                            ?: MoonPayCheckoutError.Cancelled
                        // The widget named no transaction and MoonPay's list could not be read: nothing tells whether the customer paid.
                        is MoonPayWidgetLookup.Failed -> {
                            L.w("MoonPay checkout: the widget's transaction could not be looked up: ${found.cause?.message}")
                            MoonPayCheckoutError.NoOutcome(cause ?: found.cause)
                        }
                    }
                }
                cause != null -> MoonPayCheckoutError.FrameFailed(cause)
                else -> MoonPayCheckoutError.Cancelled
            }
            fail(error)
        }
    }

    // The transactions made before the widget opened, so the lookup on close takes only one it made: the list
    // cannot be filtered by our external id. A slow answer may already list this widget's, hence the time filter.
    // Null when it failed: the lookup then goes by the time window alone.
    private suspend fun knownTransactions(openedAt: Long, before: Long, theme: MoonPayTheme?): Set<String>? {
        return transactions.transactionsSince(openedAt - CLOCK_SKEW_MS, theme).getOrNull()
            ?.filter { (it.createdAtMs ?: Long.MIN_VALUE) < before }
            ?.mapTo(mutableSetOf()) { it.id }
    }

    // The transaction the widget opened at `openedAt` made, if any. MoonPay's list may not show a just-made one yet,
    // so only an empty last answer after no failed try says there is none. Without `known` a transaction counts by
    // its time alone.
    suspend fun findWidgetTransaction(openedAt: Long, known: Set<String>?, theme: MoonPayTheme?): MoonPayWidgetLookup {
        var lastError: Throwable? = null
        repeat(LOOKUP_ATTEMPTS) { attempt ->
            if (attempt > 0) {
                delay(LOOKUP_RETRY_DELAY)
            }
            // A backgrounded app's requests are refused on Android 15; the customer may be paying in another app.
            foreground.first { it }
            transactions.transactionsSince(openedAt - CLOCK_SKEW_MS, theme)
                .onSuccess { list ->
                    val created = list
                        .filter { known?.contains(it.id) != true }
                        .filter { (it.createdAtMs ?: openedAt) >= openedAt - MATCH_MARGIN_MS }
                        .maxByOrNull { it.createdAtMs ?: Long.MIN_VALUE }
                    if (created != null) {
                        return MoonPayWidgetLookup.Found(created.id)
                    }
                    if (attempt == LOOKUP_ATTEMPTS - 1 && lastError == null) {
                        return MoonPayWidgetLookup.None
                    }
                }
                .onFailure { lastError = it }
        }
        return MoonPayWidgetLookup.Failed(lastError)
    }

    // The challenge only opens once the purchase started, so losing it leaves the outcome unknown; closed by the
    // customer, it ends without an error and is their cancel, as on iOS. Taking the first result closes the frame.
    private suspend fun challenge(
        frame: MoonPayFrame.Challenge,
        connection: MoonPayConnection,
        onShown: () -> Unit,
    ): ChallengeEnd {
        return try {
            frames.run(frame, connection.environment)
                .onEach { onShown() }
                .mapNotNull(::challengeEnd)
                .firstOrNull()
                ?: ChallengeEnd.Failed(MoonPayCheckoutError.ChallengeClosed, transactionId = null)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ChallengeEnd.Failed(MoonPayCheckoutError.NoOutcome(e), transactionId = null)
        }
    }

    private fun challengeEnd(event: MoonPayFrameEvent): ChallengeEnd? {
        return when (event) {
            is MoonPayFrameEvent.Complete -> finished(event.completion)?.let(ChallengeEnd::Finished)
                ?: ChallengeEnd.Failed(MoonPayCheckoutError.ChallengeCompleted(event.completion), transactionId = null)
            is MoonPayFrameEvent.Cancelled -> ChallengeEnd.Failed(MoonPayCheckoutError.Cancelled, event.transactionId)
            is MoonPayFrameEvent.Resolved -> ChallengeEnd.Resolved
            is MoonPayFrameEvent.Error -> ChallengeEnd.Failed(
                MoonPayCheckoutError.Remote(CHALLENGE, event.error),
                transactionId = null,
            )
            is MoonPayFrameEvent.Handshake,
            is MoonPayFrameEvent.Ready,
            is MoonPayFrameEvent.TransactionCreated,
            is MoonPayFrameEvent.Challenge,
            is MoonPayFrameEvent.ButtonPressed,
            is MoonPayFrameEvent.Unknown,
            -> null
        }
    }

    private fun finished(completion: MoonPayFrameCompletion): MoonPayCheckoutEvent.Finished? {
        return when (completion) {
            is MoonPayFrameCompletion.Transaction -> MoonPayCheckoutEvent.Finished(
                completion.transaction,
                completion.sessionUpgraded,
            )
            is MoonPayFrameCompletion.Connection,
            is MoonPayFrameCompletion.Card,
            is MoonPayFrameCompletion.Identity,
            is MoonPayFrameCompletion.Status,
            is MoonPayFrameCompletion.Raw,
            -> null
        }
    }

    private companion object {
        val PAYMENT = MoonPayCheckoutError.Source.Payment
        val CHALLENGE = MoonPayCheckoutError.Source.Challenge
        val START_TIMEOUT = 60.seconds
        val OUTCOME_TIMEOUT = 3.minutes
        const val LOOKUP_ATTEMPTS = 3
        val LOOKUP_RETRY_DELAY = 2.seconds

        // Covers a device clock ahead of MoonPay's when the quote gave no offset to correct it.
        const val CLOCK_SKEW_MS = 120_000L

        // The offset is read from a Date header, to the second, and the open is timed on the device.
        const val MATCH_MARGIN_MS = 10_000L
    }
}

// The button as the design draws it: full width, 54 high, white on the dark theme and black on the light one.
private fun googlePayButtonSettings(theme: MoonPayTheme?): String {
    val style = if (theme == MoonPayTheme.Light) {
        "black"
    } else {
        "white"
    }
    return """{"height":"54px","borderRadius":"26px","paddingX":"0px","paddingY":"0px","style":"$style"}"""
}
