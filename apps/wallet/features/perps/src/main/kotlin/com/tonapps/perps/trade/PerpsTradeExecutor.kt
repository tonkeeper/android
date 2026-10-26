package com.tonapps.perps.trade

import com.tonapps.async.Async
import com.tonapps.chainkit.lighter.LighterConstants
import com.tonapps.chainkit.lighter.trading.PerpsExecutionErrorKind
import com.tonapps.chainkit.lighter.trading.PerpsExecutionException
import com.tonapps.chainkit.lighter.trading.PerpsSubmissionOutcome
import com.tonapps.chainkit.perpetual.PerpetualApproval
import com.tonapps.chainkit.perpetual.PerpetualReview
import com.tonapps.chainkit.perpetual.PerpetualStep
import com.tonapps.chainkit.perpetual.PerpetualValidation
import com.tonapps.chainkit.perpetual.PerpsAppliedPrerequisite
import com.tonapps.chainkit.perpetual.PerpsAutoCloseSpec
import com.tonapps.chainkit.perpetual.PerpsMarginSettings
import com.tonapps.chainkit.perpetual.PerpsSigningValidationContext
import com.tonapps.chainkit.perpetual.PerpsTradeError
import com.tonapps.chainkit.perpetual.PerpsTradeException
import com.tonapps.chainkit.perpetual.PerpsSide
import com.tonapps.chainkit.perpetual.PerpsTradeIntent
import com.tonapps.log.L
import com.tonapps.mvi.graph.KResult
import com.tonapps.perps.data.PerpsAccountRefresh
import com.tonapps.perps.data.PerpsError
import com.tonapps.perps.data.PerpsPositionState
import com.tonapps.perps.data.PerpsRepository
import com.tonapps.perps.data.PerpsSendReason
import com.tonapps.perps.data.isDeadOrderStatus
import com.tonapps.perps.data.isFilledOrderStatus
import com.tonapps.perps.data.isWorkingOrderStatus
import com.tonapps.wallet.data.multichain.lighter.PerpetualRepository
import com.tonapps.wallet.data.multichain.perps.PerpsOperationJournal
import com.tonapps.perps.data.PerpsPositionSide
import com.tonapps.wallet.data.multichain.perps.PerpsOperationKind
import com.tonapps.wallet.data.multichain.perps.PerpsOperationRecord
import com.tonapps.wallet.data.multichain.perps.PerpsOperationStatus
import com.tonapps.wallet.data.multichain.perps.PerpsStepKind
import com.tonapps.wallet.data.multichain.perps.PerpsStepRecord
import com.tonapps.wallet.data.multichain.perps.PerpsStepStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import com.tonapps.chainkit.perpetual.PerpsPositionState as PerpsContextPosition
import com.tonapps.chainkit.perpetual.PerpsStepKind as PerpsPlanStepKind

sealed interface PerpsSubmitStart {
    data class Started(val operationId: String) : PerpsSubmitStart
    data class Requote(val kind: PerpsTradeError) : PerpsSubmitStart
    data class Blocked(val operationId: String) : PerpsSubmitStart
    data class Failed(val failure: PerpsTradeFailure) : PerpsSubmitStart
}

sealed interface PerpsTradeFailure {
    data class NonceUnavailable(val error: PerpsError) : PerpsTradeFailure
    data class SendRefused(val reason: PerpsSendReason, val code: String) : PerpsTradeFailure
    data class SendFailed(val error: PerpsError) : PerpsTradeFailure
    data class OrderRejected(val status: String) : PerpsTradeFailure
    data class PlannerRejected(
        val kind: PerpsTradeError,
        val field: String?,
        val suggestion: Long?,
    ) : PerpsTradeFailure

    data object OperationInProgress : PerpsTradeFailure
    data object StaleInput : PerpsTradeFailure
    data object Interrupted : PerpsTradeFailure
    data object Unresolved : PerpsTradeFailure
}

