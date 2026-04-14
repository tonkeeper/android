package com.tonapps.network.backoff

import kotlin.math.min
import kotlin.random.Random

/**
 * Generates exponential backoff delays with jitter for retry logic.
 *
 * @param minDelayMs Minimum delay in milliseconds
 * @param maxDelayMs Maximum delay in milliseconds
 * @param multiplier Multiplier for exponential growth
 */
class ExponentialBackoff(
    private val minDelayMs: Long = 100,
    private val maxDelayMs: Long = 32000,
    private val multiplier: Double = 2.0
) {
    init {
        require(minDelayMs > 0) { "minDelayMs must be positive" }
        require(maxDelayMs >= minDelayMs) { "maxDelayMs must be >= minDelayMs" }
        require(multiplier > 1.0) { "multiplier must be > 1.0" }
    }

    /**
     * Calculate the backoff delay for the given attempt number.
     * Adds jitter to prevent thundering herd problem.
     *
     * @param attempt The attempt number (0-indexed)
     * @return Delay in milliseconds
     */
    fun getDelayMs(attempt: Int): Long {
        require(attempt >= 0) { "attempt must be non-negative" }

        val exponentialDelay = minDelayMs * Math.pow(multiplier, attempt.toDouble())
        val cappedDelay = min(exponentialDelay.toLong(), maxDelayMs)

        // Add jitter (±25% randomization)
        val jitterRange = (cappedDelay * 0.25).toLong()
        val jitter = Random.Default.nextLong(-jitterRange, jitterRange + 1)

        return (cappedDelay + jitter).coerceAtLeast(minDelayMs)
    }
}