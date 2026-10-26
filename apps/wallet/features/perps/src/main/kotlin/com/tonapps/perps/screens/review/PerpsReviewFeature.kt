package com.tonapps.perps.screens.review

import android.content.Context
import androidx.annotation.StringRes
import com.tonapps.async.Async
import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.bus.generated.Events.Perps.PerpsReason
import com.tonapps.chainkit.perpetual.PerpetualReview
import com.tonapps.chainkit.perpetual.PerpsMarginSettings
import com.tonapps.chainkit.perpetual.PerpsPositionState
import com.tonapps.chainkit.perpetual.PerpsScale
import com.tonapps.chainkit.perpetual.PerpsTradeError
import com.tonapps.chainkit.perpetual.PerpsTradeIntent
import com.tonapps.log.L
import com.tonapps.mvi.MviRelay
import com.tonapps.mvi.contract.MviAction
import com.tonapps.mvi.contract.MviViewState
import com.tonapps.mvi.flow.countdown
import com.tonapps.mvi.flow.withLatestFrom
import com.tonapps.mvi.graph.GraphViewModel
import com.tonapps.mvi.graph.KResult
import com.tonapps.mvi.graph.KState
import com.tonapps.perps.PerpsAnalytics
import com.tonapps.perps.data.PERPS_EMPTY_VALUE
import com.tonapps.perps.data.PerpsAutoCloseDraft
import com.tonapps.perps.data.PerpsError
import com.tonapps.perps.data.PerpsLivePrices
import com.tonapps.perps.data.PerpsMarkQuote
import com.tonapps.perps.data.PerpsSizeDirection
import com.tonapps.perps.data.formatAutoClosePrice
import com.tonapps.perps.data.formatPlainDecimal
import com.tonapps.perps.data.formatQuoteAmount
import com.tonapps.perps.data.formatSignedPercent
import com.tonapps.perps.data.formatSignedUsdAmount
import com.tonapps.perps.data.formatUsdAmount
import com.tonapps.perps.data.formatUsdPrice
import com.tonapps.perps.data.perpsTicker
import com.tonapps.perps.failureReason
import com.tonapps.perps.screens.order.PerpsAutoCloseBounds
import com.tonapps.perps.screens.order.autoCloseEstimate
import com.tonapps.perps.screens.sizechange.withRestingLegs
import com.tonapps.perps.trade.PerpsAccountService
import com.tonapps.perps.trade.PerpsAutoCloseEdit
import com.tonapps.perps.trade.PerpsPlanFailure
import com.tonapps.perps.trade.PerpsPlannedTrade
import com.tonapps.perps.trade.PerpsPlannerAdapter
import com.tonapps.perps.trade.PerpsProductRules
import com.tonapps.perps.trade.PerpsSubmitStart
import com.tonapps.perps.trade.PerpsTradeExecutor
import com.tonapps.perps.trade.PerpsTradeFailure
import com.tonapps.perps.trade.PerpsTradeRequest
import com.tonapps.perps.trade.PerpsUnlockFailure
import com.tonapps.wallet.data.multichain.account.UnifiedAccountRepository
import com.tonapps.wallet.data.multichain.lighter.LighterSigningCredentials
import com.tonapps.wallet.localization.Localization
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

interface PerpsReviewViewState : MviViewState {
    val rows: StateFlow<PerpsReviewContent?>
    val status: StateFlow<PerpsReviewStatus>
    val sheet: StateFlow<PerpsReviewSheet?>
    val canConfirm: StateFlow<Boolean>
    val autoCloseEditor: StateFlow<PerpsReviewAutoCloseEditor?>
}

sealed interface PerpsReviewAction : MviAction {
    data class Attach(val context: Context) : PerpsReviewAction
    data object Retry : PerpsReviewAction
    data object Confirm : PerpsReviewAction
    data object DismissSheet : PerpsReviewAction
    data class UpdateAutoClose(val fix: PerpsAutoCloseFix) : PerpsReviewAction
    data class SetAutoClose(val draft: PerpsAutoCloseDraft?) : PerpsReviewAction
    data class SetVisible(val visible: Boolean) : PerpsReviewAction

    sealed interface Internal : PerpsReviewAction {
        data object Requote : Internal
        data object SubmitRequote : Internal
        data object PriceExpired : Internal
        data class Planned(val intent: PerpsTradeIntent, val request: PerpsTradeRequest) : Internal
    }
}

sealed interface PerpsReviewEvent {
    data object Submitted : PerpsReviewEvent
    data object ComingSoon : PerpsReviewEvent
    data object InProgress : PerpsReviewEvent
    data object Cancelled : PerpsReviewEvent
    data class UpdateAutoClose(val fix: PerpsAutoCloseFix) : PerpsReviewEvent
    data class PlanRefused(@StringRes val text: Int) : PerpsReviewEvent
    data class SubmitFailed(@StringRes val text: Int) : PerpsReviewEvent
}

