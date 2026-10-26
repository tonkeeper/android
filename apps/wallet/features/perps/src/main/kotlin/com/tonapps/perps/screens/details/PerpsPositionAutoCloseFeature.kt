package com.tonapps.perps.screens.details

import android.content.Context
import com.tonapps.async.Async
import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.chainkit.perpetual.PerpsTradeError
import com.tonapps.log.L
import com.tonapps.mvi.contract.MviAction
import com.tonapps.mvi.contract.MviViewState
import com.tonapps.mvi.flow.withLatestFrom
import com.tonapps.mvi.graph.GraphViewModel
import com.tonapps.mvi.graph.KResult
import com.tonapps.mvi.graph.KState
import com.tonapps.perps.data.PerpsAutoCloseDraft
import com.tonapps.perps.screens.order.PerpsAutoCloseSubmit
import com.tonapps.perps.screens.order.PerpsAutoCloseSubmitError
import com.tonapps.perps.screens.review.PERPS_RETRYABLE_PLAN_KINDS
import com.tonapps.perps.screens.review.invalidAutoCloseText
import com.tonapps.perps.screens.review.toAutoCloseField
import com.tonapps.perps.trade.PerpsAccountService
import com.tonapps.perps.trade.PerpsPlanFailure
import com.tonapps.perps.trade.PerpsPlannerAdapter
import com.tonapps.perps.trade.PerpsSubmitStart
import com.tonapps.perps.trade.PerpsTradeExecutor
import com.tonapps.perps.trade.PerpsTradeFailure
import com.tonapps.perps.trade.PerpsTradeRequest
import com.tonapps.perps.trade.PerpsUnlockFailure
import com.tonapps.wallet.data.multichain.account.UnifiedAccountRepository
import com.tonapps.wallet.data.multichain.perps.PerpsOperationStatus
import com.tonapps.wallet.localization.Localization
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import kotlin.time.Duration.Companion.seconds

interface PerpsPositionAutoCloseViewState : MviViewState {
    val submit: StateFlow<PerpsAutoCloseSubmit>
}

sealed interface PerpsPositionAutoCloseAction : MviAction {
    data class Submit(val context: Context, val draft: PerpsAutoCloseDraft?) : PerpsPositionAutoCloseAction
}