class PerpsTradeExecutor(
    private val repository: PerpsRepository,
    private val journal: PerpsOperationJournal,
    private val accountRefresh: PerpsAccountRefresh,
    private val adapter: PerpsPlannerAdapter,
    perpetualRepository: PerpetualRepository,
    private val scope: CoroutineScope,
) {

    private val relay = PerpsExecutionRelay(repository, journal, PerpsNonceCoordinator())

    private val approval = PerpsIntegratorApproval(perpetualRepository, relay)

    private val venue = PerpsVenueWitness(repository)

    private val walletLocks = ConcurrentHashMap<String, Mutex>()

    private val settling = ConcurrentHashMap.newKeySet<String>()

    private val running = PerpsRunningOperations()

    private val reconcilers = ConcurrentHashMap<String, Job>()

    private val failedOperations = MutableStateFlow<Set<String>>(emptySet())

    val unnotifiedFailures: StateFlow<Set<String>> = failedOperations

    private val announcedUnknowns = ConcurrentHashMap.newKeySet<String>()

    private val unknownOperations = MutableStateFlow<Set<String>>(emptySet())

    val unnotifiedUnknowns: StateFlow<Set<String>> = unknownOperations

    fun observe(walletId: String): Flow<List<PerpsOperationRecord>> = journal.observeOperations(walletId)

    fun acknowledgeFailure(operationId: String) {
        failedOperations.update { it - operationId }
        unknownOperations.update { it - operationId }
    }

    suspend fun start(planned: PerpsPlannedTrade): PerpsSubmitStart = withContext(Async.Io) {
        walletLock(planned.request.walletId).withLock { gate(planned) }
    }

    suspend fun recover(walletId: String, resume: Boolean): Boolean {
        return withContext(Async.Io) {
            walletLock(walletId).withLock { recoverOperations(walletId, resume) }
        }
    }

    fun reconcile(walletId: String) {
        reconcilers.compute(walletId) { _, existing ->
            if (existing?.isActive == true) {
                existing
            } else {
                scope.launch { reconcileLoop(walletId) }
            }
        }
    }

    private suspend fun reconcileLoop(walletId: String) {
        while (true) {
            val unresolved = try {
                recover(walletId, resume = true)
            } catch (e: Throwable) {
                if (e is CancellationException) {
                    throw e
                }
                L.e(e)
                return
            }
            if (!unresolved) {
                return
            }
            delay(RETRY_DELAY_MS)
        }
    }

    private suspend fun gate(planned: PerpsPlannedTrade): PerpsSubmitStart {
        val request = planned.request
        recoverOperations(request.walletId, resume = false)

        val now = System.currentTimeMillis()
        val steps = journal.getSteps(request.walletId).groupBy { it.operationId }
        val blocking = journal.getOpenOperations(request.walletId).firstOrNull { operation ->
            val unfinished = operation.operationId in running ||
                steps[operation.operationId].orEmpty().any { it.blocksPositionOrders(now) }

            operation.marketIndex == request.marketIndex && unfinished
        }
        if (blocking != null) {
            L.d("Perps open blocked by ${blocking.operationId} (${blocking.status.value})")
            return PerpsSubmitStart.Blocked(blocking.operationId)
        }

        val step = planned.plan.steps.firstOrNull()
            ?: return PerpsSubmitStart.Failed(PerpsTradeFailure.Interrupted)
        when (
            val verdict = adapter.mediator.validateForSigning(
                plan = planned.plan,
                stepId = step.stepId,
                currentContext = validationContext(
                    PerpsMarketState(rules = planned.rules, context = planned.context),
                    emptyList(),
                ),
                nowUnixMs = System.currentTimeMillis(),
            )
        ) {
            is PerpetualValidation.Approved,
            is PerpetualValidation.AlreadySettled -> Unit

            is PerpetualValidation.RequiresFreshContext -> return PerpsSubmitStart.Requote(verdict.reason)
            is PerpetualValidation.RequiresPrepare -> return PerpsSubmitStart.Requote(verdict.reason)
            is PerpetualValidation.RequiresAppliedStep -> return PerpsSubmitStart.Failed(
                PerpsTradeFailure.OperationInProgress,
            )
        }

        withContext(NonCancellable) { approval.ensure(request.walletId, planned.plan.scope, planned.integrator) }
            ?.let { return PerpsSubmitStart.Failed(it) }

        val operationId = planned.plan.operationId
        val createdAt = System.currentTimeMillis()

        // Cancelling the caller between the journal write and the launch would strand the operation.
        return withContext(NonCancellable) {
            val inserted = try {
                journal.insertOperationWithSteps(
                    operation = operationRecord(planned, operationId, createdAt),
                    steps = planned.plan.steps.map { planStep ->
                        stepRecord(planned, operationId, planStep, createdAt)
                    },
                )
            } catch (e: Throwable) {
                if (e is CancellationException) {
                    throw e
                }
                L.e(e)
                return@withContext PerpsSubmitStart.Failed(PerpsTradeFailure.Interrupted)
            }
            if (!inserted) {
                return@withContext PerpsSubmitStart.Blocked(operationId)
            }
            running.start(operationId, request.kind)
            scope.launch {
                try {
                    run(planned, operationId)
                } finally {
                    running.finish(operationId)
                }
            }

            PerpsSubmitStart.Started(operationId)
        }
    }

    private fun PerpsStepRecord.blocksPositionOrders(now: Long): Boolean {
        if (kind !in BLOCKING_KINDS) {
            return false
        }

        val expiry = txExpiry ?: return false

        return status in BLOCKING_STATUSES && now <= expiry + SETTLEMENT_GRACE_MS
    }

    private suspend fun run(planned: PerpsPlannedTrade, operationId: String) {
        val executed = walletLock(planned.request.walletId).withLock {
            execute(planned, operationId)
        } ?: return

        for (step in executed.plan.steps) {
            val outcome = settle(executed, operationId, step)
            applyOutcome(executed.request.walletId, operationId, step.stepId, outcome)
            if (outcome != StepOutcome.Accepted && outcome != StepOutcome.PartiallyFilled) {
                return
            }
        }
    }

    private suspend fun execute(planned: PerpsPlannedTrade, operationId: String): PerpsPlannedTrade? {
        var current = planned
        var state = PerpsMarketState(rules = planned.rules, context = planned.context)
        val applied = mutableListOf<PerpsAppliedPrerequisite>()
        val completed = mutableSetOf<String>()
        var freshReads = 0
        var iterations = 0
        var integratorApprovalRenewed = false

        while (true) {
            val step = current.plan.steps.firstOrNull { it.stepId !in completed } ?: return current
            iterations++
            if (iterations > ITERATION_LIMIT) {
                fail(planned.request.walletId, operationId, step.stepId, PerpsTradeFailure.OperationInProgress)
                return null
            }
            val currentContext = validationContext(state, applied)
            val verdict = adapter.mediator.validateForSigning(
                plan = current.plan,
                stepId = step.stepId,
                currentContext = currentContext,
                nowUnixMs = System.currentTimeMillis(),
            )
            when (verdict) {
                is PerpetualValidation.Approved -> {
                    val outcome = executeStep(
                        planned = current,
                        operationId = operationId,
                        step = step,
                        currentContext = currentContext,
                        approval = verdict.approval,
                    )
                    when (outcome) {
                        StepOutcome.Accepted, StepOutcome.PartiallyFilled -> {
                            applied += appliedPrerequisite(current, step)
                            completed += step.stepId
                        }

                        StepOutcome.Unknown -> {
                            holdOperationUnknown(operationId, step.stepId)
                            reconcile(planned.request.walletId)
                            return null
                        }

                        is StepOutcome.Failed -> {
                            val failure = outcome.failure
                            if (!integratorApprovalRenewed && failure is PerpsTradeFailure.SendRefused &&
                                failure.reason == PerpsSendReason.INTEGRATOR_NOT_APPROVED
                            ) {
                                integratorApprovalRenewed = true
                                L.d("Perps operation $operationId: integrator not approved, renewing the approval")
                                val renewal = withContext(NonCancellable) {
                                    approval.renew(planned.request.walletId, current.plan.scope, current.integrator)
                                }
                                if (renewal == null) {
                                    continue
                                }
                                fail(planned.request.walletId, operationId, step.stepId, renewal)
                                return null
                            }
                            fail(planned.request.walletId, operationId, step.stepId, failure)
                            return null
                        }
                    }
                }

                is PerpetualValidation.AlreadySettled -> completed += step.stepId

                is PerpetualValidation.RequiresFreshContext -> {
                    if (freshReads >= FRESH_READ_LIMIT) {
                        fail(planned.request.walletId, operationId, step.stepId, PerpsTradeFailure.OperationInProgress)
                        return null
                    }
                    freshReads++
                    delay(REFRESH_DELAY_MS)
                    val refreshed = adapter.refreshMarketState(current)
                    if (refreshed is KResult.Ok) {
                        state = refreshed.value
                    }
                }

                is PerpetualValidation.RequiresPrepare -> {
                    val replanned = replan(current, operationId, applied, verdict)
                    if (replanned == null) {
                        fail(planned.request.walletId, operationId, step.stepId, PerpsTradeFailure.StaleInput)
                        return null
                    }
                    current = replanned
                    state = PerpsMarketState(rules = replanned.rules, context = replanned.context)
                }

                is PerpetualValidation.RequiresAppliedStep -> {
                    fail(planned.request.walletId, operationId, step.stepId, PerpsTradeFailure.OperationInProgress)
                    return null
                }
            }
        }
    }

    private suspend fun replan(
        planned: PerpsPlannedTrade,
        operationId: String,
        applied: List<PerpsAppliedPrerequisite>,
        verdict: PerpetualValidation.RequiresPrepare,
    ): PerpsPlannedTrade? {
        if (applied.isNotEmpty() || verdict.reason !in REPLAN_REASONS) {
            L.e("Perps operation $operationId cannot be re-planned: ${verdict.message}")
            return null
        }
        val replanned = when (
            val result = adapter.plan(
                request = planned.request,
                credentials = planned.credentials,
                mark = null,
                operationId = operationId,
                previous = planned.intent.takeIf { it is PerpsTradeIntent.Open },
            )
        ) {
            is KResult.Ok -> result.value
            is KResult.Err -> {
                L.e("Perps operation $operationId re-plan failed: ${result.value}")
                return null
            }
        }
        val updatedAt = System.currentTimeMillis()
        val written = write {
            journal.replacePlan(
                operationId = operationId,
                planDigest = replanned.plan.planDigest,
                expectedBaseSize = expectedBaseSize(replanned)?.toPlainString(),
                steps = replanned.plan.steps.map { stepRecord(replanned, operationId, it, updatedAt) },
                updatedAt = updatedAt,
            )
        }
        if (!written) {
            return null
        }

        return replanned
    }

    private suspend fun executeStep(
        planned: PerpsPlannedTrade,
        operationId: String,
        step: PerpetualStep,
        currentContext: PerpsSigningValidationContext,
        approval: PerpetualApproval,
    ): StepOutcome {
        val nowMs = System.currentTimeMillis()
        try {
            adapter.mediator.executeStep(
                plan = planned.plan,
                stepId = step.stepId,
                currentContext = currentContext,
                approval = approval,
                signingKey = planned.credentials.key,
                execution = relay,
                attemptId = UUID.randomUUID().toString(),
                nowUnixMs = nowMs,
                transactionExpiryUnixMs = minOf(
                    nowMs + LighterConstants.DefaultExpireTimeMillis,
                    LighterConstants.MaxTimestamp,
                ),
            )
        } catch (e: PerpsExecutionException) {
            L.e(e)
            val failure = e.failure()
            if (!e.kind.isAmbiguousSubmit()) {
                return StepOutcome.Failed(failure)
            }

            return stepOutcome(planned.request.walletId, operationId, step.stepId, failure)
        } catch (e: PerpsTradeException) {
            L.e(e)
            return when (e.kind) {
                PerpsTradeError.ProtocolFailure -> StepOutcome.Unknown

                PerpsTradeError.InvalidNonce -> StepOutcome.Failed(
                    PerpsTradeFailure.SendRefused(PerpsSendReason.RESIGN_REQUIRED, e.message.orEmpty()),
                )

                else -> StepOutcome.Failed(
                    PerpsTradeFailure.PlannerRejected(e.kind, e.field, e.suggestion),
                )
            }
        } catch (e: Throwable) {
            if (e is CancellationException) {
                throw e
            }
            L.e(e)
            return stepOutcome(
                planned.request.walletId,
                operationId,
                step.stepId,
                PerpsTradeFailure.Interrupted,
            )
        }

        return StepOutcome.Accepted
    }

    private suspend fun stepOutcome(
        walletId: String,
        operationId: String,
        stepId: String,
        failure: PerpsTradeFailure,
    ): StepOutcome {
        val status = journal.getSteps(walletId)
            .firstOrNull { it.operationId == operationId && it.stepId == stepId }
            ?.status
        if (status == PerpsStepStatus.SENDING || status == PerpsStepStatus.SENT ||
            status == PerpsStepStatus.UNKNOWN
        ) {
            return StepOutcome.Unknown
        }

        return StepOutcome.Failed(failure)
    }

    private suspend fun settle(
        planned: PerpsPlannedTrade,
        operationId: String,
        step: PerpetualStep,
    ): StepOutcome {
        val walletId = planned.request.walletId
        val marketIndex = planned.request.marketIndex
        val submittedAt = System.currentTimeMillis()
        val deadline = submittedAt + POLL_CEILING_MS
        val intent = planned.intent

        return when (step.kind) {
            PerpsPlanStepKind.Open,
            PerpsPlanStepKind.Add,
            PerpsPlanStepKind.Close,
            PerpsPlanStepKind.OrderCancel,
            PerpsPlanStepKind.OrderModify -> {
                val record = stepRecord(planned, operationId, step, submittedAt)
                if (record.clientOrderIndex <= LighterConstants.NilClientOrderIndex) {
                    return StepOutcome.Accepted
                }

                settleRecord(walletId, operationRecord(planned, operationId, submittedAt), record, deadline)
            }

            PerpsPlanStepKind.AutoClose -> pollLegs(
                walletId = walletId,
                marketIndex = marketIndex,
                operationId = operationId,
                stepId = step.stepId,
                indexes = intent.legIndexes(),
                submittedAtMs = submittedAt,
                deadline = deadline,
            )

            PerpsPlanStepKind.Margin -> {
                val margin = intent as? PerpsTradeIntent.Margin ?: return StepOutcome.Accepted
                val present = planned.context.position as? PerpsContextPosition.Present
                    ?: return StepOutcome.Accepted
                val quoteDecimals = planned.plan.review.scale.quoteDecimals
                val txExpiry = journal.getSteps(walletId)
                    .firstOrNull { it.operationId == operationId && it.stepId == step.stepId }
                    ?.txExpiry

                pollMargin(
                    walletId = walletId,
                    marketIndex = marketIndex,
                    operationId = operationId,
                    stepId = step.stepId,
                    before = BigDecimal.valueOf(present.allocatedMarginQuote, quoteDecimals),
                    amount = BigDecimal.valueOf(margin.amountQuote, quoteDecimals),
                    add = margin.addToPosition,
                    txExpiry = txExpiry,
                    submittedAtMs = submittedAt,
                    deadline = deadline,
                )
            }

            else -> StepOutcome.Accepted
        }
    }

    private suspend fun settleRecord(
        walletId: String,
        operation: PerpsOperationRecord,
        step: PerpsStepRecord,
        deadline: Long,
    ): StepOutcome {
        return when (step.kind) {
            PerpsStepKind.AUTO_CLOSE -> pollLegs(
                walletId = walletId,
                marketIndex = operation.marketIndex,
                operationId = operation.operationId,
                stepId = step.stepId,
                indexes = step.legIndexes(),
                submittedAtMs = step.submittedAt,
                deadline = deadline,
            )

            PerpsStepKind.ORDER_CANCEL -> {
                val witness = venue.of(operation, step) ?: return StepOutcome.Unknown

                pollCancel(
                    operationId = operation.operationId,
                    stepId = step.stepId,
                    orderIndex = step.clientOrderIndex,
                    witness = if (operation.kind == PerpsOperationKind.ORDER_CANCEL) {
                        { venue.cancelOutcome(operation, step)?.outcome() }
                    } else {
                        { StepOutcome.Accepted.takeIf { witness() == true } }
                    },
                    submittedAtMs = step.submittedAt,
                    deadline = deadline,
                )
            }

            PerpsStepKind.ORDER_MODIFY -> {
                val price = operation.marginQuote.toBigDecimalOrNull() ?: return StepOutcome.Unknown

                pollCancel(
                    operationId = operation.operationId,
                    stepId = step.stepId,
                    orderIndex = step.clientOrderIndex,
                    witness = {
                        when (venue.limitOnVenue(walletId, operation.marketIndex, step.clientOrderIndex, price)) {
                            PerpsLimitOnVenue.REPRICED -> StepOutcome.Accepted
                            PerpsLimitOnVenue.GONE -> StepOutcome.Failed(PerpsTradeFailure.OrderRejected(ORDER_GONE_STATUS))
                            PerpsLimitOnVenue.RESTING, null -> null
                        }
                    },
                    submittedAtMs = step.submittedAt,
                    deadline = deadline,
                )
            }

            PerpsStepKind.MARGIN -> pollMargin(
                walletId = walletId,
                marketIndex = operation.marketIndex,
                operationId = operation.operationId,
                stepId = step.stepId,
                before = operation.marginBefore?.toBigDecimalOrNull() ?: return StepOutcome.Accepted,
                amount = operation.marginQuote.toBigDecimalOrNull() ?: return StepOutcome.Accepted,
                add = operation.kind == PerpsOperationKind.MARGIN_ADD,
                txExpiry = step.txExpiry,
                submittedAtMs = step.submittedAt,
                deadline = deadline,
            )

            PerpsStepKind.OPEN,
            PerpsStepKind.ADD,
            PerpsStepKind.CLOSE -> poll(
                walletId = walletId,
                marketIndex = operation.marketIndex,
                operationId = operation.operationId,
                stepId = step.stepId,
                clientOrderIndex = step.clientOrderIndex,
                expectedBase = operation.expectedBase().takeIf { operation.kind != PerpsOperationKind.OPEN_LIMIT },
                acceptWorking = operation.kind == PerpsOperationKind.OPEN_LIMIT,
                fullExit = step.kind == PerpsStepKind.CLOSE &&
                    operation.kind == PerpsOperationKind.CLOSE,
                witness = venue.of(operation, step),
                submittedAtMs = step.submittedAt,
                deadline = deadline,
            )

            else -> StepOutcome.Accepted
        }
    }

    private suspend fun poll(
        walletId: String,
        marketIndex: Int,
        operationId: String,
        stepId: String,
        clientOrderIndex: Long,
        expectedBase: BigDecimal?,
        fullExit: Boolean,
        witness: (suspend () -> Boolean?)?,
        submittedAtMs: Long,
        deadline: Long,
        acceptWorking: Boolean,
    ): StepOutcome {
        var held = false
        var venueCheckedAt = 0L
        var closingEpisodeId: String? = null
        while (true) {
            val state = repository.positionState(
                walletId = walletId,
                marketIndex = marketIndex,
                clientOrderIndex = clientOrderIndex,
            )
            if (state is KResult.Ok) {
                closingEpisodeId = state.value.open?.episodeId?.takeIf { fullExit } ?: closingEpisodeId
                val outcome = state.value.classify(submittedAtMs, expectedBase, closingEpisodeId, acceptWorking)
                if (outcome != null) {
                    return outcome
                }
            }
            val now = System.currentTimeMillis()
            if (now >= deadline) {
                return StepOutcome.Unknown
            }
            // `/state` is silent for a whole class of accounts, so the venue is asked in parallel
            // rather than once the hold has already made the user wait a minute.
            if (witness != null && now - submittedAtMs >= VENUE_GRACE_MS &&
                now - venueCheckedAt >= VENUE_CHECK_INTERVAL_MS
            ) {
                venueCheckedAt = now
                if (witness() == true) {
                    return StepOutcome.Accepted
                }
            }
            if (!held && now - submittedAtMs >= UNKNOWN_HOLD_MS) {
                held = true
                holdOperationUnknown(operationId, stepId)
            }
            delay(POLL_INTERVAL_MS)
        }
    }

    private suspend fun pollLegs(
        walletId: String,
        marketIndex: Int,
        operationId: String,
        stepId: String,
        indexes: List<Long>,
        submittedAtMs: Long,
        deadline: Long,
    ): StepOutcome {
        val pending = indexes.filter { it > LighterConstants.NilClientOrderIndex }.toMutableSet()
        var held = false
        var venueCheckedAt = 0L
        while (pending.isNotEmpty()) {
            for (index in pending.toList()) {
                val state = repository.positionState(
                    walletId = walletId,
                    marketIndex = marketIndex,
                    clientOrderIndex = index,
                )
                if (state !is KResult.Ok) {
                    continue
                }
                val dead = state.value.orders.firstOrNull { it.status.isDeadOrderStatus() }
                if (dead != null) {
                    return StepOutcome.Failed(PerpsTradeFailure.OrderRejected(dead.status))
                }
                if (state.value.orders.isNotEmpty()) {
                    pending.remove(index)
                }
            }
            if (pending.isEmpty()) {
                break
            }
            val now = System.currentTimeMillis()
            if (now >= deadline) {
                return StepOutcome.Unknown
            }
            if (now - submittedAtMs >= VENUE_GRACE_MS && now - venueCheckedAt >= VENUE_CHECK_INTERVAL_MS) {
                venueCheckedAt = now
                pending.removeAll(venue.legsOnVenue(walletId, marketIndex).orEmpty())
                if (pending.isEmpty()) {
                    break
                }
            }
            if (!held && now - submittedAtMs >= UNKNOWN_HOLD_MS) {
                held = true
                holdOperationUnknown(operationId, stepId)
            }
            delay(POLL_INTERVAL_MS)
        }

        return StepOutcome.Accepted
    }

    private suspend fun pollCancel(
        operationId: String,
        stepId: String,
        orderIndex: Long,
        witness: suspend () -> StepOutcome?,
        submittedAtMs: Long,
        deadline: Long,
    ): StepOutcome {
        if (orderIndex <= LighterConstants.NilClientOrderIndex) {
            return StepOutcome.Accepted
        }
        var held = false
        while (true) {
            witness()?.let { return it }
            val now = System.currentTimeMillis()
            if (now >= deadline) {
                return StepOutcome.Unknown
            }
            if (!held && now - submittedAtMs >= UNKNOWN_HOLD_MS) {
                held = true
                holdOperationUnknown(operationId, stepId)
            }
            delay(POLL_INTERVAL_MS)
        }
    }

    private suspend fun pollMargin(
        walletId: String,
        marketIndex: Int,
        operationId: String,
        stepId: String,
        before: BigDecimal,
        amount: BigDecimal,
        add: Boolean,
        txExpiry: Long?,
        submittedAtMs: Long,
        deadline: Long,
    ): StepOutcome {
        val tolerance = amount.abs().multiply(MARGIN_TOLERANCE_RATIO).max(MIN_MARGIN_TOLERANCE)
        var held = false
        while (true) {
            val position = repository.getOpenPosition(marketIndex, walletId)
            val now = System.currentTimeMillis()
            if (position is KResult.Ok) {
                val detail = position.value
                    ?: return StepOutcome.Failed(PerpsTradeFailure.OrderRejected(POSITION_GONE))
                val margin = detail.position.margin
                if (margin != null) {
                    val moved = if (add) {
                        margin.subtract(before)
                    } else {
                        before.subtract(margin)
                    }
                    if (moved.add(tolerance) >= amount) {
                        return StepOutcome.Accepted
                    }
                    if (txExpiry != null && now > txExpiry + MARGIN_SETTLE_GRACE_MS) {
                        return StepOutcome.Failed(PerpsTradeFailure.OrderRejected(MARGIN_UNCHANGED))
                    }
                }
            }
            if (now >= deadline) {
                return StepOutcome.Unknown
            }
            if (!held && now - submittedAtMs >= UNKNOWN_HOLD_MS) {
                held = true
                holdOperationUnknown(operationId, stepId)
            }
            delay(POLL_INTERVAL_MS)
        }
    }

    private suspend fun recoverOperations(walletId: String, resume: Boolean): Boolean {
        val operations = journal.getOperations(walletId).filter {
            !running.contains(it.operationId) && !isSettling(it.operationId) &&
                (it.status == PerpsOperationStatus.IN_PROGRESS || it.status == PerpsOperationStatus.UNKNOWN)
        }
        if (operations.isNotEmpty()) {
            L.d("Perps recover $walletId: ${operations.size} open operation(s), resume=$resume")

            val stepsByOperation = journal.getSteps(walletId).groupBy { it.operationId }
            for (operation in operations) {
                val steps = stepsByOperation[operation.operationId].orEmpty()
                if (steps.any { it.status == PerpsStepStatus.DEAD }) {
                    announcedUnknowns.add(operation.operationId)
                }
                val unresolved = steps.filterNot { it.status.isTerminal }
                if (unresolved.isEmpty()) {
                    closeIfSettled(walletId, operation.operationId)
                } else {
                    recoverOperation(walletId, operation, unresolved, resume)
                }
            }
        }

        return journal.getOpenOperations(walletId).isNotEmpty()
    }

    private suspend fun recoverOperation(
        walletId: String,
        operation: PerpsOperationRecord,
        steps: List<PerpsStepRecord>,
        resume: Boolean,
    ) {
        var failure: PerpsTradeFailure? = null
        for (step in steps) {
            when (val outcome = recoverStep(walletId, operation, step, resume)) {
                StepOutcome.Accepted -> acceptStep(operation.operationId, step.stepId)
                StepOutcome.PartiallyFilled -> acceptStep(operation.operationId, step.stepId, partial = true)
                StepOutcome.Unknown -> markStepUnknown(operation.operationId, step.stepId)
                is StepOutcome.Failed -> {
                    markStepDead(operation.operationId, step.stepId, outcome.failure)
                    failure = failure ?: outcome.failure
                }

                null -> Unit
            }
        }
        closeOperation(walletId, operation.operationId, failure)
    }

    private suspend fun recoverStep(
        walletId: String,
        operation: PerpsOperationRecord,
        step: PerpsStepRecord,
        resume: Boolean,
    ): StepOutcome? {
        if (step.status == PerpsStepStatus.PLANNED) {
            return StepOutcome.Failed(PerpsTradeFailure.Interrupted)
        }
        val status = when (step.status) {
            // SENDING is the crash window: the request may have reached the venue, so it is reconciled.
            PerpsStepStatus.SENDING -> {
                markStepUnknown(operation.operationId, step.stepId)
                PerpsStepStatus.UNKNOWN
            }

            PerpsStepStatus.SIGNED -> resend(walletId, step)
            else -> step.status
        }
        // A step that carries no order of its own (leverage) settles on the relay receipt alone.
        if (step.kind != PerpsStepKind.MARGIN && step.clientOrderIndex <= LighterConstants.NilClientOrderIndex) {
            if (status == PerpsStepStatus.SENT) {
                return StepOutcome.Accepted
            }

            return StepOutcome.Failed(PerpsTradeFailure.Interrupted)
        }
        if (resume && status == PerpsStepStatus.SENT && !step.unresolvable()) {
            resumeSettle(walletId, operation, step)
            return null
        }

        val outcome = settleRecord(
            walletId = walletId,
            operation = operation,
            step = step,
            deadline = System.currentTimeMillis(),
        )
        if (outcome == StepOutcome.Unknown) {
            val seen = venue.of(operation, step)?.invoke()
            if (seen == true) {
                return StepOutcome.Accepted
            }
            if (seen == false && step.unresolvable() && venue.movedOnVenue(operation, step) == true) {
                return StepOutcome.PartiallyFilled
            }

            return giveUp(step, venueAnswered = seen == false)
        }

        return outcome
    }

    private suspend fun resend(walletId: String, step: PerpsStepRecord): PerpsStepStatus {
        val txType = step.txType
        val txInfo = step.txInfo?.takeIf { it.isNotBlank() }
        val txHash = step.txHash?.takeIf { it.isNotBlank() }
        if (txType != null && txInfo != null && txHash != null) {
            // Posting without the SENDING mark in place would re-post the step after a crash.
            val marked = write {
                journal.updateStepStatus(
                    operationId = step.operationId,
                    stepId = step.stepId,
                    status = PerpsStepStatus.SENDING,
                    updatedAt = System.currentTimeMillis(),
                )
            }
            if (marked) {
                val outcome = try {
                    relay.submitTransaction(walletId, txType, txInfo, txHash)
                } catch (e: Throwable) {
                    if (e is CancellationException) {
                        throw e
                    }
                    L.e(e)
                    null
                }
                if (outcome == PerpsSubmissionOutcome.Accepted) {
                    write {
                        journal.updateStepStatus(
                            operationId = step.operationId,
                            stepId = step.stepId,
                            status = PerpsStepStatus.SENT,
                            updatedAt = System.currentTimeMillis(),
                        )
                    }
                    return PerpsStepStatus.SENT
                }
            }
        }
        markStepUnknown(step.operationId, step.stepId)

        return PerpsStepStatus.UNKNOWN
    }

    private fun isSettling(operationId: String): Boolean {
        val prefix = "$operationId/"

        return settling.any { it.startsWith(prefix) }
    }

    private fun resumeSettle(
        walletId: String,
        operation: PerpsOperationRecord,
        step: PerpsStepRecord,
    ) {
        val key = "${operation.operationId}/${step.stepId}"
        if (!settling.add(key)) {
            return
        }
        scope.launch {
            try {
                val outcome = settleRecord(
                    walletId = walletId,
                    operation = operation,
                    step = step,
                    deadline = System.currentTimeMillis() + POLL_CEILING_MS,
                )
                applyOutcome(walletId, operation.operationId, step.stepId, outcome)
            } finally {
                settling.remove(key)
            }
        }
    }

    private suspend fun applyOutcome(
        walletId: String,
        operationId: String,
        stepId: String,
        outcome: StepOutcome,
    ) {
        if (outcome == StepOutcome.Accepted || outcome == StepOutcome.PartiallyFilled) {
            acceptStep(operationId, stepId, partial = outcome == StepOutcome.PartiallyFilled)
            closeIfSettled(walletId, operationId)
            return
        }
        if (outcome is StepOutcome.Failed) {
            fail(walletId, operationId, stepId, outcome.failure)
            return
        }

        holdOperationUnknown(operationId, stepId)
        reconcile(walletId)
    }

    private suspend fun closeIfSettled(walletId: String, operationId: String) {
        val steps = journal.getSteps(walletId).filter { it.operationId == operationId }
        if (steps.any { !it.status.isTerminal }) {
            return
        }
        val accepted = steps.isNotEmpty() && steps.all { it.status == PerpsStepStatus.ACCEPTED }
        if (accepted) {
            val status = PerpsOperationStatus.PARTIALLY_FILLED.takeIf { steps.any { it.error == PARTIAL_FILL } }
                ?: PerpsOperationStatus.SUCCEEDED
            write {
                journal.updateOperation(
                    operationId = operationId,
                    status = status,
                    error = null,
                    updatedAt = System.currentTimeMillis(),
                )
            }
            accountRefresh.notifyChanged()
            L.d("Perps operation $operationId settled: ${status.value}")
        } else {
            failOperation(
                operationId,
                steps.firstOrNull { it.status == PerpsStepStatus.DEAD }?.error ?: PerpsTradeFailure.Interrupted.tag(),
            )
        }
    }

    private suspend fun acceptStep(operationId: String, stepId: String, partial: Boolean = false) {
        write {
            journal.updateStepStatus(
                operationId = operationId,
                stepId = stepId,
                status = PerpsStepStatus.ACCEPTED,
                error = PARTIAL_FILL.takeIf { partial },
                updatedAt = System.currentTimeMillis(),
            )
        }
    }

    private suspend fun markStepUnknown(operationId: String, stepId: String) {
        write {
            journal.updateStepStatus(
                operationId = operationId,
                stepId = stepId,
                status = PerpsStepStatus.UNKNOWN,
                updatedAt = System.currentTimeMillis(),
            )
        }
    }

    private suspend fun markStepDead(
        operationId: String,
        stepId: String,
        failure: PerpsTradeFailure,
    ) {
        val error = failure.tag()
        write {
            journal.updateStepStatus(
                operationId = operationId,
                stepId = stepId,
                status = PerpsStepStatus.DEAD,
                error = error,
                updatedAt = System.currentTimeMillis(),
            )
        }
        announcedUnknowns.add(operationId)
        unknownOperations.update { it - operationId }
        L.e("Perps step $operationId/$stepId failed: $error")
    }

    private suspend fun closeOperation(
        walletId: String,
        operationId: String,
        failure: PerpsTradeFailure?,
    ) {
        val steps = journal.getSteps(walletId).filter { it.operationId == operationId }
        if (steps.none { !it.status.isTerminal }) {
            closeIfSettled(walletId, operationId)
            return
        }
        if (failure != null || steps.any { it.status == PerpsStepStatus.UNKNOWN }) {
            holdOperationUnknown(operationId)
        }
    }

    private suspend fun holdOperationUnknown(operationId: String, stepId: String) {
        write {
            journal.resolveStep(
                operationId = operationId,
                stepId = stepId,
                stepStatus = PerpsStepStatus.UNKNOWN,
                operationStatus = PerpsOperationStatus.UNKNOWN,
                updatedAt = System.currentTimeMillis(),
            )
        }
        if (announcedUnknowns.add(operationId)) {
            unknownOperations.update { it + operationId }
        }
    }

    private suspend fun fail(
        walletId: String,
        operationId: String,
        stepId: String,
        failure: PerpsTradeFailure,
    ) {
        val inFlight = journal.getSteps(walletId).any {
            it.operationId == operationId && it.stepId != stepId && it.status.isInFlight
        }
        if (inFlight) {
            markStepDead(operationId, stepId, failure)
            holdOperationUnknown(operationId)
            reconcile(walletId)
            return
        }
        val error = failure.tag()
        write {
            journal.resolveStep(
                operationId = operationId,
                stepId = stepId,
                stepStatus = PerpsStepStatus.DEAD,
                operationStatus = PerpsOperationStatus.FAILED,
                error = error,
                updatedAt = System.currentTimeMillis(),
            )
        }
        markFailed(operationId, error)
    }

    private suspend fun failOperation(operationId: String, error: String) {
        write {
            journal.updateOperation(
                operationId = operationId,
                status = PerpsOperationStatus.FAILED,
                error = error,
                updatedAt = System.currentTimeMillis(),
            )
        }
        markFailed(operationId, error)
    }

    // A failed operation can still have moved the venue: a partial fill leaves a real position.
    private fun markFailed(operationId: String, error: String) {
        running.reportFailure(operationId, error)
        failedOperations.update { it + operationId }
        accountRefresh.notifyChanged()
        L.e("Perps operation $operationId failed: $error")
    }

    private suspend fun holdOperationUnknown(operationId: String) {
        write {
            journal.updateOperation(
                operationId = operationId,
                status = PerpsOperationStatus.UNKNOWN,
                error = null,
                updatedAt = System.currentTimeMillis(),
            )
        }
        if (announcedUnknowns.add(operationId)) {
            unknownOperations.update { it + operationId }
        }
    }

    private fun appliedPrerequisite(
        planned: PerpsPlannedTrade,
        step: PerpetualStep,
    ): PerpsAppliedPrerequisite {
        if (step.kind == PerpsPlanStepKind.Leverage) {
            val intent = planned.intent

            return PerpsAppliedPrerequisite.LeverageApplied(
                operationId = planned.plan.operationId,
                scope = planned.plan.scope,
                initialMarginBps = (intent as? PerpsTradeIntent.Open)?.initialMarginBps
                    ?: (intent as? PerpsTradeIntent.Add)?.initialMarginBps
                    ?: 0,
                marginMode = LighterConstants.IsolatedMargin,
            )
        }

        return PerpsAppliedPrerequisite.StepApplied(
            operationId = planned.plan.operationId,
            scope = planned.plan.scope,
            stepId = step.stepId,
            planDigest = planned.plan.planDigest,
        )
    }

    private suspend fun write(block: suspend () -> Unit): Boolean {
        return try {
            block()
            true
        } catch (e: Throwable) {
            if (e is CancellationException) {
                throw e
            }
            L.e(e)
            false
        }
    }

    private fun walletLock(walletId: String): Mutex {
        return walletLocks.getOrPut(walletId) { Mutex() }
    }

    private companion object {
        const val POLL_INTERVAL_MS = 1_000L
        const val POLL_CEILING_MS = 300_000L
        const val VENUE_GRACE_MS = 3_000L
        const val VENUE_CHECK_INTERVAL_MS = 3_000L
        const val UNKNOWN_HOLD_MS = 60_000L
        const val ITERATION_LIMIT = 32
        const val FRESH_READ_LIMIT = 10
        const val REFRESH_DELAY_MS = 1_000L
        const val RETRY_DELAY_MS = 5_000L

        val BLOCKING_KINDS = setOf(
            PerpsStepKind.OPEN,
            PerpsStepKind.CLOSE,
            PerpsStepKind.ADD,
            PerpsStepKind.MARGIN,
            PerpsStepKind.AUTO_CLOSE,
            PerpsStepKind.ORDER_CANCEL,
        )

        val BLOCKING_STATUSES = setOf(
            PerpsStepStatus.SIGNED,
            PerpsStepStatus.SENDING,
            PerpsStepStatus.SENT,
            PerpsStepStatus.UNKNOWN,
            PerpsStepStatus.ACCEPTED,
        )

        val REPLAN_REASONS = setOf(
            PerpsTradeError.StaleInput,
            PerpsTradeError.PlanExpired,
            PerpsTradeError.UnusableSnapshot,
            PerpsTradeError.InvalidRules,
        )
    }
}

