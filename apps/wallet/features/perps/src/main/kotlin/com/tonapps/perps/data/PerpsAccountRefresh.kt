package com.tonapps.perps.data

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

class PerpsAccountRefresh {

    private val signals = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    val events: Flow<Unit> = signals

    fun notifyChanged() {
        signals.tryEmit(Unit)
    }
}
