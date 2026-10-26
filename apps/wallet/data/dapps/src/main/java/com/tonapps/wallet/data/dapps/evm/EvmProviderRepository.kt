package com.tonapps.wallet.data.dapps.evm

import com.tonapps.blockchain.model.ConfirmRequest
import kotlinx.coroutines.CompletableDeferred
import java.util.concurrent.ConcurrentHashMap

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

    private val pending = ConcurrentHashMap<String, Entry>()

    fun expect(requestId: String, request: ConfirmRequest): Pending {
        val entry = pending.computeIfAbsent(requestId) { Entry(request, CompletableDeferred()) }
        return Pending(requestId, entry)
    }

    // The confirmation sheet reads the request from here rather than from its arguments: a
    // request can hold a chainkit Fee, which has no serializer.
    fun pendingRequest(requestId: String): ConfirmRequest? {
        return pending[requestId]?.request
    }

    // The entry goes at once, not when the waiting side wakes up: a sheet restored right after
    // would otherwise still find the request and offer to sign it again.
    fun approve(requestId: String, result: String) {
        pending.remove(requestId)?.outcome?.complete(Outcome.Signed(requestId, result))
    }

    fun reject(requestId: String) {
        pending.remove(requestId)?.outcome?.complete(Outcome.Rejected(requestId))
    }

    fun fail(requestId: String) {
        pending.remove(requestId)?.outcome?.complete(Outcome.Failed(requestId))
    }

    inner class Pending internal constructor(
        private val requestId: String,
        private val entry: Entry,
    ) {

        suspend fun await(): Outcome {
            return try {
                entry.outcome.await()
            } finally {
                pending.remove(requestId, entry)
            }
        }
    }
}