private fun validationContext(
    state: PerpsMarketState,
    applied: List<PerpsAppliedPrerequisite>,
): PerpsSigningValidationContext {
    return PerpsSigningValidationContext(
        rules = state.rules,
        accountSnapshot = state.context,
        appliedPrerequisites = applied,
        freshnessPolicy = PerpsProductRules.POLICY,
    )
}

private fun operationRecord(
    planned: PerpsPlannedTrade,
    operationId: String,
    createdAt: Long,
): PerpsOperationRecord {
    val request = planned.request
    val position = planned.context.position as? PerpsContextPosition.Present
    val settings = planned.context.marginSettings as? PerpsMarginSettings.Known
    val quoteDecimals = planned.plan.review.scale.quoteDecimals
    val review = planned.plan.review
    val amount = when (request) {
        is PerpsTradeRequest.Open -> request.marginQuote
        is PerpsTradeRequest.Close -> BigDecimal.valueOf(
            (review as? PerpetualReview.Close)?.estimatedReturnedMarginQuote ?: 0L,
            quoteDecimals,
        )

        is PerpsTradeRequest.Reduce -> request.marginDelta
        is PerpsTradeRequest.Add -> request.marginDelta
        is PerpsTradeRequest.Margin -> request.amountQuote

        is PerpsTradeRequest.OrderModify -> (review as? PerpetualReview.OrderModify)
            ?.let { BigDecimal.valueOf(it.price, it.scale.priceDecimals) }
            ?: BigDecimal.ZERO

        is PerpsTradeRequest.AutoClose,
        is PerpsTradeRequest.OrderCancel -> BigDecimal.ZERO
    }

    return PerpsOperationRecord(
        operationId = operationId,
        walletId = request.walletId,
        marketIndex = request.marketIndex,
        kind = request.kind,
        side = when {
            request is PerpsTradeRequest.Open -> request.side.name
            position == null -> ""
            position.side == PerpsSide.Short -> PerpsPositionSide.SHORT.name
            else -> PerpsPositionSide.LONG.name
        },
        marginQuote = amount.toPlainString(),
        leverage = if (request is PerpsTradeRequest.Open) {
            request.leverage
        } else {
            settings?.let { PerpsProductRules.leverage(it.initialMarginBps) } ?: 0
        },
        reviewJson = null,
        planDigest = planned.plan.planDigest,
        expectedBaseSize = expectedBaseSize(planned)?.toPlainString(),
        baseBefore = position
            ?.let { BigDecimal.valueOf(it.baseAmount, review.scale.baseDecimals).abs().toPlainString() },
        marginBefore = position
            ?.let { BigDecimal.valueOf(it.allocatedMarginQuote, quoteDecimals).toPlainString() },
        status = PerpsOperationStatus.IN_PROGRESS,
        error = null,
        createdAt = createdAt,
        updatedAt = createdAt,
    )
}

