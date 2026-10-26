package com.tonapps.icu

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * One place for the `2 Oct, 07:09` style used by transaction lists, details and charts.
 * The year is added only outside the current one: `10 Oct 2025, 18:55`.
 */
object TransactionDateFormatter {

    /**
     * Output for a transaction at 07:09 today, 2 October this year, and 10 October 2025:
     *
     * | | today | 2 Oct | 10 Oct 2025 |
     * |---|---|---|---|
     * | [Never] | `07:09` | `07:09` | `07:09` |
     * | [Today] | `07:09` | `2 Oct, 07:09` | `10 Oct 2025, 07:09` |
     * | [ThisMonth] | `07:09` | `07:09` | `10 Oct 2025, 07:09` |
     * | [Always] | `6 Oct, 07:09` | `2 Oct, 07:09` | `10 Oct 2025, 07:09` |
     */
    enum class DateFrom {
        Never,
        Today,
        ThisMonth,
        Always,
    }

    fun format(
        epochMillis: Long,
        locale: Locale,
        dateFrom: DateFrom,
        zone: ZoneId = ZoneId.systemDefault(),
    ): String {
        if (epochMillis <= 0) {
            return ""
        }
        return format(Instant.ofEpochMilli(epochMillis).atZone(zone), locale, dateFrom)
    }

    fun format(zdt: ZonedDateTime, locale: Locale, dateFrom: DateFrom): String {
        val now = ZonedDateTime.now(zdt.zone)
        val time = DateTimeFormatter.ofPattern("HH:mm", locale).format(zdt)
        val timeOnly = when (dateFrom) {
            DateFrom.Never -> true
            DateFrom.Today -> zdt.toLocalDate() == now.toLocalDate()
            DateFrom.ThisMonth -> zdt.year == now.year && zdt.month == now.month
            DateFrom.Always -> false
        }
        if (timeOnly) {
            return time
        }
        val dayMonth = dayMonth(zdt, locale)
        return if (zdt.year == now.year) {
            "$dayMonth, $time"
        } else {
            "$dayMonth ${zdt.year}, $time"
        }
    }

    /** `2 Oct` without time, for chart axes. */
    fun dayMonth(epochMillis: Long, locale: Locale, zone: ZoneId = ZoneId.systemDefault()): String {
        if (epochMillis <= 0) {
            return ""
        }
        return dayMonth(Instant.ofEpochMilli(epochMillis).atZone(zone), locale)
    }

    fun dayMonth(zdt: ZonedDateTime, locale: Locale): String {
        val day = DateTimeFormatter.ofPattern("d", locale).format(zdt)
        return "$day ${shortMonth(zdt, locale)}"
    }

    // Some locales abbreviate with a trailing period ("окт.") and English comes out lowercase on
    // older ICU data, so the raw "MMM" output is normalised here.
    private fun shortMonth(zdt: ZonedDateTime, locale: Locale): String {
        val month = DateTimeFormatter.ofPattern("MMM", locale).format(zdt).replace(".", "")
        return if (locale.language == "en") {
            month.replaceFirstChar { it.titlecase(locale) }
        } else {
            month
        }
    }
}
