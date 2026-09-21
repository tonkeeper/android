package com.tonapps.mvi.graph

sealed interface KResult<T, E> {

    @JvmInline
    value class Ok<T, E>(val value: T) : KResult<T, E>

    @JvmInline
    value class Err<T, E>(val value: E) : KResult<T, E>

    val isErr: Boolean get() = this is Err
    val isOk: Boolean get() = this is Ok

    fun unwrap(): T {
        return (this as? Ok)?.value
            ?: throw IllegalStateException("RResult can't be unwrap because of it's Err")
    }

    fun getOrNull(): T? {
        return when (this) {
            is Err -> null
            is Ok -> value
        }
    }

    fun getOrDefault(default: T): T {
        return when (this) {
            is Err -> default
            is Ok -> value
        }
    }

    fun getOrDefault(default: (error: E) -> T): T {
        return when (this) {
            is Err -> default(value)
            is Ok -> value
        }
    }

    fun onResult(
        onOk: (value: T) -> Unit,
        onErr: (err: E) -> Unit
    ) {
        when (this) {
            is Err -> onErr(value)
            is Ok -> onOk(value)
        }
    }
}
