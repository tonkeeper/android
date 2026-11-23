package com.tonapps.perps.screens.details

import androidx.annotation.StringRes
import com.tonapps.async.Async
import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.chainkit.perpetual.PerpsTradeError
import com.tonapps.mvi.graph.KResult
import com.tonapps.perps.data.PerpsLimitOrder
import com.tonapps.perps.data.PerpsLimitOrderLeg
import com.tonapps.perps.data.PerpsRepository
import com.tonapps.perps.trade.PerpsAccountService
import com.tonapps.perps.trade.PerpsOwnerMessageSigner
import com.tonapps.perps.trade.PerpsPlanFailure
import com.tonapps.perps.trade.PerpsPlannedTrade
import com.tonapps.perps.trade.PerpsPlannerAdapter
import com.tonapps.perps.trade.PerpsSubmitStart
import com.tonapps.perps.trade.PerpsTradeExecutor
import com.tonapps.perps.trade.PerpsTradeFailure
import com.tonapps.perps.trade.PerpsTradeRequest
import com.tonapps.perps.trade.PerpsUnlockFailure
import com.tonapps.perps.trade.isRateLimited
import com.tonapps.wallet.data.multichain.lighter.LighterSigningCredentials
import com.tonapps.wallet.data.multichain.perps.PerpsOperationStatus
import com.tonapps.wallet.localization.Localization
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

sealed interface PerpsLimitChangeResult {
    data class Review(val review: PerpsLimitEditReview) : PerpsLimitChangeResult
    data class Failed(@StringRes val text: Int?) : PerpsLimitChangeResult
    data object Done : PerpsLimitChangeResult
}