private fun stepRecord(
    planned: PerpsPlannedTrade,
    operationId: String,
    step: PerpetualStep,
    createdAt: Long,
): PerpsStepRecord {
    return PerpsStepRecord(
        operationId = operationId,
        stepId = step.stepId,
        kind = step.kind.toJournalKind(),
        requiresStepId = step.requiresAppliedStepId,
        attemptId = UUID.randomUUID().toString(),
        apiKeyIndex = planned.credentials.apiKeyIndex,
        nonce = null,
        accountIndex = null,
        txType = null,
        txHash = null,
        txInfo = null,
        clientOrderIndex = clientOrderIndex(planned, step),
        planId = planned.plan.planId,
        txExpiry = null,
        orderRefs = emptyList(),
        submittedAt = createdAt,
        status = PerpsStepStatus.PLANNED,
        error = null,
        updatedAt = createdAt,
    )
}

private fun clientOrderIndex(planned: PerpsPlannedTrade, step: PerpetualStep): Long {
    val intent = planned.intent

    return when (step.kind) {
        PerpsPlanStepKind.Open -> (intent as? PerpsTradeIntent.Open)?.clientOrderIndex
        PerpsPlanStepKind.Close -> (intent as? PerpsTradeIntent.Close)?.clientOrderIndex
        PerpsPlanStepKind.Add -> (intent as? PerpsTradeIntent.Add)?.clientOrderIndex
        PerpsPlanStepKind.AutoClose -> intent.legIndexes().firstOrNull()
        PerpsPlanStepKind.OrderCancel -> (intent as? PerpsTradeIntent.OrderCancel)?.orderIndex
            ?: intent.cancelledLeg(step.stepId)

        PerpsPlanStepKind.OrderModify -> (intent as? PerpsTradeIntent.OrderModify)?.orderIndex
        else -> null
    } ?: LighterConstants.NilClientOrderIndex
}

