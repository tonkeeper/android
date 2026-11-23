package com.tonapps.time

import kotlin.math.max
import kotlin.math.min

/**
 * Server UTC time, kept against [elapsedRealtime]: milliseconds since device boot, a clock the
 * user cannot set. So the time ticks on its own and does not follow a change of [deviceTime].
 *
 * Every server `Date` narrows the range the server time at boot lies in, and the time is read at
 * its middle. It is not monotonic: a new date may move it by up to a second either way.
 */
class ServerClock(
    private val elapsedRealtime: () -> Long,
    private val deviceTime: () -> Long,
) : TimeProvider {

    private class BootTime(val from: Long, val to: Long, val synced: Boolean) {

        val millis: Long
            get() = from + (to - from) / 2

        fun intersect(other: BootTime): BootTime? {
            val from = max(from, other.from)
            val to = min(to, other.to)

            if (from > to) {
                return null
            }

            return BootTime(from, to, synced = true)
        }
    }

    private val lock = Any()

    @Volatile
    private var bootTime: BootTime? = null

    /** Whether a server date set the time; before that it is the restored or the device time. */
    val isSynced: Boolean
        get() = bootTime?.synced == true

    override fun currentTimeMillis(): Long {
        val bootTime = bootTime ?: return deviceTime()
        return bootTime.millis + elapsedRealtime()
    }

    /**
     * The server time at boot, or null until a date or [restore]: the time is this plus
     * [elapsedRealtime], so no change of [deviceTime] moves it.
     */
    fun bootMillis(): Long? = bootTime?.millis

    /** Starts from [bootMillis] kept from an earlier launch in this boot, until a server date confirms it. */
    fun restore(bootMillis: Long) {
        synchronized(lock) {
            if (bootTime == null) {
                bootTime = BootTime(bootMillis, bootMillis, synced = false)
            }
        }
    }

    /**
     * Takes the `Date` of a response to a request sent at [sentAt] and answered at [receivedAt],
     * both [elapsedRealtime].
     */
    fun onServerDate(dateMillis: Long, sentAt: Long, receivedAt: Long) {
        // The server stamped the date in between, and the header drops its milliseconds.
        val sample = BootTime(
            from = dateMillis - receivedAt,
            to = dateMillis + DATE_PRECISION_MILLIS - sentAt,
            synced = true,
        )

        synchronized(lock) {
            val current = bootTime?.takeIf { it.synced }
            // A date outside the range means one of the clocks moved: start over from it.
            bootTime = current?.intersect(sample) ?: sample
        }
    }

    private companion object {
        const val DATE_PRECISION_MILLIS = 1000L
    }
}