class PerpsReviewFeature(
    private val args: PerpsReviewArgs,
    livePrices: PerpsLivePrices,
    private val adapter: PerpsPlannerAdapter,
    private val executor: PerpsTradeExecutor,
    private val accountService: PerpsAccountService,
    unifiedAccountRepository: UnifiedAccountRepository,
) : GraphViewModel<PerpsReviewViewState, PerpsReviewAction>(), PerpsReviewViewState {

    private enum class PlanTrigger { USER, LIVE, SUBMIT }

    private data class UnlockContext(val wallet: WalletEntity, val context: Context, val attempt: Int)

    private data class ReviewKey(val walletId: String, val credentials: LighterSigningCredentials)

    private data class PlanAttempt(
        val count: Int,
        val trigger: PlanTrigger,
        val autoClose: PerpsAutoCloseEdit,
        val held: Boolean = false,
    )

    private sealed interface AttemptCmd {
        data class Trigger(val trigger: PlanTrigger) : AttemptCmd
        data class AutoClose(val draft: PerpsAutoCloseDraft?) : AttemptCmd
        data object Hold : AttemptCmd
    }

    private data class PlanContext(val key: ReviewKey, val attempt: Int, val autoClose: PerpsAutoCloseEdit)

    private data class PlanRun(
        val key: ReviewKey,
        val mark: PerpsMarkQuote?,
        val previous: PerpsTradeIntent?,
        val autoClose: PerpsAutoCloseEdit,
    )

    private data class ReplanRun(
        val planned: PerpsPlannedTrade,
        val status: PerpsReviewStatus = PerpsReviewStatus.Loading,
        val settled: Boolean = false,
        val startedAtMs: Long = 0L,
        val visible: Boolean = true,
    )

    private sealed interface ConfirmOutcome {
        data object None : ConfirmOutcome
        data class Failed(@StringRes val text: Int) : ConfirmOutcome
        data class Rejected(val failure: PerpsPlanFailure) : ConfirmOutcome
    }

    private sealed interface StatusCmd {
        data class PlanStarted(val silent: Boolean) : StatusCmd
        data class PlanOk(
            val submitted: Boolean,
            val marginRisk: Boolean,
        ) : StatusCmd
        data class Failed(@StringRes val text: Int) : StatusCmd
        data object Submitting : StatusCmd
        data object PriceExpired : StatusCmd
    }

    private sealed interface SheetCmd {
        data class Failure(val failure: PerpsPlanFailure, val draft: PerpsAutoCloseDraft?) : SheetCmd
        data object Clear : SheetCmd
    }

    private val relay = MviRelay<PerpsReviewEvent>()

    val events = relay.events

    val symbol: String = args.symbol

    val iconUrl: String? = args.iconUrl

    val side = args.side

    private val operationId = UUID.randomUUID().toString()

    private val initialAutoClose = args.initialAutoClose()

    private val livePrice = livePrices
        .observeQuotes(flowOf(setOf(perpsTicker(args.symbol))))
        .map { it[perpsTicker(args.symbol)] }
        .cacheState(initialValue = null)

    private val wallet = unifiedAccountRepository.selectedTonWalletFlow
        .cacheState()

    // The passcode sheet is a dialog: it needs the screen's context, not the application one.
    private val screenContext = actions
        .on<PerpsReviewAction.Attach>()
        .map { it.context }
        .cacheState(initialValue = null)

    // A failed activation leaves no key, so Retry has to run the unlock again, not just replan.
    private val unlockAttempt = actions
        .on<PerpsReviewAction.Retry>()
        .runningFold(0) { attempt, _ -> attempt + 1 }
        .cacheState(initialValue = 0)

    private val credentialsState = combine(
        wallet,
        screenContext,
        unlockAttempt,
        transform = { wallet, context, attempt ->
            if (wallet != null && context != null) {
                UnlockContext(wallet = wallet, context = context, attempt = attempt)
            } else {
                null
            }
        }
    )
        .filterNotNull()
        .distinctUntilChangedBy { it.wallet.id to it.attempt }
        .transformStateLatest { unlock -> loadCredentials(unlock) }
        .cacheStateWithLoading()

    private val credentialsFailure = credentialsState
        .filterAndMapStateData()
        .filterResultError()
        .map { it.value }
        .onEach { failure ->
            when (failure) {
                PerpsUnlockFailure.COMING_SOON -> relay.emit(PerpsReviewEvent.ComingSoon)
                PerpsUnlockFailure.CANCELLED -> relay.emit(PerpsReviewEvent.Cancelled)
                PerpsUnlockFailure.FAILED -> Unit
            }
        }
        .cacheState(initialValue = null)

    private val credentials = credentialsState
        .filterAndMapStateData()
        .mapResultValueOrNull()
        .map { it?.value }
        .cacheState(initialValue = null)

    // The intent is immutable per operation, so a payload that lands late is redundant, never stale.
    // A position-scoped intent pins the position it read, so only an open re-quotes from the last one.
    private val lastIntent = actions
        .on<PerpsReviewAction.Internal.Planned>()
        .map { it.takeIf { planned -> planned.intent is PerpsTradeIntent.Open } }
        .cacheState(initialValue = null)

    private val planAttempt = merge(
        actions
            .on<PerpsReviewAction.Retry>()
            .map { AttemptCmd.Trigger(PlanTrigger.USER) },
        actions
            .on<PerpsReviewAction.Internal.Requote>()
            .map { AttemptCmd.Trigger(PlanTrigger.LIVE) },
        actions
            .on<PerpsReviewAction.Internal.SubmitRequote>()
            .map { AttemptCmd.Trigger(PlanTrigger.SUBMIT) },
        actions
            .on<PerpsReviewAction.SetAutoClose>()
            .map { AttemptCmd.AutoClose(it.draft) },
        actions
            .on<PerpsReviewAction.Confirm>()
            .map { AttemptCmd.Hold },
    )
        .runningFold(PlanAttempt(count = 0, trigger = PlanTrigger.USER, autoClose = initialAutoClose), ::reduceAttempt)
        .cacheState(initialValue = PlanAttempt(count = 0, trigger = PlanTrigger.USER, autoClose = initialAutoClose))

    private val planState = combine(
        credentials,
        planAttempt,
        transform = { key, attempt ->
            val key = key ?: return@combine null

            PlanContext(key = key, attempt = attempt.count, autoClose = attempt.autoClose)
        }
    )
        .distinctUntilChanged()
        .withLatestFrom(livePrice) { context, mark -> context to mark }
        .withLatestFrom(lastIntent) { (context, mark), last ->
            val context = context ?: return@withLatestFrom null
            val request = args.toRequest(context.key.walletId, context.autoClose)

            PlanRun(
                key = context.key,
                mark = mark?.takeIf { System.currentTimeMillis() - it.observedAtMs <= LIVE_MARK_MAX_AGE_MS },
                previous = last?.takeIf { it.request == request }?.intent,
                autoClose = context.autoClose,
            )
        }
        .transformStateLatest { run ->
            val run = run ?: return@transformStateLatest null

            plan(run)
        }
        .cacheStateWithLoading()

    private val plan = planState
        .filterAndMapStateData()
        .filterResultValue()
        .withLatestFrom(planAttempt) { result, attempt -> result.takeUnless { attempt.held } }
        .filterNotNull()
        .map { it.value }
        .onEach { sendAction(PerpsReviewAction.Internal.Planned(it.intent, it.request)) }
        .cacheState(initialValue = null)

    private val planRefusal = planState
        .filterAndMapStateData()
        .filterResultError()
        .map { it.value }
        .filter { !it.keepsReview() }
        .withLatestFrom(planAttempt) { failure, attempt -> failure to attempt.trigger }
        .onEach { (failure, trigger) ->
            when (trigger) {
                PlanTrigger.USER -> relay.emit(PerpsReviewEvent.PlanRefused(failure.statusText()))
                PlanTrigger.SUBMIT -> relay.emit(PerpsReviewEvent.SubmitFailed(failure.statusText()))
                PlanTrigger.LIVE -> Unit
            }
        }
        .cacheState(initialValue = null)

    override val rows = plan
        .map { it?.toContent(args) }
        .cacheState(initialValue = null)

    private val confirmState = actions
        .on<PerpsReviewAction.Confirm>()
        .withLatestFrom(plan) { _, planned -> planned }
        .transformStateFirst { planned -> submit(planned) }
        .cacheStateWithLoading()

    private val confirmOutcome = confirmState
        .filterAndMapStateData()

    private val submitFailure = confirmOutcome
        .mapNotNull { outcome ->
            when (outcome) {
                is ConfirmOutcome.Failed -> outcome.text
                is ConfirmOutcome.Rejected -> if (outcome.failure.keepsReview()) {
                    null
                } else {
                    outcome.failure.statusText()
                }

                ConfirmOutcome.None -> null
            }
        }
        .onEach { relay.emit(PerpsReviewEvent.SubmitFailed(it)) }
        .cacheState(initialValue = null)

    private val planMarginRisk = plan
        .filterNotNull()
        .map { it.plan.review.isMarginRisk() }

    override val status = merge(
        credentialsFailure
            .filter { it == PerpsUnlockFailure.FAILED }
            .map { StatusCmd.Failed(Localization.perps_review_failed) },
        planState
            .filterStateLoading()
            .withLatestFrom(planAttempt) { _, attempt ->
                StatusCmd.PlanStarted(silent = attempt.trigger != PlanTrigger.USER)
            },
        planMarginRisk
            .withLatestFrom(planAttempt) { marginRisk, attempt ->
                StatusCmd.PlanOk(
                    submitted = attempt.trigger == PlanTrigger.SUBMIT,
                    marginRisk = marginRisk,
                )
            },
        planState
            .filterAndMapStateData()
            .filterResultError()
            .withLatestFrom(planAttempt) { failure, attempt -> failure.value to attempt.trigger }
            .filter { (failure, trigger) -> trigger != PlanTrigger.LIVE && failure.keepsReview() }
            .map { (failure, _) -> StatusCmd.Failed(failure.statusText()) },
        actions
            .on<PerpsReviewAction.Confirm>()
            .map { StatusCmd.Submitting },
        confirmOutcome
            .filterIsInstance<ConfirmOutcome.Rejected>()
            .filter { it.failure.keepsReview() }
            .map { StatusCmd.Failed(it.failure.statusText()) },
        actions
            .on<PerpsReviewAction.Internal.PriceExpired>()
            .map { StatusCmd.PriceExpired },
    )
        .runningFold(PerpsReviewStatus.Loading, ::reduceStatus)
        .cacheState(initialValue = PerpsReviewStatus.Loading)

    override val autoCloseEditor = combine(
        plan,
        planAttempt,
        status,
        transform = { planned, attempt, status ->
            if (status == PerpsReviewStatus.Loading || status == PerpsReviewStatus.Submitting) {
                null
            } else {
                planned?.autoCloseEditor(args, attempt.autoClose)
            }
        }
    )
        .cacheState(initialValue = null)

    override val sheet = merge(
        planState
            .filterAndMapStateData()
            .filterResultError()
            .withLatestFrom(planAttempt) { failure, attempt -> failure.value to attempt }
            .filter { (_, attempt) -> attempt.trigger != PlanTrigger.LIVE }
            .map { (failure, attempt) ->
                SheetCmd.Failure(failure, (attempt.autoClose as? PerpsAutoCloseEdit.Set)?.target)
            },
        confirmOutcome
            .filterIsInstance<ConfirmOutcome.Rejected>()
            .withLatestFrom(planAttempt) { rejected, attempt ->
                SheetCmd.Failure(rejected.failure, (attempt.autoClose as? PerpsAutoCloseEdit.Set)?.target)
            },
        actions
            .on<PerpsReviewAction.Retry>()
            .map { SheetCmd.Clear },
        actions
            .on<PerpsReviewAction.DismissSheet>()
            .map { SheetCmd.Clear },
        actions
            .on<PerpsReviewAction.UpdateAutoClose>()
            .onEach { relay.emit(PerpsReviewEvent.UpdateAutoClose(it.fix)) }
            .map { SheetCmd.Clear },
    )
        .runningFold(null, ::reduceSheet)
        .cacheState(initialValue = null)

    override val canConfirm = combine(
        rows,
        status,
        transform = { rows, status ->
            rows != null && (status == PerpsReviewStatus.Ready || status == PerpsReviewStatus.PriceUpdated)
        }
    )
        .cacheState(initialValue = false)

    private val priceUpdatedCountdown = status
        .map { current ->
            if (current == PerpsReviewStatus.PriceUpdated) {
                current
            } else {
                null
            }
        }
        .countdown(PRICE_UPDATED)
        .onEach { countdown ->
            if (countdown != null && countdown.finished) {
                sendAction(PerpsReviewAction.Internal.PriceExpired)
            }
        }
        .cacheState(initialValue = null)

    // The mark feed can stall for minutes, so the plan ages and expires on its own clock.
    private val replanTick = flow {
        while (true) {
            delay(REPLAN_TICK)
            emit(Unit)
        }
    }

    private val planStartedAtMs = planState
        .filterStateLoading()
        .map { System.currentTimeMillis() }
        .cacheState(initialValue = 0L)

    private val visible = actions
        .on<PerpsReviewAction.SetVisible>()
        .map { it.visible }
        .cacheState(initialValue = true)

    private val replanTrigger = replanTick
        .withLatestFrom(plan) { _, planned -> planned?.let(::ReplanRun) }
        .filterNotNull()
        .withLatestFrom(status) { run, current -> run.copy(status = current) }
        .withLatestFrom(planState) { run, state -> run.copy(settled = state is KState.Data) }
        .withLatestFrom(planStartedAtMs) { run, startedAt -> run.copy(startedAtMs = startedAt) }
        .withLatestFrom(visible) { run, shown -> run.copy(visible = shown) }
        .onEach { run ->
            val ready = run.status == PerpsReviewStatus.Ready ||
                run.status == PerpsReviewStatus.PriceUpdated
            val now = System.currentTimeMillis()
            val limitOpen = (args as? PerpsReviewArgs.Open)?.limitPrice != null
            val aged = !limitOpen && now - run.startedAtMs >= REPLAN_MIN_AGE_MS
            val expiring = now >= run.planned.plan.validUntilUnixMs - REPLAN_LEAD_MS
            if (run.visible && run.settled && ready && (aged || expiring)) {
                sendAction(PerpsReviewAction.Internal.Requote)
            }
        }
        .cacheState(initialValue = null)

    // Loaders
    private suspend fun loadCredentials(attempt: UnlockContext): KResult<ReviewKey, PerpsUnlockFailure> {
        return when (val result = accountService.unlock(attempt.wallet, attempt.context)) {
            is KResult.Ok -> KResult.Ok(ReviewKey(walletId = attempt.wallet.id, credentials = result.value))
            is KResult.Err -> KResult.Err(result.value)
        }
    }

    private suspend fun plan(run: PlanRun): KResult<PerpsPlannedTrade, PerpsPlanFailure> {
        var attempts = PLAN_ATTEMPTS
        while (true) {
            attempts--
            val result: KResult<PerpsPlannedTrade, PerpsPlanFailure> = withContext(Async.Io) {
                try {
                    adapter.plan(
                        request = args.toRequest(run.key.walletId, run.autoClose),
                        credentials = run.key.credentials,
                        mark = run.mark,
                        operationId = operationId,
                        previous = run.previous,
                    )
                } catch (t: Throwable) {
                    verifyError(t)
                    L.e(t)
                    KResult.Err(PerpsPlanFailure.Load(PerpsError.Unknown))
                }
            }
            if (result is KResult.Ok) {
                L.d("Perps review plan ${result.value.plan.planId}: ${result.value.plan.review}")
                return result
            }

            val failure = (result as KResult.Err).value
            val retryable = failure is PerpsPlanFailure.Planner && failure.kind in PERPS_RETRYABLE_PLAN_KINDS
            if (!retryable || attempts == 0) {
                return result
            }
        }
    }

    private suspend fun submit(planned: PerpsPlannedTrade?): ConfirmOutcome {
        if (planned == null) {
            return ConfirmOutcome.Failed(Localization.perps_review_failed)
        }
        val open = args as? PerpsReviewArgs.Open
        open?.let(PerpsAnalytics::orderSubmitted)

        val start = try {
            executor.start(planned)
        } catch (t: Throwable) {
            verifyError(t)
            L.e(t)
            open?.let { PerpsAnalytics.orderFailed(PerpsReason.Other) }
            return ConfirmOutcome.Failed(Localization.perps_review_failed)
        }
        if (open != null) {
            start.failureReason()?.let(PerpsAnalytics::orderFailed)
        }

        return when (start) {
            is PerpsSubmitStart.Started -> {
                relay.emit(PerpsReviewEvent.Submitted)
                ConfirmOutcome.None
            }

            is PerpsSubmitStart.Requote -> {
                sendAction(PerpsReviewAction.Internal.SubmitRequote)
                ConfirmOutcome.None
            }

            is PerpsSubmitStart.Blocked -> {
                relay.emit(PerpsReviewEvent.InProgress)
                ConfirmOutcome.None
            }

            is PerpsSubmitStart.Failed -> when (val failure = start.failure) {
                is PerpsTradeFailure.PlannerRejected -> ConfirmOutcome.Rejected(
                    PerpsPlanFailure.Planner(
                        kind = failure.kind,
                        message = "",
                        field = failure.field,
                        suggestion = failure.suggestion,
                        priceDecimals = planned.plan.review.scale.priceDecimals,
                    )
                )

                PerpsTradeFailure.OperationInProgress -> {
                    relay.emit(PerpsReviewEvent.InProgress)
                    ConfirmOutcome.None
                }

                else -> ConfirmOutcome.Failed(Localization.perps_review_failed)
            }
        }
    }

    private fun PerpsPlanFailure.keepsReview(): Boolean {
        return this is PerpsPlanFailure.Load ||
            (this is PerpsPlanFailure.Planner && kind in SHEET_KINDS)
    }

    // Reducers
    private fun reduceAttempt(attempt: PlanAttempt, cmd: AttemptCmd): PlanAttempt = when (cmd) {
        is AttemptCmd.Trigger -> PlanAttempt(
            count = attempt.count + 1,
            trigger = cmd.trigger,
            autoClose = attempt.autoClose,
        )

        is AttemptCmd.AutoClose -> PlanAttempt(
            count = attempt.count + 1,
            trigger = PlanTrigger.USER,
            autoClose = PerpsAutoCloseEdit.Set(
                (args as? PerpsReviewArgs.SizeChange)
                    ?.let { cmd.draft.withRestingLegs(it.direction, it.restingDraft()) }
                    ?: cmd.draft,
            ),
        )

        AttemptCmd.Hold -> attempt.copy(held = true)
    }

    private fun reduceStatus(status: PerpsReviewStatus, cmd: StatusCmd): PerpsReviewStatus = when (cmd) {
        is StatusCmd.PlanStarted -> if (cmd.silent) {
            status
        } else {
            PerpsReviewStatus.Loading
        }

        is StatusCmd.PlanOk -> when {
            cmd.marginRisk -> PerpsReviewStatus.Failed(Localization.perps_margin_reduce_risk)
            cmd.submitted -> PerpsReviewStatus.PriceUpdated
            status == PerpsReviewStatus.Submitting || status == PerpsReviewStatus.PriceUpdated -> status
            else -> PerpsReviewStatus.Ready
        }

        is StatusCmd.Failed -> PerpsReviewStatus.Failed(cmd.text)

        StatusCmd.Submitting -> PerpsReviewStatus.Submitting

        StatusCmd.PriceExpired -> if (status == PerpsReviewStatus.PriceUpdated) {
            PerpsReviewStatus.Ready
        } else {
            status
        }
    }

    private fun reduceSheet(sheet: PerpsReviewSheet?, cmd: SheetCmd): PerpsReviewSheet? {
        val failure = when (cmd) {
            SheetCmd.Clear -> return null
            is SheetCmd.Failure -> cmd.failure as? PerpsPlanFailure.Planner ?: return sheet
        }

        return when (failure.kind) {
            PerpsTradeError.SlippageExceeded -> PerpsReviewSheet.OrderNotPlaced(PerpsProductRules.PRICE_SLIPPAGE_PPM)

            PerpsTradeError.InvalidAutoClose -> {
                val fix = failure.toAutoCloseFix(cmd.draft)
                if (fix != null) {
                    PerpsReviewSheet.AutoCloseUpdate(fix)
                } else {
                    sheet
                }
            }

            else -> sheet
        }
    }

    private companion object {
        const val REPLAN_MIN_AGE_MS = 1_000L
        const val LIVE_MARK_MAX_AGE_MS = 10_000L
        const val REPLAN_LEAD_MS = 10_000L
        const val PLAN_ATTEMPTS = 2

        val REPLAN_TICK = 250.milliseconds
        val PRICE_UPDATED = 2.seconds

        val SHEET_KINDS = setOf(
            PerpsTradeError.SlippageExceeded,
            PerpsTradeError.InvalidAutoClose,
        )
    }
}