class PerpsLimitOrderChanger(
    private val marketIndex: Int,
    private val repository: PerpsRepository,
    private val executor: PerpsTradeExecutor,
    private val adapter: PerpsPlannerAdapter,
    private val accountService: PerpsAccountService,
) {

    suspend fun change(
        action: PerpsAssetDetailsAction.LimitOrderChange,
        wallet: WalletEntity?,
        shown: PerpsLimitOrder?,
    ): PerpsLimitChangeResult {
        wallet ?: return PerpsLimitChangeResult.Failed(Localization.perps_review_failed)
        val unlock: suspend () -> PerpsOwnerMessageSigner? = { accountService.unlockOwnerSigner(wallet) }

        return when (action) {
            is PerpsAssetDetailsAction.CancelLimitOrder -> unlocked(wallet) { credentials ->
                planAndStart(
                    request = PerpsTradeRequest.OrderCancel(
                        walletId = wallet.id,
                        marketIndex = marketIndex,
                        orderIndex = action.orderIndex,
                    ),
                    credentials = credentials,
                    unlock = unlock,
                ).toChangeResult()
            }

            is PerpsAssetDetailsAction.ModifyLimitOrder -> unlocked(wallet) { credentials ->
                prepare(action, wallet.id, credentials, shown)
            }

            is PerpsAssetDetailsAction.ConfirmLimitEdit -> {
                val walletId = action.review.planned.request.walletId
                if (wallet.id != walletId) {
                    return PerpsLimitChangeResult.Failed(Localization.perps_review_failed)
                }
                Async.globalScope(Async.Io)
                    .async { confirm(action.review, walletId, unlock) }
                    .await()
            }
        }
    }

    private suspend fun unlocked(
        wallet: WalletEntity,
        block: suspend (LighterSigningCredentials) -> PerpsLimitChangeResult,
    ): PerpsLimitChangeResult {
        val credentials = when (val result = accountService.unlock(wallet)) {
            is KResult.Ok -> result.value
            is KResult.Err -> return PerpsLimitChangeResult.Failed(
                if (result.value == PerpsUnlockFailure.CANCELLED) {
                    null
                } else {
                    Localization.perps_review_failed
                }
            )
        }

        return withContext(Async.Io) { block(credentials) }
    }

    private suspend fun prepare(
        action: PerpsAssetDetailsAction.ModifyLimitOrder,
        walletId: String,
        credentials: LighterSigningCredentials,
        shown: PerpsLimitOrder?,
    ): PerpsLimitChangeResult {
        val seenBase = action.seenBase ?: return PerpsLimitChangeResult.Failed(Localization.perps_review_failed)
        val order = shown
            ?: when (val fresh = repository.getOpenOrders(marketIndex, walletId)) {
                is KResult.Ok -> fresh.value.firstOrNull { it.orderIndex == action.orderIndex }
                    ?: return PerpsLimitChangeResult.Failed(Localization.perps_limit_order_gone)
                is KResult.Err -> return PerpsLimitChangeResult.Failed(Localization.perps_review_failed)
            }
        val modify = PerpsTradeRequest.OrderModify(
            walletId = walletId,
            marketIndex = marketIndex,
            orderIndex = action.orderIndex,
            price = action.price,
            maxBaseAmount = seenBase,
        )
        val planned = when (val result = plan(modify, credentials)) {
            is KResult.Ok -> result.value
            is KResult.Err -> return PerpsLimitChangeResult.Failed(result.value.limitOrderFailureText())
        }
        val review = limitEditReview(planned, order)
            ?: return PerpsLimitChangeResult.Failed(Localization.perps_review_failed)

        return PerpsLimitChangeResult.Review(review)
    }

    private suspend fun confirm(
        review: PerpsLimitEditReview,
        walletId: String,
        unlock: suspend () -> PerpsOwnerMessageSigner?,
    ): PerpsLimitChangeResult {
        val request = review.confirmedRequest()
        val credentials = review.planned.credentials
        if (review.passedLegs.isNotEmpty()) {
            val planBeforeLegCancels = plan(request, credentials)
            if (planBeforeLegCancels is KResult.Err) {
                return PerpsLimitChangeResult.Failed(planBeforeLegCancels.value.limitOrderFailureText())
            }
        }
        for (leg in review.passedLegs) {
            val cancel = PerpsTradeRequest.OrderCancel(
                walletId = walletId,
                marketIndex = marketIndex,
                orderIndex = leg.orderIndex,
                limitAutoCloseLeg = true,
            )
            when (val start = planAndStart(cancel, credentials, unlock)) {
                is LimitChangeStart.Started -> when (settledStatus(walletId, start.operationId)) {
                    PerpsOperationStatus.SUCCEEDED -> Unit
                    null -> return PerpsLimitChangeResult.Failed(Localization.perps_operation_update_failed_toast)
                    PerpsOperationStatus.FAILED,
                    PerpsOperationStatus.PARTIALLY_FILLED,
                    PerpsOperationStatus.IN_PROGRESS,
                    PerpsOperationStatus.UNKNOWN -> return PerpsLimitChangeResult.Failed(null)
                }

                is LimitChangeStart.Refused -> {
                    if (!start.orderMissing) {
                        return PerpsLimitChangeResult.Failed(start.text)
                    }
                    when (legResting(walletId, request.orderIndex, leg)) {
                        false -> Unit
                        true -> return PerpsLimitChangeResult.Failed(start.text)
                        null -> return PerpsLimitChangeResult.Failed(Localization.perps_review_failed)
                    }
                }
            }
        }

        return planAndStart(request, credentials, unlock).toChangeResult()
    }

    private suspend fun settledStatus(walletId: String, operationId: String): PerpsOperationStatus? {
        return withTimeoutOrNull(LEG_CANCEL_SETTLE_TIMEOUT_MS) {
            executor.observe(walletId)
                .mapNotNull { records -> records.firstOrNull { it.operationId == operationId } }
                .first { it.status != PerpsOperationStatus.IN_PROGRESS }
                .status
        }
    }

    private suspend fun legResting(walletId: String, orderIndex: Long, leg: PerpsLimitOrderLeg): Boolean? {
        val order = when (val result = repository.getOpenOrders(marketIndex, walletId)) {
            is KResult.Ok -> result.value.firstOrNull { it.orderIndex == orderIndex } ?: return true
            is KResult.Err -> return null
        }

        return order.takeProfit?.orderIndex == leg.orderIndex || order.stopLoss?.orderIndex == leg.orderIndex
    }

    private suspend fun plan(
        request: PerpsTradeRequest,
        credentials: LighterSigningCredentials,
    ): KResult<PerpsPlannedTrade, PerpsPlanFailure> {
        val operationId = UUID.randomUUID().toString()
        val result = adapter.plan(request, credentials, mark = null, operationId = operationId)
        if (!result.orderMissing()) {
            return result
        }
        delay(ORDER_MISSING_DELAY_MS)

        return adapter.plan(request, credentials, mark = null, operationId = operationId)
    }

    private suspend fun planAndStart(
        request: PerpsTradeRequest,
        credentials: LighterSigningCredentials,
        unlock: suspend () -> PerpsOwnerMessageSigner?,
    ): LimitChangeStart {
        val planned = when (val result = plan(request, credentials)) {
            is KResult.Ok -> result.value
            is KResult.Err -> return LimitChangeStart.Refused(
                text = result.value.limitOrderFailureText(),
                orderMissing = result.orderMissing(),
            )
        }

        return executor.start(planned, unlock).toLimitChangeStart()
    }

    private companion object {
        const val ORDER_MISSING_DELAY_MS = 1_500L
        const val LEG_CANCEL_SETTLE_TIMEOUT_MS = 360_000L
    }
}

