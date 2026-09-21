package com.tonapps.extensions

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn

class AppLifecycleProvider {

    /**
     * Whether the process is in the foreground. `true` is emitted immediately, `false` only after
     * [BACKGROUND_GRACE_MS], so short round-trips (camera, share sheet, TonConnect returning to
     * another app) do not tear down long-lived connections gated on this flow.
     */
    @OptIn(FlowPreview::class)
    val observe: Flow<Boolean> = callbackFlow {
        val lifecycle = ProcessLifecycleOwner.get().lifecycle
        val observer = object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                trySend(true)
            }

            override fun onStop(owner: LifecycleOwner) {
                trySend(false)
            }
        }
        trySend(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
        lifecycle.addObserver(observer)
        awaitClose {
            lifecycle.removeObserver(observer)
        }
    }.flowOn(Dispatchers.Main.immediate)
        .distinctUntilChanged()
        .debounce { foreground ->
            if (foreground) {
                0L
            } else {
                BACKGROUND_GRACE_MS
            }
        }.distinctUntilChanged()

    private companion object {
        private const val BACKGROUND_GRACE_MS = 15_000L
    }
}