private fun expectedBaseSize(planned: PerpsPlannedTrade): BigDecimal? {
    val review = planned.plan.review
    val baseAmount = when (review) {
        is PerpetualReview.Open -> review.baseAmount
        is PerpetualReview.Add -> review.baseAmount
        is PerpetualReview.Close -> review.closingBaseAmount
        else -> return null
    }

    return BigDecimal.valueOf(baseAmount, review.scale.baseDecimals)
}

private sealed interface StepOutcome {
    data object Accepted : StepOutcome
    data object PartiallyFilled : StepOutcome
    data object Unknown : StepOutcome
    data class Failed(val failure: PerpsTradeFailure) : StepOutcome
}

private fun PerpsCancelOnVenue.outcome(): StepOutcome? {
    return when (this) {
        PerpsCancelOnVenue.CANCELLED -> StepOutcome.Accepted
        PerpsCancelOnVenue.FILLED -> StepOutcome.Failed(PerpsTradeFailure.OrderRejected(ORDER_FILLED_STATUS))
        PerpsCancelOnVenue.RESTING -> null
    }
}

internal fun PerpsExecutionException.failure(): PerpsTradeFailure {
    return when (kind) {
        PerpsExecutionErrorKind.ServerRejected -> {
            val parts = message.orEmpty().split(":", limit = 2)
            PerpsTradeFailure.SendRefused(
                reason = PerpsSendReason.entries.firstOrNull { it.name == parts.first() }
                    ?: PerpsSendReason.OTHER,
                code = parts.getOrElse(1) { "" },
            )
        }

        PerpsExecutionErrorKind.Offline -> PerpsTradeFailure.SendFailed(PerpsError.Network)
        PerpsExecutionErrorKind.ServerUnavailable -> PerpsTradeFailure.SendFailed(
            PerpsError.ServiceUnavailable,
        )

        PerpsExecutionErrorKind.Validation -> PerpsTradeFailure.NonceUnavailable(PerpsError.Unknown)
        PerpsExecutionErrorKind.AuthExpired,
        PerpsExecutionErrorKind.CredentialsRevoked -> PerpsTradeFailure.SendFailed(PerpsError.Unauthorized)

        else -> PerpsTradeFailure.SendFailed(PerpsError.Unknown)
    }
}

