package com.tonapps.perps.trade

import com.tonapps.chainkit.perpetual.PerpsMarginSettings
import com.tonapps.chainkit.perpetual.PerpsTradeError
import com.tonapps.log.L
import com.tonapps.mvi.graph.KResult
import com.tonapps.perps.data.PerpsAutoCloseDraft
import kotlinx.coroutines.CancellationException
import java.util.UUID

fun interface PerpsAttemptPacer {
    suspend fun repeatUntilDone(walletId: String, marketIndex: Int, attempt: suspend () -> Boolean)
}

class PerpsAutoCloseFromEntry(
    private val planner: PerpsTradePlanner,
    private val start: suspend (PerpsPlannedTrade) -> PerpsSubmitStart,
    private val pacer: PerpsAttemptPacer,
) {

    suspend fun afterFill(filled: PerpsPlannedTrade) {
        val request = filled.toAutoCloseFromEntry() ?: return
        try {
            place(request, filled)
        } catch (e: Throwable) {
            if (e is CancellationException) {
                throw e
            }
            L.e(e, "perps: auto-close from entry failed market=${request.marketIndex}")
        }
    }

    private suspend fun place(request: PerpsTradeRequest.AutoCloseFromEntry, filled: PerpsPlannedTrade) {
        val operationId = RENEWAL_OPERATION_PREFIX + UUID.randomUUID()
        var attempts = 0
        pacer.repeatUntilDone(request.walletId, request.marketIndex) {
            attempts++
            val done = attempt(request, filled, operationId)
            if (!done && attempts >= ATTEMPTS) {
                L.d("perps: auto-close from entry gave up market=${request.marketIndex}")
            }
            done || attempts >= ATTEMPTS
        }
    }

    private suspend fun attempt(
        request: PerpsTradeRequest.AutoCloseFromEntry,
        filled: PerpsPlannedTrade,
        operationId: String,
    ): Boolean {
        val planned = when (
            val result = planner.plan(request, filled.credentials, mark = null, operationId = operationId)
        ) {
            is KResult.Ok -> result.value
            is KResult.Err -> if (result.value.isWorthRetrying()) {
                return false
            } else {
                L.d("perps: auto-close from entry skipped market=${request.marketIndex} (${result.value})")
                return true
            }
        }
        return when (val started = start(planned)) {
            is PerpsSubmitStart.Started -> {
                L.d("perps: auto-close from entry started market=${request.marketIndex} op=${started.operationId}")
                true
            }

            is PerpsSubmitStart.Blocked,
            is PerpsSubmitStart.Requote -> false

            is PerpsSubmitStart.Failed -> {
                L.d("perps: auto-close from entry failed market=${request.marketIndex} (${started.failure})")
                true
            }
        }
    }

    private companion object {
        const val ATTEMPTS = 6
    }
}

private fun PerpsPlannedTrade.toAutoCloseFromEntry(): PerpsTradeRequest.AutoCloseFromEntry? {
    val trade = request
    val placed: PerpsAutoCloseDraft?
    val leverage: Int?
    when (trade) {
        is PerpsTradeRequest.Open -> {
            placed = trade.autoClose
            leverage = trade.leverage
        }

        is PerpsTradeRequest.Add -> {
            placed = (trade.autoClose as? PerpsAutoCloseEdit.Set)?.target
            leverage = (context.marginSettings as? PerpsMarginSettings.Known)
                ?.let { PerpsProductRules.leverage(it.initialMarginBps) }
        }

        else -> return null
    }
    if (placed == null || leverage == null || leverage <= 0 ||
        (placed.takeProfitRoi == null && placed.stopLossRoi == null)
    ) {
        return null
    }

    return PerpsTradeRequest.AutoCloseFromEntry(
        walletId = trade.walletId,
        marketIndex = trade.marketIndex,
        leverage = leverage,
        placed = placed,
    )
}

private fun PerpsPlanFailure.isWorthRetrying(): Boolean {
    return when (this) {
        is PerpsPlanFailure.Load -> true
        is PerpsPlanFailure.Planner -> kind in PERPS_RETRYABLE_PLAN_KINDS || kind in NOT_VISIBLE_YET_KINDS
        PerpsPlanFailure.ClockSkew -> false
    }
}

private val NOT_VISIBLE_YET_KINDS = setOf(
    PerpsTradeError.OrderNotFound,
    PerpsTradeError.NoPosition,
    PerpsTradeError.IncompleteContext,
)
