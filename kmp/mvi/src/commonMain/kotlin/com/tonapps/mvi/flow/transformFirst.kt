package com.tonapps.mvi.flow

import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch

/**
 * Runs [transform] for the first upstream value and drops every value that arrives while it is
 * still in flight; once it completes, the next value starts a new transform.
 */
@OptIn(ExperimentalAtomicApi::class)
fun <T, R> Flow<T>.transformFirst(
    transform: suspend FlowCollector<R>.(value: T) -> Unit,
): Flow<R> = channelFlow {
    val busy = AtomicBoolean(false)
    val collector = FlowCollector<R> { send(it) }

    collect { value ->
        if (busy.compareAndSet(expectedValue = false, newValue = true)) {
            launch(start = CoroutineStart.UNDISPATCHED) {
                try {
                    collector.transform(value)
                } finally {
                    busy.store(false)
                }
            }
        }
    }
}
