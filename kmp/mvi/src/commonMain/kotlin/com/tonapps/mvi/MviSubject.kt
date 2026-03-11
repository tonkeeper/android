package com.tonapps.mvi

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

// Data channel: ViewModel -> Repository (e.g. with debounce)
class MviSubject<T : Any?> {

    private val mutEvents = MutableSharedFlow<T>(
        replay = 1,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    val events = mutEvents.asSharedFlow()

    fun emit(event: T) {
        mutEvents.tryEmit(event)
    }
}
