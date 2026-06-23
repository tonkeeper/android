package com.tonapps.mvi

import com.tonapps.log.L
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

interface MviEmitter<T> {
    fun emit(event: T)
}

// Event channel ViewModel -> Composable
//context(AsyncViewModel)
class MviRelay<T : Any> : MviEmitter<T> {

    private val channel = Channel<T>(
        capacity = 100,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
        onUndeliveredElement = { L.d("Undelivered Event: $it") }
    )

    val events: Flow<T> = channel.receiveAsFlow()

    override fun emit(event: T) {
        channel.trySend(event)
    }
}
