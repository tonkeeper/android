package com.tonapps.async

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

class Relay<T : Any> {
    val channel = MutableSharedFlow<T>(
        replay = 0,
        extraBufferCapacity = 100,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    val events: Flow<T> = channel

    fun emit(event: T) {
        channel.tryEmit(event)
    }
}