internal val PERPS_RETRYABLE_PLAN_KINDS = setOf(
    PerpsTradeError.StaleInput,
    PerpsTradeError.PlanExpired,
    PerpsTradeError.PositionChanged,
    PerpsTradeError.UnusableSnapshot,
    PerpsTradeError.NeedsMoreDepth,
)

@StringRes
private fun PerpsPlanFailure.statusText(): Int {
    if (this is PerpsPlanFailure.ClockSkew) {
        return Localization.perps_review_wrong_device_time
    }
    if (this !is PerpsPlanFailure.Planner) {
        return Localization.perps_review_failed
    }

    return when (kind) {
        PerpsTradeError.InsufficientBalance -> Localization.perps_order_insufficient_balance

        PerpsTradeError.TradingDisabled,
        PerpsTradeError.UnsupportedMarket -> Localization.perps_order_trading_disabled

        PerpsTradeError.ConflictingPosition -> Localization.perps_review_conflicting_position

        PerpsTradeError.InsufficientLiquidity -> Localization.perps_order_insufficient_liquidity

        PerpsTradeError.SlippageExceeded -> Localization.perps_review_order_not_placed_title

        PerpsTradeError.NoPosition -> Localization.perps_review_no_position

        PerpsTradeError.PositionChanged -> Localization.perps_review_position_changed

        PerpsTradeError.ExceedsPosition,
        PerpsTradeError.MarginBelowRequirement -> Localization.perps_review_exceeds_margin

        PerpsTradeError.InvalidInput,
        PerpsTradeError.UnrepresentableValue -> Localization.perps_review_invalid_amount

        PerpsTradeError.InvalidAutoClose -> field.toAutoCloseField().invalidAutoCloseText()

        else -> Localization.perps_review_failed
    }
}

