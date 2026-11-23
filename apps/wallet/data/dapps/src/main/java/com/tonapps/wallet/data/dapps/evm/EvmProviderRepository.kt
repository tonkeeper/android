package com.tonapps.wallet.data.dapps.evm

import com.tonapps.blockchain.model.ConfirmRequest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet

class EvmProviderRepository {

    sealed interface Outcome {

        val requestId: String

        data class Signed(override val requestId: String, val result: String) : Outcome

        data class Rejected(override val requestId: String) : Outcome

        data class Failed(override val requestId: String) : Outcome
    }

    internal class Entry(
        val request: ConfirmRequest,
        val outcome: CompletableDeferred<Outcome>,
    )

    private val pending = MutableStateFlow<Map<String, Entry>>(emptyMap())

    fun expect(requestId: String, request: ConfirmRequest): Pending {
        val entry = pending.updateAndGet {
            if (requestId in it) {
                it
            } else {
                it + (requestId to Entry(request, CompletableDeferred()))
            }
        }.getValue(requestId)
        return Pending(requestId, entry)
    }

    // The confirmation sheet reads the request from here rather than from its arguments: a
    // request can hold a chainkit Fee, which has no serializer.
    fun pendingRequest(requestId: String): ConfirmRequest? {
        return pending.value[requestId]?.request
    }

    fun requestGone(requestId: String): Flow<Unit> {
        return pending.filter { requestId !in it }.take(1).map { }
    }

    // The entry goes at once, not when the waiting side wakes up: a sheet restored right after
    // would otherwise still find the request and offer to sign it again.
    fun approve(requestId: String, result: String) {
        remove(requestId)?.outcome?.complete(Outcome.Signed(requestId, result))
    }

    fun reject(requestId: String) {
        remove(requestId)?.outcome?.complete(Outcome.Rejected(requestId))
    }

    fun fail(requestId: String) {
        remove(requestId)?.outcome?.complete(Outcome.Failed(requestId))
    }

    private fun remove(requestId: String): Entry? {
        return pending.getAndUpdate { it - requestId }[requestId]
    }

    inner class Pending internal constructor(
        private val requestId: String,
        private val entry: Entry,
    ) {

        suspend fun await(): Outcome {
            return try {
                entry.outcome.await()
            } finally {
                pending.update {
                    if (it[requestId] === entry) {
                        it - requestId
                    } else {
                        it
                    }
                }
            }
        }
    }
}
