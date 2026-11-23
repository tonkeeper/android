package com.tonapps.wallet.data.ledger.connection

import com.tonapps.ledgerkit.controller.LedgerController
import com.tonapps.ledgerkit.controller.LedgerOperationState
import com.tonapps.ledgerkit.controller.LedgerRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class LedgerActivityLog internal constructor(
    private val clock: () -> Long,
    private val store: LedgerActivityStore = LedgerActivityStore.None,
) {

    val entries: StateFlow<List<LedgerActivity>>
        field = MutableStateFlow(emptyList())

    private val lock = Any()
    private var nextId = 0L
    private var openId: Long? = null
    private var openRequest: LedgerRequest? = null

    internal constructor(
        controller: LedgerController,
        scope: CoroutineScope,
        store: LedgerActivityStore,
    ) : this(System::currentTimeMillis, store) {
        scope.launch {
            restore()
            controller.state.collect(::record)
        }
    }

    internal fun restore() = synchronized(lock) {
        val saved = store.load()
        entries.value = saved
        nextId = (saved.maxOfOrNull { it.id } ?: -1L) + 1
    }

    internal fun record(state: LedgerOperationState) = synchronized(lock) {
        when (state) {
            LedgerOperationState.Idle -> close()
            is LedgerOperationState.Active -> track(state.request, LedgerActivityOutcome.InProgress(state.progress))
            is LedgerOperationState.Failed -> track(state.request, LedgerActivityOutcome.Failed(state.failure))
            is LedgerOperationState.Done -> {
                track(state.request, LedgerActivityOutcome.Done)
                openId = null
                openRequest = null
            }
        }
    }

    private fun track(request: LedgerRequest, outcome: LedgerActivityOutcome) {
        val id = openId
        if (id != null && openRequest == request) {
            update(id, outcome)
            return
        }
        close()
        val entry = LedgerActivity(
            id = nextId++,
            deviceId = request.deviceId,
            purpose = request.purpose,
            startedAt = clock(),
            outcome = outcome,
        )
        openId = entry.id
        openRequest = request
        change { (listOf(entry) + it).take(MAX_ENTRIES) }
    }

    private fun close() {
        val id = openId ?: return
        change { list ->
            list.map { entry ->
                if (entry.id == id && entry.outcome is LedgerActivityOutcome.InProgress) {
                    entry.copy(outcome = LedgerActivityOutcome.Interrupted)
                } else {
                    entry
                }
            }
        }
        openId = null
        openRequest = null
    }

    private fun change(transform: (List<LedgerActivity>) -> List<LedgerActivity>) {
        entries.update(transform)
        store.save(entries.value)
    }

    private fun update(id: Long, outcome: LedgerActivityOutcome) {
        change { list -> list.map { if (it.id == id) { it.copy(outcome = outcome) } else { it } } }
    }

    private companion object {
        const val MAX_ENTRIES = 20
    }
}
