package com.tonapps.deposit.multicoin.screens.moonpay

import com.tonapps.deposit.multicoin.moonpay.MOONPAY_GOOGLE_PAY_TYPE
import com.tonapps.deposit.multicoin.moonpay.api.MOONPAY_CARD_TYPE
import com.tonapps.deposit.multicoin.analytics.BuyCryptoAnalytics
import com.tonapps.bus.generated.Events.DepositFlow.DepositFlowOutcome
import com.tonapps.bus.generated.Events.DepositFlow.DepositFlowFailureReason
import androidx.lifecycle.SavedStateHandle
import com.tonapps.deposit.multicoin.moonpay.domain.MoonPayPaymentFailure
import com.tonapps.deposit.multicoin.moonpay.domain.MoonPayButtonPayments
import com.tonapps.deposit.multicoin.moonpay.domain.MoonPayPaymentInteractor
import com.tonapps.deposit.multicoin.moonpay.domain.MoonPayPaymentState
import com.tonapps.log.L
import com.tonapps.mvi.MviRelay
import com.tonapps.mvi.contract.MviAction
import com.tonapps.mvi.contract.MviViewState
import com.tonapps.mvi.graph.GraphViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.runningFold
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

interface MoonPayPaymentViewState : MviViewState {
    val summary: MoonPayPaymentSummary
    val steps: StateFlow<MoonPayPaymentSteps>
    val orderId: StateFlow<String?>
    val showsDetails: StateFlow<Boolean>
    val retry: StateFlow<MoonPayPaymentRetry?>
    val dismissal: StateFlow<MoonPayPaymentDismissal>
}

sealed interface MoonPayPaymentAction : MviAction

data class MoonPayPaymentSummary(
    val paid: String,
    val receive: String,
    val feesTotal: String,
    val fees: List<MoonPayPaymentFee>,
)

data class MoonPayPaymentSteps(
    val started: MoonPayPaymentStep,
    val pending: MoonPayPaymentStep,
    val completed: MoonPayPaymentStep,
)

data class MoonPayPaymentStep(val text: MoonPayPaymentStepText, val status: MoonPayPaymentStepStatus)

sealed interface MoonPayPaymentStepText {
    data class Started(val paymentTitle: String, val amount: String) : MoonPayPaymentStepText
    data object Checking : MoonPayPaymentStepText
    data class Pending(val cleared: Boolean) : MoonPayPaymentStepText
    data class Completed(val amount: String) : MoonPayPaymentStepText
    data object StillProcessing : MoonPayPaymentStepText
    data object Unknown : MoonPayPaymentStepText
    data class Failed(val failure: MoonPayPaymentFailure) : MoonPayPaymentStepText
}

enum class MoonPayPaymentStepStatus { Waiting, InProgress, Done, Failed }

data class MoonPayPaymentFee(val kind: MoonPayPaymentFeeKind, val amount: String)

enum class MoonPayPaymentFeeKind { MoonPay, Ecosystem, Network, Other }

// [AnotherCard] and [AnotherMethod] both open the method picker: the card one only after a card payment.
enum class MoonPayPaymentRetry { AnotherCard, AnotherMethod, ChangeAmount, Again }

// `Leave`: closing ends the flow while MoonPay still settles the payment; the customer is told it may take a while.
enum class MoonPayPaymentDismissal { Blocked, Back, CloseAll, Leave }

sealed interface MoonPayPaymentEvent {
    data object Cancelled : MoonPayPaymentEvent
}

