package com.tonapps.singer.core.flow

sealed class Resource<D>(val data: D? = null) {
    class Loading<T> : Resource<T>()
    class Error<T>(val error: String) : Resource<T>()

    class Success<T>(data: T) : Resource<T>(data = data) {
        val value: T
            get() = requireNotNull(this.data)
    }
}