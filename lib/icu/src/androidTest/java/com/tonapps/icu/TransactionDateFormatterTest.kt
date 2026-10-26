package com.tonapps.icu

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tonapps.icu.TransactionDateFormatter.DateFrom
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class TransactionDateFormatterTest {

    private val zone = ZoneId.of("UTC")
    private val now = ZonedDateTime.now(zone)
    private val pastYear = ZonedDateTime.of(2020, 10, 10, 14, 30, 0, 0, zone)

    @Test
    fun pastYearAddsTheYear() {
        assertEquals("10 Oct 2020, 14:30", TransactionDateFormatter.format(pastYear, Locale.US, DateFrom.Always))
        assertEquals("10 окт 2020, 14:30", TransactionDateFormatter.format(pastYear, Locale("ru"), DateFrom.Always))
    }

    @Test
    fun todayIsTimeOnlyUnlessAlways() {
        val time = "%02d:%02d".format(now.hour, now.minute)
        assertEquals(time, TransactionDateFormatter.format(now, Locale.US, DateFrom.Never))
        assertEquals(time, TransactionDateFormatter.format(now, Locale.US, DateFrom.Today))
        assertEquals(time, TransactionDateFormatter.format(now, Locale.US, DateFrom.ThisMonth))
        assertEquals("${TransactionDateFormatter.dayMonth(now, Locale.US)}, $time", TransactionDateFormatter.format(now, Locale.US, DateFrom.Always))
    }

    @Test
    fun thresholdsDecideWhenTheDateAppears() {
        assertEquals("14:30", TransactionDateFormatter.format(pastYear, Locale.US, DateFrom.Never))
        assertEquals("10 Oct 2020, 14:30", TransactionDateFormatter.format(pastYear, Locale.US, DateFrom.Today))
        assertEquals("10 Oct 2020, 14:30", TransactionDateFormatter.format(pastYear, Locale.US, DateFrom.ThisMonth))
    }

    @Test
    fun invalidEpochIsEmpty() {
        assertEquals("", TransactionDateFormatter.format(0L, Locale.US, DateFrom.Always))
        assertEquals("", TransactionDateFormatter.dayMonth(-1L, Locale.US))
    }

    @Test
    fun dayMonthHasNoTrailingPeriod() {
        assertEquals("10 Oct", TransactionDateFormatter.dayMonth(pastYear, Locale.US))
        assertEquals("10 окт", TransactionDateFormatter.dayMonth(pastYear, Locale("ru")))
    }
}