private fun PerpsPlanFailure.Planner.toAutoCloseFix(draft: PerpsAutoCloseDraft?): PerpsAutoCloseFix? {
    val decimals = priceDecimals
    val target = field.toAutoCloseField() ?: return null

    return PerpsAutoCloseFix(
        field = target,
        suggestion = if (suggestion != null && decimals != null) {
            BigDecimal.valueOf(suggestion, decimals)
        } else {
            null
        },
        draft = draft,
    )
}

private fun PerpsReviewArgs.toRequest(walletId: String, autoClose: PerpsAutoCloseEdit): PerpsTradeRequest {
    return when (this) {
        is PerpsReviewArgs.Open -> PerpsTradeRequest.Open(
            walletId = walletId,
            marketIndex = marketIndex,
            side = side,
            marginQuote = BigDecimal(marginQuote),
            leverage = leverage,
            autoClose = (autoClose as? PerpsAutoCloseEdit.Set)?.target,
            limitPrice = limitPrice?.let(::BigDecimal),
        )

        is PerpsReviewArgs.Close -> PerpsTradeRequest.Close(
            walletId = walletId,
            marketIndex = marketIndex,
        )

        is PerpsReviewArgs.SizeChange -> if (direction == PerpsSizeDirection.ADD) {
            PerpsTradeRequest.Add(
                walletId = walletId,
                marketIndex = marketIndex,
                marginDelta = BigDecimal(marginDelta),
                autoClose = autoClose,
            )
        } else {
            PerpsTradeRequest.Reduce(
                walletId = walletId,
                marketIndex = marketIndex,
                marginDelta = BigDecimal(marginDelta),
                autoClose = autoClose,
            )
        }

        is PerpsReviewArgs.Margin -> PerpsTradeRequest.Margin(
            walletId = walletId,
            marketIndex = marketIndex,
            amountQuote = BigDecimal(amountQuote),
            add = add,
        )
    }
}

