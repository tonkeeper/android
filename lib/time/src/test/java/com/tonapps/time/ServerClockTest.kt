package com.tonapps.time

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class ServerClockTest {

    private var elapsed = 0L
    private var device = DEVICE_TIME
    private val clock = ServerClock({ elapsed }, { device })

    // The server time at boot: the server time is this plus elapsed.
    private val serverBoot = SERVER_TIME + 123

    private val serverTime: Long
        get() = serverBoot + elapsed

    @Test
    fun beforeAnyDateTheDeviceTimeIsUsed() {
        assertEquals(DEVICE_TIME, clock.currentTimeMillis())
        assertFalse(clock.isSynced)
        assertNull(clock.bootMillis())
    }

    @Test
    fun aDateSetsTheServerTime() {
        elapsed = 50_000
        receiveDate()

        assertTrue(clock.isSynced)
        assertEquals(singleDateTime(), clock.currentTimeMillis())
        assertEquals(singleDateTime() - elapsed, clock.bootMillis())
    }

    @Test
    fun theTimeTicksOnItsOwnWhateverTheDeviceClockSays() {
        elapsed = 50_000
        receiveDate()
        val synced = clock.currentTimeMillis()

        elapsed += 60_000
        device -= HOUR_MILLIS

        assertEquals(synced + 60_000, clock.currentTimeMillis())
    }

    @Test
    fun theBootTimeIgnoresTheDeviceClock() {
        elapsed = 50_000
        receiveDate()
        val bootMillis = clock.bootMillis()

        elapsed += 60_000
        device += HOUR_MILLIS

        assertEquals(bootMillis, clock.bootMillis())
    }

    @Test
    fun datesNarrowTheRange() {
        // Each date lands at another fraction of a second, so together they pin the time down.
        repeat(10) {
            elapsed += 10_137
            receiveDate()
        }

        assertNear(serverTime, clock.currentTimeMillis(), 150)
    }

    @Test
    fun aDateOutsideTheRangeStartsOver() {
        repeat(10) {
            elapsed += 10_137
            receiveDate()
        }

        // The server clock was corrected, or ours drifted.
        val shift = 5_000L
        elapsed += 10_000
        receiveDate(serverTime + shift)

        assertEquals((serverTime + shift).wholeSeconds() + 500, clock.currentTimeMillis())
    }

    @Test
    fun aRestoredBootTimeIsUsedUntilADate() {
        elapsed = 50_000
        clock.restore(serverBoot)

        assertFalse(clock.isSynced)
        assertEquals(serverTime, clock.currentTimeMillis())

        // A device clock set while the app was not running does not move it.
        elapsed += 60_000
        device -= HOUR_MILLIS
        assertEquals(serverTime, clock.currentTimeMillis())
    }

    @Test
    fun aDateReplacesARestoredOffsetEvenWhenItIsInRange() {
        elapsed = 50_000
        // Close to the server time, but unconfirmed: it must not narrow the date's range.
        clock.restore(serverBoot + 400)
        receiveDate()

        assertTrue(clock.isSynced)
        assertEquals(singleDateTime(), clock.currentTimeMillis())
    }

    @Test
    fun aRestoreDoesNotOverrideADate() {
        elapsed = 50_000
        receiveDate()
        val synced = clock.currentTimeMillis()

        clock.restore(serverBoot + HOUR_MILLIS)

        assertTrue(clock.isSynced)
        assertEquals(synced, clock.currentTimeMillis())
    }

    /** A response to a request sent [RTT_MILLIS] / 2 ago and stamped by the server half way. */
    private fun receiveDate(stampedAt: Long = serverTime) {
        clock.onServerDate(stampedAt.wholeSeconds(), sentAt = elapsed - RTT_MILLIS / 2, receivedAt = elapsed + RTT_MILLIS / 2)
    }

    // A lone date is read at the middle of its second.
    private fun singleDateTime(): Long = serverTime.wholeSeconds() + 500

    private fun Long.wholeSeconds(): Long = this / 1000 * 1000

    private fun assertNear(expected: Long, actual: Long, tolerance: Long) {
        assertTrue("expected $expected ± $tolerance, was $actual", abs(expected - actual) <= tolerance)
    }

    private companion object {
        const val SERVER_TIME = 1_760_000_000_000L

        // An hour and a bit behind.
        const val DEVICE_TIME = SERVER_TIME - 3_723_456L
        const val HOUR_MILLIS = 3_600_000L
        const val RTT_MILLIS = 200L
    }
}