class MoonPayPaymentFeature(
    private val data: MoonPayPaymentData,
    interactor: MoonPayPaymentInteractor,
    buttons: MoonPayButtonPayments,
    private val savedStateHandle: SavedStateHandle,
) : GraphViewModel<MoonPayPaymentViewState, MoonPayPaymentAction>(), MoonPayPaymentViewState {

    @Serializable
    private sealed interface Phase {
        // `widgetOpenedAt` once MoonPay's widget is open: it may take a payment it never reports.
        @Serializable
        data class Starting(val widgetOpenedAt: Long? = null) : Phase

        @Serializable
        data object Cancelled : Phase

        @Serializable
        data class Started(val transactionId: String?) : Phase

        @Serializable
        data class Checking(val widgetOpenedAt: Long?) : Phase

        // `named`, here and below: MoonPay named the transaction before this phase, so the start is shown done.
        @Serializable
        data class Verifying(val transactionId: String, val named: Boolean) : Phase

        @Serializable
        data class Processing(val transactionId: String) : Phase

        @Serializable
        data class Completed(val transactionId: String?) : Phase

        @Serializable
        data class StillProcessing(val transactionId: String, val pollsUntil: Long) : Phase

        @Serializable
        data class Unknown(val transactionId: String?, val named: Boolean) : Phase

        @Serializable
        data class Failed(val failure: MoonPayPaymentFailure, val transactionId: String?, val named: Boolean) : Phase
    }

    private val relay = MviRelay<MoonPayPaymentEvent>()
    val events: Flow<MoonPayPaymentEvent> = relay.events

    private val analytics = data.report?.let { BuyCryptoAnalytics(it.from, it.utm) }

    private val quoteSummary = MoonPayQuoteSummary(data.quote, data.assetLabel)

    private val authorised: String
        get() = data.quote.source.amount?.toBigDecimalOrNull()?.let(quoteSummary::formatPaid) ?: MoonPayQuoteSummary.NO_VALUE

    override val summary = quoteSummary.summary

    // Paid on MoonPay's buy button, whose wallet sheet runs over the app.
    private val paidOnButton: Boolean = data.quote.paymentMethod?.type == MOONPAY_GOOGLE_PAY_TYPE

    private val restored: Phase? = savedStateHandle.get<String>(KEY_PHASE)?.let(::decode)

    private val initial: Phase = restored ?: Phase.Starting()

    // A restored quote has lost its expiry, so a restored screen never pays.
    private val payment: Flow<MoonPayPaymentState> = when (restored) {
        // A tap on MoonPay's buy button started the payment before this sheet: it follows that one, and never pays
        // a button's quote itself, which would start a second payment nobody sees.
        null -> buttons.attach(data.externalTransactionId)
            ?.also { addCloseable { buttons.stop(data.externalTransactionId) } }
            ?: if (data.quote.paymentMethod?.type == MOONPAY_GOOGLE_PAY_TYPE) {
                flowOf(MoonPayPaymentState.Unknown(transactionId = null))
            } else {
                interactor.pay(data.quote, data.theme, data.externalTransactionId, interactive = true, commands = emptyFlow())
            }
        is Phase.Starting -> restored.widgetOpenedAt
            ?.let { interactor.lookUpWidget(it, data.theme) }
            ?: flowOf(MoonPayPaymentState.Failed(MoonPayPaymentFailure.Cancelled, transactionId = null))
        is Phase.Started -> restored.transactionId
            ?.let { interactor.verify(it, data.theme) }
            ?: flowOf(MoonPayPaymentState.Unknown(transactionId = null))
        is Phase.Verifying -> interactor.verify(restored.transactionId, data.theme)
        is Phase.Checking -> restored.widgetOpenedAt
            ?.let { interactor.lookUpWidget(it, data.theme) }
            ?: flowOf(MoonPayPaymentState.Unknown(transactionId = null))
        is Phase.Processing -> interactor.follow(restored.transactionId, data.theme)
        is Phase.StillProcessing -> interactor.followUntil(restored.transactionId, data.theme, restored.pollsUntil)
        is Phase.Cancelled,
        is Phase.Completed,
        is Phase.Unknown,
        is Phase.Failed,
        -> emptyFlow()
    }

    private val phase = payment
        .runningFold(initial, ::reduce)
        .onEach { phase ->
            savedStateHandle[KEY_PHASE] = Json.encodeToString(phase)
            outcome(phase)?.let { (outcome, failure) -> report(outcome, failure) }
            if (phase is Phase.Cancelled) {
                relay.emit(MoonPayPaymentEvent.Cancelled)
            }
        }
        .cacheState(initialValue = initial)

    override val steps = phase
        .map(::steps)
        .cacheState(initialValue = steps(initial))

    override val orderId = phase
        .map { it.id }
        .cacheState(initialValue = initial.id)

    override val showsDetails = phase
        .map(::showsDetails)
        .cacheState(initialValue = showsDetails(initial))

    override val retry = phase
        .map { (it as? Phase.Failed)?.failure?.let(::retry) }
        .cacheState(initialValue = (initial as? Phase.Failed)?.failure?.let(::retry))

    override val dismissal = phase
        .map(::dismissal)
        .cacheState(initialValue = dismissal(initial))

    private val Phase.id: String?
        get() = when (this) {
            is Phase.Starting,
            is Phase.Checking,
            is Phase.Cancelled,
            -> null
            is Phase.Started -> transactionId
            is Phase.Verifying -> transactionId
            is Phase.Processing -> transactionId
            is Phase.Completed -> transactionId
            is Phase.StillProcessing -> transactionId
            is Phase.Unknown -> transactionId
            is Phase.Failed -> transactionId
        }

    // The sheet closed before the outcome: MoonPay still settles a payment it named; one closed before MoonPay's widget
    // opened took nothing; otherwise nobody learns how it ended.
    // Called by the fragment, not as an action: on its last destroy the view model is already cleared.
    fun closedBeforeOutcome() {
        val outcome = when (val phase = phase.value) {
            is Phase.Verifying,
            is Phase.Processing,
            is Phase.StillProcessing,
            -> DepositFlowOutcome.Pending
            is Phase.Starting -> when (phase.widgetOpenedAt) {
                null -> DepositFlowOutcome.Cancelled
                else -> DepositFlowOutcome.Unknown
            }
            is Phase.Started,
            is Phase.Checking,
            is Phase.Cancelled,
            is Phase.Completed,
            is Phase.Unknown,
            is Phase.Failed,
            -> DepositFlowOutcome.Unknown
        }
        report(outcome, failure = null)
    }

    // Once per payment: a restored sheet has reported already.
    private fun report(outcome: DepositFlowOutcome, failure: DepositFlowFailureReason?) {
        val report = data.report ?: return
        if (savedStateHandle.get<Boolean>(KEY_REPORTED) == true) {
            return
        }
        savedStateHandle[KEY_REPORTED] = true
        analytics?.result(report.purchase, data.externalTransactionId, outcome, failure)
    }

    private fun outcome(phase: Phase): Pair<DepositFlowOutcome, DepositFlowFailureReason?>? {
        return when (phase) {
            is Phase.Completed -> DepositFlowOutcome.Success to null
            is Phase.Failed -> DepositFlowOutcome.Failed to failureReason(phase.failure)
            is Phase.Unknown -> DepositFlowOutcome.Unknown to null
            is Phase.Cancelled -> DepositFlowOutcome.Cancelled to null
            is Phase.Starting,
            is Phase.Started,
            is Phase.Checking,
            is Phase.Verifying,
            is Phase.Processing,
            is Phase.StillProcessing,
            -> null
        }
    }

    private fun failureReason(failure: MoonPayPaymentFailure): DepositFlowFailureReason {
        return when (failure) {
            MoonPayPaymentFailure.Declined -> DepositFlowFailureReason.Declined
            MoonPayPaymentFailure.LimitReached -> DepositFlowFailureReason.LimitReached
            MoonPayPaymentFailure.QuoteRejected,
            MoonPayPaymentFailure.QuoteExpired,
            -> DepositFlowFailureReason.QuoteRejected
            MoonPayPaymentFailure.TransactionCancelled -> DepositFlowFailureReason.TransactionCancelled
            MoonPayPaymentFailure.NotConfirmed -> DepositFlowFailureReason.NotConfirmed
            MoonPayPaymentFailure.Unavailable -> DepositFlowFailureReason.Unavailable
            MoonPayPaymentFailure.VerificationPending -> DepositFlowFailureReason.VerificationPending
            MoonPayPaymentFailure.VerificationUnavailable -> DepositFlowFailureReason.VerificationUnavailable
            MoonPayPaymentFailure.NoResponse -> DepositFlowFailureReason.NoResponse
            // A customer's cancel is `Phase.Cancelled` before a transaction; after one, MoonPay's status decides.
            MoonPayPaymentFailure.Cancelled,
            MoonPayPaymentFailure.SignInRequired,
            MoonPayPaymentFailure.Unknown,
            -> DepositFlowFailureReason.Unknown
        }
    }

    private fun decode(saved: String): Phase {
        return runCatching { Json.decodeFromString<Phase>(saved) }.getOrElse {
            L.w("MoonPay payment: unreadable saved phase: ${it.message}")
            Phase.Unknown(transactionId = null, named = false)
        }
    }

    private fun reduce(previous: Phase, update: MoonPayPaymentState): Phase {
        return when (update) {
            is MoonPayPaymentState.Starting -> Phase.Starting()
            // Before the tap the sheet is not open; the tap opens MoonPay's buy button as a widget, by time.
            is MoonPayPaymentState.ButtonReady,
            is MoonPayPaymentState.ButtonPressed,
            -> previous
            is MoonPayPaymentState.WidgetOpened -> Phase.Starting(update.openedAt)
            is MoonPayPaymentState.Started -> Phase.Started(update.transactionId)
            is MoonPayPaymentState.Checking -> Phase.Checking(
                (previous as? Phase.Starting)?.widgetOpenedAt ?: (previous as? Phase.Checking)?.widgetOpenedAt,
            )
            is MoonPayPaymentState.Verifying -> Phase.Verifying(update.transactionId, named(previous))
            is MoonPayPaymentState.Processing -> Phase.Processing(update.transactionId)
            is MoonPayPaymentState.Completed -> Phase.Completed(update.transactionId)
            is MoonPayPaymentState.StillProcessing -> Phase.StillProcessing(update.transactionId, update.pollsUntil)
            is MoonPayPaymentState.Unknown -> Phase.Unknown(update.transactionId, named(previous))
            is MoonPayPaymentState.Failed -> {
                // A customer cancel before MoonPay names a transaction goes back to buying.
                val cancelled = update.failure == MoonPayPaymentFailure.Cancelled && update.transactionId == null
                if (cancelled) {
                    Phase.Cancelled
                } else {
                    Phase.Failed(update.failure, update.transactionId, named(previous))
                }
            }
        }
    }

    private fun named(previous: Phase): Boolean {
        return when (previous) {
            is Phase.Started -> previous.transactionId != null
            is Phase.Verifying -> previous.named
            is Phase.Processing,
            is Phase.StillProcessing,
            -> true
            is Phase.Starting,
            is Phase.Cancelled,
            is Phase.Checking,
            is Phase.Completed,
            is Phase.Unknown,
            is Phase.Failed,
            -> false
        }
    }

    private fun steps(phase: Phase): MoonPayPaymentSteps {
        val started = MoonPayPaymentStep(MoonPayPaymentStepText.Started(data.paymentTitle, authorised), WAITING)
        val pending = MoonPayPaymentStep(MoonPayPaymentStepText.Pending(cleared = false), WAITING)
        val cleared = MoonPayPaymentStep(MoonPayPaymentStepText.Pending(cleared = true), DONE)
        val completed = MoonPayPaymentStep(MoonPayPaymentStepText.Completed(summary.receive), WAITING)
        return when (phase) {
            is Phase.Starting,
            is Phase.Cancelled,
            -> MoonPayPaymentSteps(started.copy(status = IN_PROGRESS), pending, completed)
            is Phase.Checking -> MoonPayPaymentSteps(
                MoonPayPaymentStep(MoonPayPaymentStepText.Checking, IN_PROGRESS),
                pending,
                completed,
            )
            // As on iOS, the first step is done only once MoonPay names the transaction: a card's runs on through
            // its 3DS, and an end with no transaction is shown on it.
            is Phase.Started -> if (phase.transactionId == null) {
                MoonPayPaymentSteps(started.copy(status = IN_PROGRESS), pending, completed)
            } else {
                MoonPayPaymentSteps(started.copy(status = DONE), pending.copy(status = IN_PROGRESS), completed)
            }
            is Phase.Verifying -> if (phase.named) {
                MoonPayPaymentSteps(started.copy(status = DONE), pending.copy(status = IN_PROGRESS), completed)
            } else {
                MoonPayPaymentSteps(MoonPayPaymentStep(MoonPayPaymentStepText.Checking, IN_PROGRESS), pending, completed)
            }
            is Phase.Processing -> MoonPayPaymentSteps(
                started.copy(status = DONE),
                cleared,
                completed.copy(status = IN_PROGRESS),
            )
            is Phase.Completed -> MoonPayPaymentSteps(
                started.copy(status = DONE),
                cleared,
                completed.copy(status = DONE),
            )
            is Phase.StillProcessing -> MoonPayPaymentSteps(
                started.copy(status = DONE),
                cleared,
                MoonPayPaymentStep(MoonPayPaymentStepText.StillProcessing, IN_PROGRESS),
            )
            is Phase.Unknown -> if (phase.named) {
                MoonPayPaymentSteps(
                    started.copy(status = DONE),
                    phase.transactionId?.let { cleared } ?: pending,
                    MoonPayPaymentStep(MoonPayPaymentStepText.Unknown, FAILED),
                )
            } else {
                MoonPayPaymentSteps(MoonPayPaymentStep(MoonPayPaymentStepText.Unknown, FAILED), pending, completed)
            }
            is Phase.Failed -> {
                val failed = MoonPayPaymentStep(MoonPayPaymentStepText.Failed(phase.failure), FAILED)
                if (phase.named) {
                    MoonPayPaymentSteps(started.copy(status = DONE), failed, completed)
                } else {
                    MoonPayPaymentSteps(failed, pending, completed)
                }
            }
        }
    }

    // As on iOS, a payment MoonPay named no transaction for has no sums to show.
    private fun showsDetails(phase: Phase): Boolean {
        return phase !is Phase.Failed && !(phase is Phase.Unknown && phase.transactionId == null)
    }

    private fun dismissal(phase: Phase): MoonPayPaymentDismissal {
        return when (phase) {
            // MoonPay's frames are taking the payment, or the widget's is looked up: closing would drop it.
            is Phase.Started,
            is Phase.Checking,
            -> MoonPayPaymentDismissal.Blocked
            // Only MoonPay's status is asked for, as on iOS the sheet can be closed.
            is Phase.Verifying,
            is Phase.Processing,
            is Phase.StillProcessing,
            -> MoonPayPaymentDismissal.Leave
            // The open widget may already have charged; MoonPay's buy button pays in the wallet's own sheet over the
            // app, and before MoonPay names a transaction this sheet closes like a failure, as on iOS.
            is Phase.Starting -> if (phase.widgetOpenedAt == null || paidOnButton) {
                MoonPayPaymentDismissal.Back
            } else {
                MoonPayPaymentDismissal.Blocked
            }
            is Phase.Cancelled,
            is Phase.Failed,
            -> MoonPayPaymentDismissal.Back
            is Phase.Unknown,
            is Phase.Completed,
            -> MoonPayPaymentDismissal.CloseAll
        }
    }

    private fun retry(failure: MoonPayPaymentFailure): MoonPayPaymentRetry? {
        return when (failure) {
            MoonPayPaymentFailure.Declined -> if (data.quote.paymentMethod?.type == MOONPAY_CARD_TYPE) {
                MoonPayPaymentRetry.AnotherCard
            } else {
                MoonPayPaymentRetry.AnotherMethod
            }
            MoonPayPaymentFailure.LimitReached -> MoonPayPaymentRetry.ChangeAmount
            MoonPayPaymentFailure.VerificationPending,
            MoonPayPaymentFailure.VerificationUnavailable,
            -> null
            MoonPayPaymentFailure.QuoteRejected,
            MoonPayPaymentFailure.QuoteExpired,
            MoonPayPaymentFailure.Cancelled,
            MoonPayPaymentFailure.TransactionCancelled,
            MoonPayPaymentFailure.NotConfirmed,
            MoonPayPaymentFailure.Unavailable,
            MoonPayPaymentFailure.NoResponse,
            MoonPayPaymentFailure.SignInRequired,
            MoonPayPaymentFailure.Unknown,
            -> MoonPayPaymentRetry.Again
        }
    }

    private companion object {
        const val KEY_PHASE = "moonpay_payment_phase"
        const val KEY_REPORTED = "moonpay_payment_reported"
        val WAITING = MoonPayPaymentStepStatus.Waiting
        val IN_PROGRESS = MoonPayPaymentStepStatus.InProgress
        val DONE = MoonPayPaymentStepStatus.Done
        val FAILED = MoonPayPaymentStepStatus.Failed
    }
}

/*
 *   MoonPayButtonPayments.attach [a tapped buy button's payment] | MoonPayPaymentInteractor.pay | restored ──► payment
 *
 *   payment ──► [fold] phase ──► events, analytics [the outcome, once per payment]
 *   closedBeforeOutcome ⋯ phase ──► analytics [pending, cancelled or unknown, once per payment]
 *
 *   phase ──► steps
 *   phase ──► orderId?
 *   phase ──► showsDetails
 *   phase ──► retry?
 *   phase ──► dismissal
 */
