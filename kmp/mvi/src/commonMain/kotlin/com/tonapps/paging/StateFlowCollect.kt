package com.tonapps.paging

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.ProduceStateScope
import androidx.compose.runtime.SnapshotMutationPolicy
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.structuralEqualityPolicy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

@Composable
fun <T> StateFlow<T>.collectAsStateWorkaround(
    context: CoroutineContext = EmptyCoroutineContext,
    policy: SnapshotMutationPolicy<T> = structuralEqualityPolicy(),
): State<T> =
    collectAsStateWorkaround(value, context, policy)

@Composable
fun <T : R, R> Flow<T>.collectAsStateWorkaround(
    initial: R,
    context: CoroutineContext = EmptyCoroutineContext,
    policy: SnapshotMutationPolicy<R> = structuralEqualityPolicy(),
): State<R> {
    return produceState(initial, this, context, policy) {
        if (context == EmptyCoroutineContext) {
            collect { value = it }
        } else withContext(context) { collect { value = it } }
    }
}

@Composable
fun <T> produceState(
    initialValue: T,
    key1: Any?,
    key2: Any?,
    policy: SnapshotMutationPolicy<T> = structuralEqualityPolicy(),
    producer: suspend ProduceStateScope<T>.() -> Unit
): State<T> {
    val result = remember { mutableStateOf(initialValue, policy) }
    LaunchedEffect(key1, key2) { ProduceStateScopeImpl(result, coroutineContext).producer() }
    return result
}

private class ProduceStateScopeImpl<T>(
    state: MutableState<T>,
    override val coroutineContext: CoroutineContext
) : ProduceStateScope<T>, MutableState<T> by state {

    override suspend fun awaitDispose(onDispose: () -> Unit): Nothing {
        try {
            suspendCancellableCoroutine<Nothing> {}
        } finally {
            onDispose()
        }
    }
}