private fun PerpsReviewArgs.initialAutoClose(): PerpsAutoCloseEdit {
    return when (this) {
        is PerpsReviewArgs.Open -> PerpsAutoCloseEdit.Set(autoCloseDraft(takeProfit, stopLoss, takeProfitRoi, stopLossRoi))
        is PerpsReviewArgs.SizeChange -> autoClose.toEdit()
        else -> PerpsAutoCloseEdit.Keep
    }
}

private fun PerpsReviewArgs.SizeChange.restingDraft(): PerpsAutoCloseDraft? {
    return autoCloseDraft(restingTakeProfit, restingStopLoss)
}

private fun PerpsAutoCloseEdit.legs(args: PerpsReviewArgs): PerpsAutoCloseDraft? {
    return when (this) {
        PerpsAutoCloseEdit.Keep -> (args as? PerpsReviewArgs.SizeChange)?.restingDraft()
        is PerpsAutoCloseEdit.Set -> target
    }
}

private fun PerpsPlannedTrade.autoCloseLegs(args: PerpsReviewArgs): PerpsAutoCloseDraft? {
    return when (val request = request) {
        is PerpsTradeRequest.Open -> request.autoClose
        is PerpsTradeRequest.Add -> request.autoClose.legs(args)
        is PerpsTradeRequest.Reduce -> request.autoClose.legs(args)
        else -> null
    }
}

private fun PerpsPlannedTrade.positionLeverage(): Int? {
    return (context.marginSettings as? PerpsMarginSettings.Known)
        ?.let { PerpsProductRules.leverage(it.initialMarginBps) }
}

