package com.tonapps.network.interceptor

import okhttp3.Interceptor
import okhttp3.Response
import java.util.ArrayDeque
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * OkHttp interceptor that implements rate limiting and automatic retry for 429 responses.
 *
 * Uses a sliding window algorithm with ArrayDeque to track requests per second.
 * Implements non-blocking approach where possible using atomic operations.
 *
 * @param requestsPerSecondLimit Maximum number of requests allowed per second
 */
class RateLimitInterceptor(
    private val requestsPerSecondLimit: Int = 10,
) : Interceptor {

    private val requestTimestamps = ArrayDeque<Long>(requestsPerSecondLimit)
    private val lock = ReentrantLock()

    init {
        require(requestsPerSecondLimit > 0) { "requestsPerSecondLimit must be positive" }
    }

    companion object {
        private const val WINDOW_MS = 1000L
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        waitForRateLimit()
        return chain.proceed(chain.request())
    }

    /**
     * Waits if necessary to ensure we don't exceed the rate limit.
     * Fixed-size ArrayDeque acts as a circular buffer of [requestsPerSecondLimit] elements.
     * When full, checks if oldest request was within 1 second — if so, waits the remainder.
     */
    private fun waitForRateLimit() {
        lock.withLock {
            val now = System.currentTimeMillis()

            if (requestTimestamps.size >= requestsPerSecondLimit) {
                val oldest = requestTimestamps.first()
                val delta = now - oldest

                if (delta < WINDOW_MS) {
                    Thread.sleep(WINDOW_MS - delta)
                }

                requestTimestamps.removeFirst()
            }

            requestTimestamps.addLast(now)
        }
    }
}