private fun PerpsExecutionErrorKind.isAmbiguousSubmit(): Boolean {
    return this == PerpsExecutionErrorKind.Offline ||
        this == PerpsExecutionErrorKind.Timeout ||
        this == PerpsExecutionErrorKind.ServerUnavailable ||
        this == PerpsExecutionErrorKind.ProtocolFailure ||
        this == PerpsExecutionErrorKind.SubmitUnknown ||
        this == PerpsExecutionErrorKind.Unknown
}

private val PerpsStepStatus.isInFlight: Boolean
    get() = this == PerpsStepStatus.SENDING || this == PerpsStepStatus.SENT || this == PerpsStepStatus.UNKNOWN

internal fun PerpsOperationRecord.expectedBase(): BigDecimal? {
    return expectedBaseSize?.takeIf { it.isNotBlank() }?.toBigDecimalOrNull()
}

private fun giveUp(step: PerpsStepRecord, venueAnswered: Boolean): StepOutcome {
    if ((venueAnswered && step.unresolvable()) ||
        System.currentTimeMillis() - step.submittedAt >= UNKNOWN_GIVE_UP_MS
    ) {
        return StepOutcome.Failed(PerpsTradeFailure.Unresolved)
    }

    return StepOutcome.Unknown
}

// A signed transaction cannot be included past its expiry, so a still-missing outcome is final.
private fun PerpsStepRecord.unresolvable(): Boolean {
    val now = System.currentTimeMillis()
    val expiry = txExpiry
    if (expiry != null) {
        return now > expiry + SETTLEMENT_GRACE_MS
    }

    return now - submittedAt >= UNKNOWN_GIVE_UP_MS
}

