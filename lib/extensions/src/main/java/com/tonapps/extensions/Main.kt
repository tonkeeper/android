package com.tonapps.extensions

import android.util.Log
import kotlinx.coroutines.delay

suspend fun <R> withRetry(
    times: Int = 5,
    delay: Long = 1000,
    block: () -> R
): R? {
    for (i in 0 until times) {
        try {
            return block()
        } catch (e: Throwable) {
            Log.e("TONKeeperLog", "error request", e)
        }
        delay(delay)
    }
    return null
}
