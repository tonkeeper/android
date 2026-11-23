package com.tonapps.perps.trade

import com.tonapps.chainkit.perpetual.PerpetualStep
import com.tonapps.chainkit.perpetual.PerpsStepKind
import com.tonapps.perps.PerpsAnalytics
import com.tonapps.perps.data.PerpsRepository
import com.tonapps.wallet.data.multichain.perps.PerpsOperationKind
import com.tonapps.wallet.data.multichain.perps.PerpsOperationStatus
import io.perpsapi.models.ClientMetric
import io.perpsapi.models.ClientMetricPositionClose
import io.perpsapi.models.ClientMetricPositionOpen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.TimeSource

internal class PerpsRunningOperations(
    private val repository: PerpsRepository,
    private val scope: CoroutineScope,
) {

    private val ids = ConcurrentHashMap.newKeySet<String>()

    private val openOrders = ConcurrentHashMap<String, PerpsTradeRequest.Open>()

    private val waits = ConcurrentHashMap<String, Wait>()

    operator fun contains(operationId: String): Boolean = operationId in ids

    fun start(operationId: String, request: PerpsTradeRequest, startedAt: TimeSource.Monotonic.ValueTimeMark) {
        ids.add(operationId)
        waits[operationId] = Wait(request, startedAt)
        if (request is PerpsTradeRequest.Open) {
            openOrders[operationId] = request
        }
    }

    fun finish(operationId: String) {
        ids.remove(operationId)
        openOrders.remove(operationId)
        waits.remove(operationId)
    }

    fun reportSent(operationId: String, step: PerpetualStep, clientOrderIndex: Long) {
        if (step.kind != PerpsStepKind.Open) {
            return
        }
        openOrders.remove(operationId)?.let { PerpsAnalytics.orderSubmitted(it, clientOrderIndex) }
    }

    fun reportSettled(operationId: String, status: PerpsOperationStatus) {
        if (status != PerpsOperationStatus.SUCCEEDED) {
            return
        }
        val wait = waits.remove(operationId) ?: return
        val durationMs = wait.startedAt.elapsedNow().inWholeMilliseconds
        val metric = perpsWaitMetric(wait.request, UUID.randomUUID().toString(), durationMs) ?: return
        scope.launch { repository.recordClientMetric(wait.request.walletId, metric) }
    }

    private class Wait(val request: PerpsTradeRequest, val startedAt: TimeSource.Monotonic.ValueTimeMark)
}

internal fun perpsWaitMetric(request: PerpsTradeRequest, id: String, durationMs: Long): ClientMetric? {
    return when (request.kind) {
        PerpsOperationKind.OPEN -> ClientMetricPositionOpen(id = id, type = "position_open", durationMs = durationMs)
        PerpsOperationKind.CLOSE -> ClientMetricPositionClose(id = id, type = "position_close", durationMs = durationMs)
        else -> null
    }
}