class PerpsPositionAutoCloseFeature(
    private val marketIndex: Int,
    private val adapter: PerpsPlannerAdapter,
    private val executor: PerpsTradeExecutor,
    private val accountService: PerpsAccountService,
    unifiedAccountRepository: UnifiedAccountRepository,
) : GraphViewModel<PerpsPositionAutoCloseViewState, PerpsPositionAutoCloseAction>(),
    PerpsPositionAutoCloseViewState {

    private sealed interface Outcome {
        data object Done : Outcome
        data object Cancelled : Outcome
        data class Failed(val error: PerpsAutoCloseSubmitError) : Outcome
    }

    private val wallet = unifiedAccountRepository.selectedTonWalletFlow
        .cacheState()

    override val submit = actions
        .on<PerpsPositionAutoCloseAction.Submit>()
        .withLatestFrom(wallet) { action, wallet -> action to wallet }
        .transformStateFirst { (action, wallet) -> submit(action, wallet) }
        .map { state ->
            val outcome = (state as? KState.Data)?.state
            PerpsAutoCloseSubmit(
                submitting = state is KState.Loading,
                error = (outcome as? Outcome.Failed)?.error,
                done = outcome == Outcome.Done,
            )
        }
        .cacheState(initialValue = PerpsAutoCloseSubmit(submitting = false, error = null, done = false))

    private suspend fun submit(action: PerpsPositionAutoCloseAction.Submit, wallet: WalletEntity?): Outcome {
        wallet ?: return Outcome.Failed(GENERIC_ERROR)
        val operationId = UUID.randomUUID().toString()

        return try {
            val credentials = when (val unlock = accountService.unlock(wallet, action.context)) {
                is KResult.Ok -> unlock.value
                is KResult.Err -> return if (unlock.value == PerpsUnlockFailure.CANCELLED) {
                    Outcome.Cancelled
                } else {
                    Outcome.Failed(GENERIC_ERROR)
                }
            }
            val request = PerpsTradeRequest.AutoClose(
                walletId = wallet.id,
                marketIndex = marketIndex,
                target = action.draft,
            )

            repeat(PLAN_ATTEMPTS) {
                val result = withContext(Async.Io) {
                    adapter.plan(request, credentials, mark = null, operationId = operationId)
                }
                when (result) {
                    is KResult.Ok -> when (val start = executor.start(result.value)) {
                        is PerpsSubmitStart.Started -> return settle(wallet.id, start.operationId)
                        is PerpsSubmitStart.Blocked -> return Outcome.Failed(IN_PROGRESS_ERROR)
                        is PerpsSubmitStart.Failed -> return Outcome.Failed(start.failure.toSubmitError())
                        is PerpsSubmitStart.Requote -> Unit
                    }

                    is KResult.Err -> {
                        val failure = result.value
                        if (failure !is PerpsPlanFailure.Planner || failure.kind !in PERPS_RETRYABLE_PLAN_KINDS) {
                            return Outcome.Failed(failure.toSubmitError())
                        }
                    }
                }
            }
            Outcome.Failed(GENERIC_ERROR)
        } catch (t: Throwable) {
            verifyError(t)
            L.e(t)
            Outcome.Failed(GENERIC_ERROR)
        }
    }

    private suspend fun settle(walletId: String, operationId: String): Outcome {
        val record = withTimeoutOrNull(SETTLE_TIMEOUT) {
            executor.observe(walletId)
                .mapNotNull { records -> records.firstOrNull { it.operationId == operationId } }
                .first { it.status == PerpsOperationStatus.SUCCEEDED || it.status == PerpsOperationStatus.FAILED }
        }

        return when (record?.status) {
            PerpsOperationStatus.SUCCEEDED -> Outcome.Done

            PerpsOperationStatus.FAILED -> {
                executor.acknowledgeFailure(operationId)
                Outcome.Failed(PerpsAutoCloseSubmitError(field = null, text = record.failureText()))
            }

            else -> {
                executor.acknowledgeFailure(operationId)
                Outcome.Failed(
                    PerpsAutoCloseSubmitError(field = null, text = Localization.perps_operation_status_unknown_toast),
                )
            }
        }
    }

    private companion object {
        const val PLAN_ATTEMPTS = 2

        val SETTLE_TIMEOUT = 90.seconds

        val GENERIC_ERROR = PerpsAutoCloseSubmitError(field = null, text = Localization.perps_review_failed)

        val IN_PROGRESS_ERROR = PerpsAutoCloseSubmitError(field = null, text = Localization.perps_operation_in_progress)

        fun PerpsPlanFailure.toSubmitError(): PerpsAutoCloseSubmitError {
            return when (this) {
                PerpsPlanFailure.ClockSkew ->
                    PerpsAutoCloseSubmitError(field = null, text = Localization.perps_review_wrong_device_time)

                is PerpsPlanFailure.Planner if kind == PerpsTradeError.InvalidAutoClose -> invalidAutoCloseError(field)

                else -> GENERIC_ERROR
            }
        }

        fun PerpsTradeFailure.toSubmitError(): PerpsAutoCloseSubmitError {
            return when {
                this is PerpsTradeFailure.PlannerRejected && kind == PerpsTradeError.InvalidAutoClose ->
                    invalidAutoCloseError(field)

                this == PerpsTradeFailure.OperationInProgress -> IN_PROGRESS_ERROR

                else -> GENERIC_ERROR
            }
        }

        fun invalidAutoCloseError(field: String?): PerpsAutoCloseSubmitError {
            val target = field.toAutoCloseField()
            return PerpsAutoCloseSubmitError(field = target, text = target.invalidAutoCloseText())
        }
    }
}

/*
 *   selectedTonWalletFlow ──► wallet?
 *
 *   Submit, wallet? ⋯──► [first] submit
 */