private sealed interface LimitChangeStart {
    data class Started(val operationId: String) : LimitChangeStart

    data class Refused(
        @StringRes val text: Int?,
        val orderMissing: Boolean = false,
    ) : LimitChangeStart
}

private fun LimitChangeStart.toChangeResult(): PerpsLimitChangeResult {
    return when (this) {
        is LimitChangeStart.Started -> PerpsLimitChangeResult.Done
        is LimitChangeStart.Refused -> PerpsLimitChangeResult.Failed(text)
    }
}

private fun PerpsSubmitStart.toLimitChangeStart(): LimitChangeStart {
    return when (this) {
        is PerpsSubmitStart.Started -> LimitChangeStart.Started(operationId)
        is PerpsSubmitStart.Blocked -> LimitChangeStart.Refused(Localization.perps_operation_in_progress)
        is PerpsSubmitStart.Failed -> LimitChangeStart.Refused(
            when (failure) {
                PerpsTradeFailure.OperationInProgress -> Localization.perps_operation_in_progress
                PerpsTradeFailure.ApprovalCancelled -> null
                is PerpsTradeFailure.SendRefused if failure.isRateLimited -> Localization.perps_too_many_requests
                else -> Localization.perps_review_failed
            }
        )

        is PerpsSubmitStart.Requote -> LimitChangeStart.Refused(Localization.perps_review_failed)
    }
}

private fun KResult<PerpsPlannedTrade, PerpsPlanFailure>.orderMissing(): Boolean {
    return ((this as? KResult.Err)?.value as? PerpsPlanFailure.Planner)?.kind == PerpsTradeError.OrderNotFound
}

private fun PerpsPlanFailure.limitOrderFailureText(): Int {
    if (this is PerpsPlanFailure.ClockSkew) {
        return Localization.perps_review_wrong_device_time
    }
    if (this !is PerpsPlanFailure.Planner) {
        return Localization.perps_review_failed
    }

    return when (kind) {
        PerpsTradeError.OrderNotFound -> Localization.perps_limit_order_gone

        PerpsTradeError.InvalidInput -> when (field) {
            BASE_AMOUNT_FIELD -> Localization.perps_limit_order_too_small
            null -> Localization.perps_review_invalid_amount
            else -> Localization.perps_review_failed
        }

        PerpsTradeError.ExceedsPosition -> if (field == BASE_AMOUNT_FIELD) {
            Localization.perps_limit_order_too_large
        } else {
            Localization.perps_review_failed
        }

        PerpsTradeError.TradingDisabled -> Localization.perps_order_trading_disabled

        PerpsTradeError.StaleInput -> Localization.perps_limit_order_inputs_changed

        else -> Localization.perps_review_failed
    }
}

private const val BASE_AMOUNT_FIELD = "baseAmount"
