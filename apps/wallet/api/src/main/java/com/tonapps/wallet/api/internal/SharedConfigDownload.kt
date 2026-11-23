package com.tonapps.wallet.api.internal

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlin.time.ComparableTimeMark
import kotlin.time.Duration
import kotlin.time.TimeSource

internal class SharedConfigDownload(
    private val scope: CoroutineScope,
    private val retryDelay: Duration,
    private val timeSource: TimeSource.WithComparableMarks = TimeSource.Monotonic,
    private val download: suspend () -> Boolean,
) {

    private val lock = Any()

    private var inFlight: Deferred<Boolean>? = null

    private var hasDownloaded = false

    private var lastFailure: ComparableTimeMark? = null

    suspend fun refresh(): Boolean = synchronized(lock) { start() }.await()

    suspend fun awaitDownloaded(): Boolean {
        var awaited: Deferred<Boolean>? = null
        while (true) {
            val pending = synchronized(lock) {
                if (hasDownloaded) {
                    return true
                }
                inFlight?.takeIf { it.isActive } ?: run {
                    if (awaited != null || lastFailure?.let { it.elapsedNow() < retryDelay } == true) {
                        return false
                    }
                    start()
                }
            }
            if (pending.await()) {
                return true
            }
            awaited = pending
        }
    }

    private fun start(): Deferred<Boolean> {
        val started = scope.async {
            var downloaded = false
            try {
                downloaded = download()
            } finally {
                synchronized(lock) {
                    hasDownloaded = hasDownloaded || downloaded
                    lastFailure = if (downloaded) {
                        null
                    } else {
                        timeSource.markNow()
                    }
                }
            }
            downloaded
        }
        inFlight = started
        return started
    }
}