private fun PerpsPlannedTrade.autoCloseEditor(
    args: PerpsReviewArgs,
    edit: PerpsAutoCloseEdit,
): PerpsReviewAutoCloseEditor? {
    if (args is PerpsReviewArgs.SizeChange && !args.autoCloseEditable) {
        return null
    }
    if (args is PerpsReviewArgs.Open && !args.autoCloseEditable) {
        return null
    }

    val review = plan.review
    val scale = review.scale
    fun price(value: Long): BigDecimal = BigDecimal.valueOf(value, scale.priceDecimals)
    val limitPrice = (args as? PerpsReviewArgs.Open)?.limitPrice?.let(::BigDecimal)
    val (entry, base, liquidation) = when (review) {
        is PerpetualReview.Open -> Triple(
            limitPrice ?: markPrice,
            review.baseAmount,
            review.liquidationPrice,
        )

        is PerpetualReview.Add -> Triple(price(review.mergedEntryPrice), review.mergedBaseAmount, review.liquidationPrice)

        is PerpetualReview.Close -> {
            val entryPrice = (context.position as? PerpsPositionState.Present)?.entryPrice?.let(::price) ?: markPrice
            if (review.fullExit) {
                return null
            }
            Triple(entryPrice, review.remainingBaseAmount, review.liquidationPrice)
        }

        else -> return null
    }
    val leverage = ((request as? PerpsTradeRequest.Open)?.leverage ?: positionLeverage())?.takeIf { it > 0 } ?: 1

    return PerpsReviewAutoCloseEditor(
        priceDecimals = scale.priceDecimals,
        bounds = PerpsAutoCloseBounds(
            entryPrice = entry,
            leverage = BigDecimal(leverage),
            currentPrice = if (review is PerpetualReview.Open) {
                entry
            } else {
                markPrice
            },
            liquidationPrice = liquidation?.let(::price),
        ),
        initial = edit.legs(args),
        estimate = autoCloseEstimate(baseAmount(base, scale), entry),
        limitPrice = limitPrice,
    )
}

private fun PerpsAutoCloseArgs.toEdit(): PerpsAutoCloseEdit {
    return when (this) {
        PerpsAutoCloseArgs.Keep -> PerpsAutoCloseEdit.Keep
        is PerpsAutoCloseArgs.Set -> PerpsAutoCloseEdit.Set(autoCloseDraft(takeProfit, stopLoss, takeProfitRoi, stopLossRoi))
    }
}

private fun autoCloseDraft(
    takeProfit: String?,
    stopLoss: String?,
    takeProfitRoi: String? = null,
    stopLossRoi: String? = null,
): PerpsAutoCloseDraft? {
    if (takeProfit == null && stopLoss == null) {
        return null
    }

    return PerpsAutoCloseDraft(
        takeProfit = takeProfit?.let { BigDecimal(it) },
        stopLoss = stopLoss?.let { BigDecimal(it) },
        takeProfitRoi = takeProfitRoi?.let { BigDecimal(it) },
        stopLossRoi = stopLossRoi?.let { BigDecimal(it) },
    )
}

private fun PerpetualReview.isMarginRisk(): Boolean {
    return this is PerpetualReview.Margin && !addToPosition && isImmediateRisk
}

private fun PerpsPlannedTrade.toContent(args: PerpsReviewArgs): PerpsReviewContent? {
    val scale = plan.review.scale
    val legs = autoCloseLegs(args)
    val position = context.position as? PerpsPositionState.Present
    val leverage = positionLeverage() ?: 0

    return when (val review = plan.review) {
        is PerpetualReview.Open -> openContent(
            review = review,
            scale = scale,
            leverage = (request as? PerpsTradeRequest.Open)?.leverage ?: leverage,
            legs = legs,
            autoCloseEditable = (args as? PerpsReviewArgs.Open)?.autoCloseEditable == true,
        )

        is PerpetualReview.Close -> if (review.fullExit) {
            closeContent(review, scale, leverage)
        } else {
            reduceContent(review, args as? PerpsReviewArgs.SizeChange, position, scale, leverage, legs)
        }

        is PerpetualReview.Add -> addContent(review, position, scale, leverage, legs)

        is PerpetualReview.Margin -> marginContent(review, position, scale)

        else -> null
    }
}

private fun openContent(
    review: PerpetualReview.Open,
    scale: PerpsScale,
    leverage: Int,
    legs: PerpsAutoCloseDraft?,
    autoCloseEditable: Boolean,
): PerpsReviewContent {
    val margin = quoteAmount(review.marginBudgetQuote, scale)
    val notional = quoteAmount(review.notionalQuote, scale)
    val fee = quoteAmount(review.estimatedFeeQuote, scale)
    val autoClose = autoCloseRow(legs, scale.priceDecimals)
        ?: PerpsReviewRow(
            title = Localization.perps_order_auto_close,
            value = PerpsReviewValue.AutoClose(takeProfit = null, stopLoss = null),
        ).takeIf { autoCloseEditable }

    return PerpsReviewContent(
        title = Localization.perps_review_open_title,
        rows = listOfNotNull(
            PerpsReviewRow(
                title = Localization.perps_review_you_pay,
                value = PerpsReviewValue.Text(formatUsdAmount(margin)),
                sub = PerpsReviewSub.Quote(formatQuoteAmount(margin)),
            ),
            PerpsReviewRow(
                title = Localization.perps_review_entry_price,
                value = PerpsReviewValue.Text(priceText(review.estimatedEntryPrice, scale)),
            ),
            PerpsReviewRow(
                title = Localization.perps_order_leverage,
                value = PerpsReviewValue.Leverage(leverage),
            ),
            liquidationRow(review.liquidationPrice, null, review.isImmediateRisk, scale),
            PerpsReviewRow(
                title = Localization.perps_size,
                value = PerpsReviewValue.Text(formatUsdAmount(notional)),
                sub = PerpsReviewSub.Base(formatPlainDecimal(baseAmount(review.baseAmount, scale))),
            ),
            feeRow(fee),
            autoClose,
        ),
    )
}

