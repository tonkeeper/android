package com.tonapps.mvi.flow

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map

/**
 * Emits only when this flow emits, sampling the current value of [other]. Unlike `combine`,
 * [other] never drives the output, so it declares the dependency without triggering on it.
 */
fun <T, R, V> Flow<T>.withLatestFrom(
    other: StateFlow<R>,
    transform: suspend (T, R) -> V,
): Flow<V> = map { value ->
    transform(value, other.value)
}
