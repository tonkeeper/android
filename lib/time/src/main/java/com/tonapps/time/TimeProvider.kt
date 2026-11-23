package com.tonapps.time

import kotlin.time.Clock
import kotlin.time.Instant

/**
 * The current UTC time. Unlike [Clock.System], an implementation may correct a device clock
 * that is off (see [ServerTimeProvider]).
 */
fun interface TimeProvider : Clock {

    /** Milliseconds since the Unix epoch. */
    fun currentTimeMillis(): Long

    fun currentTimeSeconds(): Long = currentTimeMillis() / 1000

    override fun now(): Instant = Instant.fromEpochMilliseconds(currentTimeMillis())
}
