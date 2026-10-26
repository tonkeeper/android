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

/** [withLatestFrom] over two flows, both sampled at the same emission. */
fun <T, R1, R2, V> Flow<T>.withLatestFrom(
    first: StateFlow<R1>,
    second: StateFlow<R2>,
    transform: suspend (T, R1, R2) -> V,
): Flow<V> = map { value ->
    transform(value, first.value, second.value)
}

/** [withLatestFrom] over three flows, all sampled at the same emission. */
fun <T, R1, R2, R3, V> Flow<T>.withLatestFrom(
    first: StateFlow<R1>,
    second: StateFlow<R2>,
    third: StateFlow<R3>,
    transform: suspend (T, R1, R2, R3) -> V,
): Flow<V> = map { value ->
    transform(value, first.value, second.value, third.value)
}
