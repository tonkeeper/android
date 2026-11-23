package com.tonapps.deposit.multicoin

import kotlinx.coroutines.CancellationException

// Any failure becomes a failed Result; cancellation still propagates.
internal suspend inline fun <T> catchingResult(block: suspend () -> T): Result<T> {
    return runCatching { block() }.onFailure {
        if (it is CancellationException) {
            throw it
        }
    }
}
