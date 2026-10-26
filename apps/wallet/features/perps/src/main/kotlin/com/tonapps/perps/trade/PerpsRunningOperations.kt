package com.tonapps.perps.trade

import com.tonapps.perps.PerpsAnalytics
import com.tonapps.perps.failureReason
import com.tonapps.wallet.data.multichain.perps.PerpsOperationKind
import java.util.concurrent.ConcurrentHashMap

internal class PerpsRunningOperations {

    private val ids = ConcurrentHashMap.newKeySet<String>()

    private val openOrders = ConcurrentHashMap.newKeySet<String>()

    operator fun contains(operationId: String): Boolean = operationId in ids

    fun start(operationId: String, kind: PerpsOperationKind) {
        ids.add(operationId)
        if (kind == PerpsOperationKind.OPEN || kind == PerpsOperationKind.OPEN_LIMIT) {
            openOrders.add(operationId)
        }
    }

    fun finish(operationId: String) {
        ids.remove(operationId)
        openOrders.remove(operationId)
    }

    fun reportFailure(operationId: String, error: String) {
        if (openOrders.remove(operationId)) {
            PerpsAnalytics.orderFailed(error.toTradeFailure().failureReason())
        }
    }
}
