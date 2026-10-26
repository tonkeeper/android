package com.tonapps.wallet.data.dapps.confirm

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * Confirm sheets of dApp requests, one on screen at a time: the next one is shown only after the
 * current sheet is destroyed, not when its request is answered — a sheet still shows "Done" after
 * that. Callable from any thread.
 */
class ConfirmSheetQueue {

    /** What a sheet tells the presenter about itself; null for a sheet opened outside the queue. */
    interface Sheet {
        val queuedRequestId: String?
    }

    private data class State(
        val current: String? = null,
        val waiting: Set<String> = emptySet(),
    ) {

        fun contains(requestId: String): Boolean = current == requestId || requestId in waiting
    }

    private val state = MutableStateFlow(State())

    private val sheetDestroyed = MutableSharedFlow<String>(
        extraBufferCapacity = SHEET_DESTROYED_BUFFER,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /** The request whose sheet is to be on screen. */
    val current: Flow<String?> = state.map { it.current }.distinctUntilChanged()


    fun enqueue(requestId: String) {
        state.update { state ->
            when {
                state.contains(requestId) -> state
                state.current == null -> state.copy(current = requestId)
                else -> state.copy(waiting = state.waiting + requestId)
            }
        }
    }

    fun onSheetDestroyed(requestId: String) {
        val previous = state.getAndUpdate { state ->
            if (state.current == requestId) {
                State(current = state.waiting.firstOrNull(), waiting = state.waiting.drop(1).toSet())
            } else {
                state
            }
        }
        if (previous.current == requestId) {
            sheetDestroyed.tryEmit(requestId)
        }
    }

    /**
     * Returns once the sheet of [requestId] is destroyed while it is the current one. Only a sheet
     * destroyed after the call counts, so start waiting before the request is enqueued.
     */
    suspend fun awaitSheetDestroyed(requestId: String) {
        sheetDestroyed.first { it == requestId }
    }

    /**
     * The request no longer needs an answer. Only a waiting one is dropped: the current one keeps the
     * queue until its sheet is destroyed, or the next sheet would open over it.
     */
    fun discard(requestId: String) {
        state.update { state ->
            state.copy(waiting = state.waiting - requestId)
        }
    }

    private companion object {
        private const val SHEET_DESTROYED_BUFFER = 16
    }
}