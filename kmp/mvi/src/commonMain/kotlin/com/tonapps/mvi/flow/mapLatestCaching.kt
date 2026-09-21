package com.tonapps.mvi.flow

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.mapLatest

@OptIn(ExperimentalCoroutinesApi::class)
fun <T, R> Flow<T>.mapLatestCatching(
    onError: (suspend (Throwable) -> Unit)? = null,
    onFinally: (suspend () -> Unit)? = null,
    transform: suspend (T) -> R?
): Flow<R?> = mapLatest {
    try {
        transform(it)
    } catch (e: Throwable) {
        if (e is CancellationException) {
            throw e
        }

        onError?.invoke(e)
        null
    } finally {
        onFinally?.invoke()
    }
}

/**
 * [flatMapLatest] that survives failures: an exception thrown while building
 * or collecting the inner flow stops the current inner flow instead of
 * cancelling the whole chain.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun <T, R> Flow<T>.flatMapLatestCatching(
    onError: (suspend (Throwable) -> Unit)? = null,
    transform: suspend (T) -> Flow<R>,
): Flow<R> = flatMapLatest { value ->
    try {
        transform(value)
            .catch { e ->
                if (e is CancellationException) {
                    throw e
                }

                onError?.invoke(e)
            }
    } catch (e: Throwable) {
        if (e is CancellationException) {
            throw e
        }

        onError?.invoke(e)
        emptyFlow()
    }
}