internal fun PerpsOperationRecord.baseAfter(grew: Boolean): BigDecimal? {
    val before = baseBefore?.toBigDecimalOrNull() ?: return null
    val delta = expectedBase() ?: return null

    return if (grew) {
        before.add(delta)
    } else {
        before.subtract(delta)
    }
}

internal fun PerpsStepRecord.legIndexes(): List<Long> {
    return orderRefs.map { it.clientOrderIndex }.ifEmpty { listOf(clientOrderIndex) }
}

private fun PerpsTradeIntent.autoCloseSpec(): PerpsAutoCloseSpec? {
    return when (this) {
        is PerpsTradeIntent.Open -> autoClose
        is PerpsTradeIntent.Close -> autoClose
        is PerpsTradeIntent.Add -> autoClose
        is PerpsTradeIntent.AutoClose -> autoClose
        else -> null
    }
}

private fun PerpsTradeIntent.cancelledLeg(stepId: String): Long? {
    val autoClose = autoCloseSpec()

    return when (stepId) {
        "cancelTakeProfit" -> autoClose?.replacedTakeProfitOrderIndex
        "cancelStopLoss" -> autoClose?.replacedStopLossOrderIndex
        else -> null
    }
}

private fun PerpsTradeIntent.legIndexes(): List<Long> {
    val autoClose = autoCloseSpec()

    return listOfNotNull(
        autoClose?.takeProfit?.clientOrderIndex,
        autoClose?.stopLoss?.clientOrderIndex,
    )
}

