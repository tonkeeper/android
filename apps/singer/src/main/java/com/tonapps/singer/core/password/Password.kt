package com.tonapps.singer.core.password

object Password {

    private const val MIN_LENGTH = 6
    private const val MAX_LENGTH = 24

    fun isValid(value: String): Boolean {
        return value.length in MIN_LENGTH..MAX_LENGTH
    }

    sealed class Result {
        data object Success : Result()
        data object Incorrect : Result()
        data object Error : Result()
    }
}