private fun closeContent(
    review: PerpetualReview.Close,
    scale: PerpsScale,
    leverage: Int,
): PerpsReviewContent {
    val returned = quoteAmount(review.estimatedReturnedMarginQuote, scale)
    val notional = quoteAmount(review.notionalQuote, scale)
    val realized = quoteAmount(review.estimatedRealizedPnlQuote, scale)
    val receive = quoteAmount(review.estimatedReceiveQuote, scale)

    return PerpsReviewContent(
        title = Localization.perps_review_close_title,
        rows = listOf(
            PerpsReviewRow(
                title = Localization.perps_review_position,
                value = PerpsReviewValue.Text(formatUsdAmount(returned)),
                sub = PerpsReviewSub.Quote(formatQuoteAmount(returned)),
            ),
            PerpsReviewRow(
                title = Localization.perps_order_leverage,
                value = PerpsReviewValue.Leverage(leverage),
            ),
            PerpsReviewRow(
                title = Localization.perps_size,
                value = PerpsReviewValue.Text(formatUsdAmount(notional)),
                sub = PerpsReviewSub.Base(
                    formatPlainDecimal(baseAmount(review.estimatedFillBaseAmount, scale)),
                ),
            ),
            feeRow(quoteAmount(review.estimatedFeeQuote, scale)),
            PerpsReviewRow(
                title = Localization.perps_review_pnl,
                value = PerpsReviewValue.Text(formatSignedUsdAmount(realized)),
                sub = if (returned.signum() > 0) {
                    PerpsReviewSub.Percent(formatSignedPercent(percentOf(realized, returned)))
                } else {
                    null
                },
                tone = if (realized.signum() < 0) {
                    PerpsReviewTone.NEGATIVE
                } else {
                    PerpsReviewTone.POSITIVE
                },
            ),
            PerpsReviewRow(
                title = Localization.perps_review_you_receive,
                value = PerpsReviewValue.Text(formatUsdAmount(receive)),
                sub = PerpsReviewSub.Quote(formatQuoteAmount(receive)),
            ),
        ),
    )
}

private fun reduceContent(
    review: PerpetualReview.Close,
    args: PerpsReviewArgs.SizeChange?,
    position: PerpsPositionState.Present?,
    scale: PerpsScale,
    leverage: Int,
    legs: PerpsAutoCloseDraft?,
): PerpsReviewContent {
    val closed = args?.marginDelta?.let { BigDecimal(it) }
        ?: quoteAmount(review.estimatedReturnedMarginQuote, scale)
    val entry = position?.entryPrice
    val entryValue = entry?.let { BigDecimal.valueOf(it, scale.priceDecimals) } ?: BigDecimal.ZERO

    return PerpsReviewContent(
        title = Localization.perps_review_reduce_title,
        rows = listOfNotNull(
            PerpsReviewRow(
                title = Localization.perps_review_you_close,
                value = PerpsReviewValue.Text(formatUsdAmount(closed)),
                sub = PerpsReviewSub.Quote(formatQuoteAmount(closed)),
            ),
            PerpsReviewRow(
                title = Localization.perps_review_entry_price,
                value = PerpsReviewValue.Text(priceText(entry, scale)),
            ),
            PerpsReviewRow(
                title = Localization.perps_order_leverage,
                value = PerpsReviewValue.Leverage(leverage),
            ),
            liquidationRow(
                value = review.liquidationPrice,
                previous = position?.liquidationPrice,
                atRisk = review.isImmediateRisk,
                scale = scale,
            ),
            PerpsReviewRow(
                title = Localization.perps_size,
                value = PerpsReviewValue.Text(
                    formatUsdAmount(baseAmount(review.remainingBaseAmount, scale).multiply(entryValue)),
                ),
                previous = position?.let {
                    PerpsReviewValue.Text(
                        formatUsdAmount(baseAmount(it.baseAmount, scale).multiply(entryValue)),
                    )
                },
                sub = PerpsReviewSub.BaseDelta(
                    amount = formatPlainDecimal(baseAmount(review.estimatedFillBaseAmount, scale)),
                    added = false,
                ),
            ),
            feeRow(quoteAmount(review.estimatedFeeQuote, scale)),
            autoCloseRow(legs, scale.priceDecimals),
        ),
    )
}

private fun addContent(
    review: PerpetualReview.Add,
    position: PerpsPositionState.Present?,
    scale: PerpsScale,
    leverage: Int,
    legs: PerpsAutoCloseDraft?,
): PerpsReviewContent {
    val margin = quoteAmount(review.marginBudgetQuote, scale)
    val entryValue = position?.entryPrice?.let { BigDecimal.valueOf(it, scale.priceDecimals) }
        ?: BigDecimal.ZERO
    val mergedEntry = BigDecimal.valueOf(review.mergedEntryPrice, scale.priceDecimals)

    return PerpsReviewContent(
        title = Localization.perps_review_add_title,
        rows = listOfNotNull(
            PerpsReviewRow(
                title = Localization.perps_review_you_pay,
                value = PerpsReviewValue.Text(formatUsdAmount(margin)),
                sub = PerpsReviewSub.Quote(formatQuoteAmount(margin)),
            ),
            PerpsReviewRow(
                title = Localization.perps_review_entry_price,
                value = PerpsReviewValue.Text(priceText(review.mergedEntryPrice, scale)),
                previous = position?.let { PerpsReviewValue.Text(priceText(it.entryPrice, scale)) },
            ),
            PerpsReviewRow(
                title = Localization.perps_order_leverage,
                value = PerpsReviewValue.Leverage(leverage),
            ),
            liquidationRow(
                value = review.liquidationPrice,
                previous = position?.liquidationPrice,
                atRisk = review.isImmediateRisk,
                scale = scale,
            ),
            PerpsReviewRow(
                title = Localization.perps_size,
                value = PerpsReviewValue.Text(
                    formatUsdAmount(baseAmount(review.mergedBaseAmount, scale).multiply(mergedEntry)),
                ),
                previous = position?.let {
                    PerpsReviewValue.Text(
                        formatUsdAmount(baseAmount(it.baseAmount, scale).multiply(entryValue)),
                    )
                },
                sub = PerpsReviewSub.BaseDelta(
                    amount = formatPlainDecimal(baseAmount(review.baseAmount, scale)),
                    added = true,
                ),
            ),
            feeRow(quoteAmount(review.estimatedFeeQuote, scale)),
            autoCloseRow(legs, scale.priceDecimals),
        ),
    )
}

