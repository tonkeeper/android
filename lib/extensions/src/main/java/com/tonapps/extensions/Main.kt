package com.tonapps.extensions

import kotlinx.coroutines.delay

suspend fun <R> withRetry(
    times: Int = 5,
    delay: Long = 1000,
    block: () -> R
): R? {
    for (i in 0 until times) {
        try {
            return block()
        } catch (ignored: Throwable) { }
        delay(delay)
    }
    return null
}
