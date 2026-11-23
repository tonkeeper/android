package com.tonapps.wallet.data.attribution

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InstallAttributionReporterTest {

    private var isInstallReported = false
    private var sentEvents = 0

    @Test
    fun aFreshInstallIsReportedOnce() {
        val reporter = reporter(installedAt = NOW - HOUR)

        reporter.reportOnce(::send)
        reporter.reportOnce(::send)

        assertEquals(1, sentEvents)
    }

    @Test
    fun anInstallReportedOnAnEarlierLaunchIsNotReportedAgain() {
        isInstallReported = true

        reporter(installedAt = NOW - HOUR).reportOnce(::send)

        assertEquals(0, sentEvents)
    }

    @Test
    fun aLaunchClosedBeforeTheAnswerLeavesTheReportToTheNextLaunch() {
        reporter(installedAt = NOW - HOUR)

        assertFalse(isInstallReported)

        reporter(installedAt = NOW - HOUR).reportOnce(::send)

        assertEquals(1, sentEvents)
        assertTrue(isInstallReported)
    }

    @Test
    fun anInstallSevenDaysOldIsStillReported() {
        reporter(installedAt = NOW - SEVEN_DAYS).reportOnce(::send)

        assertEquals(1, sentEvents)
    }

    @Test
    fun anInstallOlderThanSevenDaysIsNeverReported() {
        val reporter = reporter(installedAt = NOW - SEVEN_DAYS - 1)

        reporter.reportOnce(::send)
        reporter.reportOnce(::send)

        assertEquals(0, sentEvents)
        assertTrue(isInstallReported)
    }

    @Test
    fun anInstallOfUnknownAgeIsNotReported() {
        reporter(installedAt = null).reportOnce(::send)

        assertEquals(0, sentEvents)
        assertFalse(isInstallReported)
    }

    private fun reporter(installedAt: Long?) = InstallAttributionReporter(
        markInstallReported = { !isInstallReported.also { isInstallReported = true } },
        firstInstallTime = { installedAt },
        currentTime = { NOW },
    )

    private fun send() {
        sentEvents++
    }

    private companion object {
        const val NOW = 1_794_500_000_000L
        const val HOUR = 60L * 60 * 1000
        const val SEVEN_DAYS = 7L * 24 * HOUR
    }
}
