package com.tonapps.mvi.graph

sealed interface KState<T> {
    val state: T?
    data class Loading<T>(override val state: T? = null) : KState<T>
    data class Data<T>(override val state: T) : KState<T>

    companion object {
        fun <T> empty(): Data<T?> {
            return Data(state = null)
        }
    }
}

inline fun <T, R> KState<T>.map(transform: (T) -> R): KState<R> = when (this) {
    is KState.Loading -> KState.Loading(state?.let(transform))
    is KState.Data -> KState.Data(transform(state))
}