private fun PerpsPositionState.classify(
    submittedAtMs: Long,
    expectedBase: BigDecimal?,
    closingEpisodeId: String?,
    acceptWorking: Boolean,
): StepOutcome? {
    // Only the episode this poll saw open counts; an older `last_closed` says nothing about our order.
    if (closingEpisodeId != null && open == null && lastClosed?.episodeId == closingEpisodeId) {
        return StepOutcome.Accepted
    }
    if (acceptWorking && orders.any { it.status.isWorkingOrderStatus() }) {
        return StepOutcome.Accepted
    }
    val filled = orders.mapNotNull { it.filledBase }.maxOrNull() ?: BigDecimal.ZERO
    val dead = orders.firstOrNull { it.status.isDeadOrderStatus() }
    if (expectedBase != null) {
        if (filled.coversBase(expectedBase)) {
            return StepOutcome.Accepted
        }
        if (dead == null) {
            if (filled.signum() == 1) {
                return null
            }
        } else {
            if (filled.signum() == 1) {
                return StepOutcome.PartiallyFilled
            }

            return StepOutcome.Failed(PerpsTradeFailure.OrderRejected(dead.status))
        }
    } else {
        if (orders.any { it.status.isFilledOrderStatus() } || filled.signum() == 1) {
            return StepOutcome.Accepted
        }
        if (dead != null) {
            return StepOutcome.Failed(PerpsTradeFailure.OrderRejected(dead.status))
        }
    }
    // The order event for our index can be missed entirely; an episode of our own age settles it.
    val episode = open ?: lastClosed
    if (episode != null && episode.openedAtMs >= submittedAtMs - EPISODE_SKEW_MS) {
        return StepOutcome.Accepted
    }

    return null
}

internal fun BigDecimal.coversBase(expectedBase: BigDecimal): Boolean {
    val tolerance = expectedBase.abs().multiply(FILL_TOLERANCE_RATIO).max(MIN_FILL_TOLERANCE)

    return add(tolerance) >= expectedBase
}

private const val EPISODE_SKEW_MS = 5_000L
private const val UNKNOWN_GIVE_UP_MS = 86_400_000L
private const val SETTLEMENT_GRACE_MS = 60_000L
private const val POSITION_GONE = "position_gone"
private const val PARTIAL_FILL = "partial_fill"
internal const val ORDER_GONE_STATUS = "order_gone"
internal const val ORDER_FILLED_STATUS = "order_filled"
private const val MARGIN_UNCHANGED = "margin_unchanged"
private const val MARGIN_SETTLE_GRACE_MS = 60_000L
private val MIN_MARGIN_TOLERANCE = BigDecimal.valueOf(1L, 6)
private val MARGIN_TOLERANCE_RATIO = BigDecimal.valueOf(1L, 9)
internal val MIN_FILL_TOLERANCE = BigDecimal.valueOf(1L, 9)
internal val FILL_TOLERANCE_RATIO = BigDecimal.valueOf(1L, 9)

fun PerpsTradeFailure.tag(): String {
    return when (this) {
        is PerpsTradeFailure.OrderRejected -> "order_rejected:$status"
        is PerpsTradeFailure.SendRefused -> "send_refused:${reason.name}:$code"
        is PerpsTradeFailure.SendFailed -> "send_failed:$error"
        is PerpsTradeFailure.NonceUnavailable -> "nonce_unavailable:$error"
        is PerpsTradeFailure.PlannerRejected -> "planner:${kind.name}"
        PerpsTradeFailure.OperationInProgress -> "operation_in_progress"
        PerpsTradeFailure.StaleInput -> "stale_input"
        PerpsTradeFailure.Interrupted -> "interrupted"
        PerpsTradeFailure.Unresolved -> "unresolved"
    }
}

fun PerpsOperationRecord.failure(): PerpsTradeFailure? {
    return error?.takeIf { it.isNotBlank() }?.toTradeFailure()
}

internal fun String.toTradeFailure(): PerpsTradeFailure {
    val parts = split(":", limit = 3)

    return when (parts.first()) {
        "order_rejected" -> PerpsTradeFailure.OrderRejected(parts.getOrElse(1) { "" })
        "send_refused" -> PerpsTradeFailure.SendRefused(
            reason = PerpsSendReason.entries.firstOrNull { it.name == parts.getOrNull(1) }
                ?: PerpsSendReason.OTHER,
            code = parts.getOrElse(2) { "" },
        )

        "send_failed" -> PerpsTradeFailure.SendFailed(parts.getOrNull(1).toPerpsError())
        "nonce_unavailable" -> PerpsTradeFailure.NonceUnavailable(parts.getOrNull(1).toPerpsError())
        "planner" -> PerpsTradeFailure.PlannerRejected(
            kind = PerpsTradeError.entries.firstOrNull { it.name == parts.getOrNull(1) }
                ?: PerpsTradeError.InvalidPlan,
            field = null,
            suggestion = null,
        )

        "operation_in_progress" -> PerpsTradeFailure.OperationInProgress
        "stale_input" -> PerpsTradeFailure.StaleInput
        "unresolved" -> PerpsTradeFailure.Unresolved
        else -> PerpsTradeFailure.Interrupted
    }
}

private fun String?.toPerpsError(): PerpsError {
    return when (this) {
        "Network" -> PerpsError.Network
        "ServiceUnavailable" -> PerpsError.ServiceUnavailable
        else -> PerpsError.Unknown
    }
}

private fun PerpsPlanStepKind.toJournalKind(): PerpsStepKind {
    return when (this) {
        PerpsPlanStepKind.Leverage -> PerpsStepKind.LEVERAGE
        PerpsPlanStepKind.Open -> PerpsStepKind.OPEN
        PerpsPlanStepKind.Close -> PerpsStepKind.CLOSE
        PerpsPlanStepKind.Add -> PerpsStepKind.ADD
        PerpsPlanStepKind.AutoClose -> PerpsStepKind.AUTO_CLOSE
        PerpsPlanStepKind.Margin -> PerpsStepKind.MARGIN
        PerpsPlanStepKind.OrderCancel -> PerpsStepKind.ORDER_CANCEL
        PerpsPlanStepKind.OrderModify -> PerpsStepKind.ORDER_MODIFY
    }
}
