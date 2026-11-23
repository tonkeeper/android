package com.tonapps.wallet.data.dapps.wc

import com.tonapps.blockchain.model.ConfirmContext
import com.tonapps.blockchain.model.ConfirmRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.update

internal class WcPendingRequests {

    private val requests = MutableStateFlow<Map<String, ConfirmRequest>>(emptyMap())

    private val ConfirmRequest.topic: String?
        get() = (context as? ConfirmContext.Dapp)?.topic

    fun put(requestId: String, request: ConfirmRequest) {
        requests.update { it + (requestId to request) }
    }

    fun get(requestId: String): ConfirmRequest? {
        return requests.value[requestId]
    }

    fun remove(requestId: String): ConfirmRequest? {
        return requests.getAndUpdate { it - requestId }[requestId]
    }

    fun removeTopic(topic: String): List<String> {
        val previous = requests.getAndUpdate { current ->
            current.filterValues { it.topic != topic }
        }
        return previous.filterValues { it.topic == topic }.keys.toList()
    }

    fun gone(requestId: String): Flow<Unit> {
        return requests.filter { requestId !in it }.take(1).map { }
    }
}
