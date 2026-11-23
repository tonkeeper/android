package com.tonapps.perps.trade

import com.tonapps.log.L
import com.tonapps.mvi.graph.KResult
import com.tonapps.wallet.data.multichain.lighter.LighterSigningCredentials
import com.tonapps.wallet.data.multichain.lighter.PerpetualRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.UUID

internal const val RENEWAL_OPERATION_PREFIX = "renewal-"

internal fun isRenewalOperation(operationId: String): Boolean = operationId.startsWith(RENEWAL_OPERATION_PREFIX)

class PerpsAutoCloseRenewal(
    private val planner: PerpsTradePlanner,
    private val executor: PerpsTradeExecutor,
    private val perpetualRepository: PerpetualRepository,
    private val confirmedAutoClose: PerpsConfirmedAutoCloseStore,
    private val scope: CoroutineScope,
    private val nowMs: () -> Long = System::currentTimeMillis,
) {

    private val lock = Any()

    private val checkedAtMs = HashMap<String, Long>()

    fun renewInBackground(walletId: String, marketIndices: List<Int>) {
        if (marketIndices.isEmpty()) {
            return
        }
        synchronized(lock) {
            val now = nowMs()
            if (now - (checkedAtMs[walletId] ?: 0L) < CHECK_INTERVAL_MS) {
                return
            }
            checkedAtMs[walletId] = now
        }
        scope.launch {
            var rearm = true
            try {
                rearm = renew(walletId, marketIndices.distinct())
            } catch (e: Throwable) {
                if (e is CancellationException) {
                    throw e
                }
                L.e(e, "perps: auto-close renewal failed for $walletId")
                rearm = false
            } finally {
                if (rearm) {
                    synchronized(lock) { checkedAtMs[walletId] = nowMs() - CHECK_INTERVAL_MS + RETRY_AFTER_MS }
                }
            }
        }
    }

    private suspend fun renew(walletId: String, markets: List<Int>): Boolean {
        val credentials = perpetualRepository.signingCredentials(walletId) ?: return false

        return markets.map { renewMarket(walletId, it, credentials) }.any { it }
    }

    private suspend fun renewMarket(
        walletId: String,
        marketIndex: Int,
        credentials: LighterSigningCredentials,
    ): Boolean {
        val confirmed = confirmedAutoClose.confirmed(walletId)
        val due = when (val result = planner.autoCloseRenewalDue(walletId, marketIndex, credentials, nowMs(), confirmed)) {
            is KResult.Ok -> result.value
            is KResult.Err -> return true
        }
        if (!due) {
            return false
        }
        val request = PerpsTradeRequest.AutoCloseRenewal(walletId, marketIndex, confirmed)
        val operationId = RENEWAL_OPERATION_PREFIX + UUID.randomUUID()
        repeat(PLAN_ATTEMPTS) {
            val planned = when (val result = planner.plan(request, credentials, mark = null, operationId = operationId)) {
                is KResult.Ok -> result.value
                is KResult.Err -> when (val failure = result.value) {
                    is PerpsPlanFailure.Load -> return true
                    is PerpsPlanFailure.Planner -> if (failure.kind in PERPS_RETRYABLE_PLAN_KINDS) {
                        return@repeat
                    } else {
                        return false
                    }

                    PerpsPlanFailure.ClockSkew -> return false
                }
            }
            when (val start = executor.start(planned) { null }) {
                is PerpsSubmitStart.Started -> {
                    L.d("perps: auto-close renewal started market=$marketIndex op=${start.operationId}")
                    return false
                }

                is PerpsSubmitStart.Requote -> Unit

                is PerpsSubmitStart.Blocked -> {
                    L.d("perps: auto-close renewal blocked market=$marketIndex by op=${start.operationId}")
                    return false
                }

                is PerpsSubmitStart.Failed -> {
                    L.d("perps: auto-close renewal failed market=$marketIndex (${start.failure})")
                    return false
                }
            }
        }
        return true
    }

    private companion object {
        const val CHECK_INTERVAL_MS = 60 * 60 * 1000L
        const val PLAN_ATTEMPTS = 2
        const val RETRY_AFTER_MS = 60_000L
    }
}