private fun marginContent(
    review: PerpetualReview.Margin,
    position: PerpsPositionState.Present?,
    scale: PerpsScale,
): PerpsReviewContent {
    val amount = quoteAmount(review.amountQuote, scale)

    return PerpsReviewContent(
        title = if (review.addToPosition) {
            Localization.perps_review_margin_add_title
        } else {
            Localization.perps_review_margin_remove_title
        },
        rows = listOfNotNull(
            PerpsReviewRow(
                title = if (review.addToPosition) {
                    Localization.perps_review_you_pay
                } else {
                    Localization.perps_review_you_close
                },
                value = PerpsReviewValue.Text(formatUsdAmount(amount)),
                sub = PerpsReviewSub.Quote(formatQuoteAmount(amount)),
            ),
            liquidationRow(
                value = review.liquidationPrice,
                previous = position?.liquidationPrice,
                atRisk = review.isImmediateRisk,
                scale = scale,
            ),
        ),
    )
}

private fun liquidationRow(
    value: Long?,
    previous: Long?,
    atRisk: Boolean,
    scale: PerpsScale,
): PerpsReviewRow {
    return PerpsReviewRow(
        title = Localization.perps_review_liquidation,
        value = PerpsReviewValue.Text(priceText(value, scale)),
        previous = previous?.let { PerpsReviewValue.Text(priceText(it, scale)) },
        tone = if (atRisk) {
            PerpsReviewTone.WARNING
        } else {
            PerpsReviewTone.NEUTRAL
        },
    )
}

private fun feeRow(fee: BigDecimal): PerpsReviewRow {
    return PerpsReviewRow(
        title = Localization.perps_review_fee,
        value = PerpsReviewValue.Text(formatUsdAmount(fee)),
        sub = PerpsReviewSub.Quote(formatQuoteAmount(fee)),
    )
}

private fun autoCloseRow(legs: PerpsAutoCloseDraft?, priceDecimals: Int): PerpsReviewRow? {
    val takeProfit = legs?.takeProfit
    val stopLoss = legs?.stopLoss
    if (takeProfit == null && stopLoss == null) {
        return null
    }

    return PerpsReviewRow(
        title = Localization.perps_order_auto_close,
        value = PerpsReviewValue.AutoClose(
            takeProfit = takeProfit?.let { formatAutoClosePrice(it, priceDecimals) },
            stopLoss = stopLoss?.let { formatAutoClosePrice(it, priceDecimals) },
        ),
    )
}

private fun priceText(value: Long?, scale: PerpsScale): String {
    val price = value ?: return PERPS_EMPTY_VALUE

    return formatUsdPrice(
        BigDecimal.valueOf(price, scale.priceDecimals).toDouble(),
        scale.priceDecimals,
    )
}

private fun quoteAmount(value: Long, scale: PerpsScale): BigDecimal {
    return BigDecimal.valueOf(value, scale.quoteDecimals)
}

private fun baseAmount(value: Long, scale: PerpsScale): BigDecimal {
    return BigDecimal.valueOf(value, scale.baseDecimals)
}

private fun percentOf(value: BigDecimal, total: BigDecimal): BigDecimal {
    return value.multiply(HUNDRED).divide(total, PERCENT_SCALE, RoundingMode.HALF_UP)
}

private const val PERCENT_SCALE = 2
private val HUNDRED = BigDecimal(100)

/*
 *          selectedTonWalletFlow ──► wallet? ──┐
 *                   Attach ──► screenContext? ─┼──► [distinct, latest] credentialsState
 *            Retry ──► [fold] unlockAttempt ───┘
 *
 *   credentialsState [dataOnly, errOnly] ──► credentialsFailure? ──┬──► events
 *                                                                  └──► status
 *   credentialsState [dataOnly, okOrNull] ──► credentials? ──────────┐
 *                                                                    │
 *                           Planned ──► lastIntent? ⋯────────────────┤
 *   Retry / Requote / SubmitRequote ──┐                              │
 *                      SetAutoClose ──┤                              │
 *                           Confirm ──┴──► [fold] planAttempt ───────┼──► [distinct, latest] planState
 *                                                    livePrice? ⋯────┘
 *
 *   planState [dataOnly, okOnly] ──┐
 *                                  ├──► plan? ──┬──► rows?
 *                 planAttempt ⋯────┘            ├──► planMarginRisk ──► status
 *                                               ╎
 *                          Planned ◄╌╌╌╌╌╌╌╌╌╌╌╌┘
 *
 *   planState [dataOnly, errOnly] ──► planRefusal? ──► events
 *                     planAttempt ⋯──┘
 *
 *                        Confirm ──┐
 *                                  ├──► [first] confirmState ──┬──► submitFailure? ──► events
 *                          plan? ⋯─┘                           ├──► events
 *                                                              ├──► status
 *                                                              ├──► sheet
 *                                                              ╎
 *                   SubmitRequote ◄╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌╌┘
 *
 *              credentialsFailure? ──┐
 *                       planState ───┤
 *                    planAttempt ⋯───┤
 *                  planMarginRisk ───┤
 *                         Confirm ───┼──► [fold] status
 *                   confirmState ────┤
 *                    PriceExpired ───┘
 *
 *                       planState ───┐
 *                    confirmState ───┤
 *                    planAttempt ⋯───┤
 *                           Retry ───┼──► [fold] sheet?
 *                    DismissSheet ───┤
 *                 UpdateAutoClose ───┴──► events
 *
 *                           rows? ──┐
 *                                   ├──► canConfirm
 *                          status ──┘
 *
 *                           plan? ──┐
 *                     planAttempt ──┼──► autoCloseEditor?
 *                          status ──┘
 *
 *   status ──► [countdown] priceUpdatedCountdown?
 *                                    ╎
 *                    PriceExpired ◄╌╌┘
 *
 *   planState [loading] ──► planStartedAtMs
 *
 *   SetVisible ──► visible
 *
 *               [tick] replanTick ──┐
 *                          plan? ⋯──┤
 *                         status ⋯──┤
 *                      planState ⋯──┼──► replanTrigger?
 *                planStartedAtMs ⋯──┤        ╎
 *                        visible ⋯──┘        ╎
 *                                            ╎
 *                         Requote ◄╌╌╌╌╌╌╌╌╌╌┘
 */
