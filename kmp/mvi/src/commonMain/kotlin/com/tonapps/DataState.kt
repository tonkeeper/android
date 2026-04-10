package com.tonapps

class DataState<T> {
    companion object {
        fun <T> error() = DataState<T>().apply {
            isErrorState = true
        }

        fun <T> loading() = DataState<T>().apply {
            isLoadingState = true
        }

        fun <T> data(data: T) = DataState<T>().apply {
            dataState = data
        }
    }

    private var isErrorState: Boolean = false
    private var isLoadingState: Boolean = false
    private var dataState: T? = null

    val isError get() = isErrorState
    val isLoading get() = isLoadingState
    val data get(): T? = dataState
}
