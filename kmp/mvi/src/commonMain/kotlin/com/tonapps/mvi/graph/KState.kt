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
