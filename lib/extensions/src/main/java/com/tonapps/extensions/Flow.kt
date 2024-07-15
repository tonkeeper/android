package com.tonapps.extensions

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.timeout
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@Suppress("FunctionName", "unused")
fun <T> MutableEffectFlow(): MutableSharedFlow<T> {
    return MutableSharedFlow(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
}

fun <T, R> Flow<List<T>>.mapList(transform: (T) -> R): Flow<List<R>> = map { list ->
    list.map(transform)
}

fun <T> Flow<List<T>>.filterList(predicate: (T) -> Boolean): Flow<List<T>> = map { list ->
    list.filter(predicate)
}

@OptIn(FlowPreview::class)
fun <T> Flow<T>.single(timeout: Duration = 1.seconds): Flow<T> {
    return this.take(1).timeout(timeout)
}

fun <T> Flow<T>.state(
    scope: CoroutineScope,
    context: CoroutineContext = Dispatchers.IO
): Flow<T & Any> {
    return flowOn(context).stateIn(scope, SharingStarted.Eagerly, null).filterNotNull()
}
