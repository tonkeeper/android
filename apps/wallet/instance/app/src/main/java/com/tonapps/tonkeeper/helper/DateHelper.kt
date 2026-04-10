package com.tonapps.tonkeeper.helper

import android.content.Context
import com.tonapps.extensions.currentTimeSeconds
import com.tonapps.tonkeeper.extensions.capitalized
import com.tonapps.wallet.localization.Localization
import kotlinx.datetime.DateTimePeriod
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.number
import kotlinx.datetime.plus
import kotlinx.datetime.toJavaLocalDateTime
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.util.Date
import java.util.Locale
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

object DateHelper {

    private val currentTz = TimeZone.currentSystemDefault()

    @OptIn(ExperimentalTime::class)
    fun formatTransactionDetailsTime(date: Long, locale: Locale): String {
        if (0 >= date) {
            return ""
        }
        val instant = Instant.fromEpochMilliseconds(date)
        return formatTransactionDetailsTime(instant, locale)
    }

    @OptIn(ExperimentalTime::class)
    fun formatTransactionDetailsTime(date: Instant, locale: Locale): String {
        val shortMonth = formatDate(date, "MMM", locale).replace(".", "") + ","
        val month = if (locale.language == "en") shortMonth.capitalized else shortMonth
        val time = formatDate(date, "HH:mm", locale)
        val day = formatDate(date, "d", locale)
        val year = formatDate(date, "yyyy", locale)
        return if (isThisYear(date)) {
            "$day $month $time"
        } else {
            "$day $month $year, $time"
        }
    }

    @OptIn(ExperimentalTime::class)
    fun untilDate(timestamp: Long = currentTimeSeconds(), locale: Locale): String {
        val startInstant = Instant.fromEpochSeconds(timestamp)

        val oneYearLater = startInstant.plus(
            DateTimePeriod(years = 1),
            currentTz
        )

        return formatDate(oneYearLater, "dd MMM yyyy", locale)
    }

    @OptIn(ExperimentalTime::class)
    fun timestampToDateString(timestamp: Long, locale: Locale): String {
        val date = Instant.fromEpochSeconds(timestamp)
        return formatDate(date, "yyyy-MM-dd", locale)
    }

    @OptIn(ExperimentalTime::class)
    fun formatTransactionTime(date: Long, locale: Locale): String {
        if (0 >= date) {
            return ""
        }
        val instant = Instant.fromEpochMilliseconds(date)
        return formatTransactionTime(instant, locale)
    }

    @OptIn(ExperimentalTime::class)
    fun formatChartTime(epochSeconds: Long, locale: Locale, hasHHmm: Boolean): String {
        if (0 >= epochSeconds) {
            return ""
        }
        val instant = Instant.fromEpochSeconds(epochSeconds)
        val shortMonth = formatDate(instant, "MMM", locale).replace(".", "") + ","
        val month = if (locale.language == "en") shortMonth.capitalized else shortMonth
        val time = formatDate(instant, "HH:mm", locale)
        val day = formatDate(instant, "d", locale)
        return if (hasHHmm) {
            "$day $month $time"
        } else {
            "$day $month".replace(",", "")
        }
    }

    @OptIn(ExperimentalTime::class)
    fun formatTransactionTime(date: Instant, locale: Locale): String {
        val shortMonth = formatDate(date, "MMM", locale).replace(".", "") + ","
        val month = if (locale.language == "en") shortMonth.capitalized else shortMonth
        val time = formatDate(date, "HH:mm", locale)
        val day = formatDate(date, "d", locale)
        return if (isThisMonth(date)) {
            time
        } else {
            "$day $month $time"
        }
    }

    @OptIn(ExperimentalTime::class)
    fun formatTransactionsGroupDate(context: Context, timestamp: Long, locale: Locale): String {
        val date = Instant.fromEpochMilliseconds(timestamp)
        return when {
            isToday(date) -> context.getString(Localization.today)
            isYesterday(date) -> context.getString(Localization.yesterday)
            isThisMonth(date) -> formatDate(date, "d MMMM", locale, true)
            isThisYear(date) -> formatDate(date, "MMMM", locale).capitalized
            else -> formatDate(date, "MMMM yyyy", locale).capitalized
        }
    }

    @OptIn(ExperimentalTime::class)
    fun isToday(timestamp: Long): Boolean {
        val today = Clock.System.todayIn(currentTz)
        return Instant.fromEpochSeconds(timestamp)
            .toLocalDateTime(currentTz).date == today
    }

    @OptIn(ExperimentalTime::class)
    fun isToday(date: Instant): Boolean {
        val today = Clock.System.todayIn(currentTz)
        return date.toLocalDateTime(currentTz).date == today
    }

    @OptIn(ExperimentalTime::class)
    fun isYesterday(date: Instant): Boolean {
        val yesterday = Clock.System.todayIn(currentTz).minus(1, DateTimeUnit.DAY)
        return date.toLocalDateTime(currentTz).date == yesterday
    }

    @OptIn(ExperimentalTime::class)
    fun isThisYear(date: Instant): Boolean {
        val nowYear = Clock.System.now().toLocalDateTime(currentTz).year
        val dateYear = date.toLocalDateTime(currentTz).year
        return nowYear == dateYear
    }

    @OptIn(ExperimentalTime::class)
    fun isThisMonth(date: Instant): Boolean {
        val now = Clock.System.now().toLocalDateTime(currentTz)
        val other = date.toLocalDateTime(currentTz)
        return now.year == other.year && now.month.number == other.month.number
    }

    private fun createModernFormatter(pattern: String, locale: Locale): DateTimeFormatter {
        return DateTimeFormatterBuilder()
            .appendPattern(pattern)
            .toFormatter(locale)
    }

    @OptIn(ExperimentalTime::class)
    private fun formatModernDate(instant: Instant, formatString: String, locale: Locale): String {
        val formatter = createModernFormatter(formatString, locale)
        val zonedDateTime = instant.toLocalDateTime(currentTz).toJavaLocalDateTime()
        return formatter.format(zonedDateTime)
    }

    @OptIn(ExperimentalTime::class)
    fun formatDate(
        instant: Instant,
        formatString: String,
        locale: Locale,
        declensionMonth: Boolean = false
    ): String {
        val date = Date(instant.toEpochMilliseconds())
        if (declensionMonth) {
            val sdf = android.icu.text.SimpleDateFormat(formatString, locale)
            return sdf.format(date)
        } else {
            return formatModernDate(instant, formatString, locale)
        }
    }

    @OptIn(ExperimentalTime::class)
    fun formattedDate(unixTimestamp: Long, locale: Locale): String {
        if (0 >= unixTimestamp) {
            return ""
        }
        val formatString = "d MMM, HH:mm"
        val instant = Instant.fromEpochMilliseconds(unixTimestamp * 1000)
        return formatDate(instant, formatString, locale)
    }

    @OptIn(ExperimentalTime::class)
    fun formatCycleEnd(timestamp: Long): String {
        val now = Clock.System.now()
        var estimateInstant = Instant.fromEpochSeconds(timestamp)
        if (estimateInstant < now) {
            estimateInstant = now
        }

        val duration = estimateInstant - now
        val hours = duration.inWholeHours
        val minutes = (duration - hours.hours).inWholeMinutes
        val seconds = (duration - hours.hours - minutes.minutes).inWholeSeconds

        return String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }

}
