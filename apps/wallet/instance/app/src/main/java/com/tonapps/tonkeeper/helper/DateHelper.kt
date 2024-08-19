package com.tonapps.tonkeeper.helper

import android.content.Context
import android.icu.text.SimpleDateFormat
import com.tonapps.tonkeeper.extensions.capitalized
import com.tonapps.wallet.localization.Localization
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.toJavaInstant
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.util.Calendar
import java.util.Locale

object DateHelper {

    fun formatTransactionDetailsTime(date: Long, locale: Locale): String {
        val instant = Instant.fromEpochMilliseconds(date * 1000)
        return formatTransactionDetailsTime(instant, locale)
    }

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

    fun timestampToDateString(timestamp: Long, locale: Locale): String {
        val date = Instant.fromEpochSeconds(timestamp)
        return formatDate(date, "yyyy-MM-dd", locale)
    }

    fun formatTransactionTime(date: Long, locale: Locale): String {
        val instant = Instant.fromEpochMilliseconds(date * 1000)
        return formatTransactionTime(instant, locale)
    }

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

    fun formatTransactionsGroupDate(context: Context, timestamp: Long, locale: Locale): String {
        val date = Instant.fromEpochMilliseconds(timestamp * 1000)
        return when {
            isToday(date) -> context.getString(Localization.today)
            isYesterday(date) -> context.getString(Localization.yesterday)
            isThisMonth(date) -> formatDate(date, "d MMMM", locale)
            isThisYear(date) -> formatDate(date, "MMMM", locale).capitalized
            else -> formatDate(date, "MMMM yyyy", locale).capitalized
        }
    }

    fun isToday(date: Instant): Boolean {
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        return date.toLocalDateTime(TimeZone.currentSystemDefault()).date == today
    }

    fun isYesterday(date: Instant): Boolean {
        val yesterday = Clock.System.todayIn(TimeZone.currentSystemDefault()).minus(1, DateTimeUnit.DAY)
        return date.toLocalDateTime(TimeZone.currentSystemDefault()).date == yesterday
    }

    fun isThisYear(date: Instant): Boolean {
        val now = Clock.System.now()
        return now.minus(date, DateTimeUnit.YEAR, TimeZone.currentSystemDefault()) < 1
    }

    fun isThisMonth(date: Instant): Boolean {
        val now = Clock.System.now()
        return now.minus(date, DateTimeUnit.MONTH, TimeZone.currentSystemDefault()) < 1
    }

    private fun createFormatter(pattern: String, locale: Locale): DateTimeFormatter {
        return DateTimeFormatterBuilder()
            .appendPattern(pattern)
            .toFormatter(locale)
    }

    fun formatDate(instant: Instant, formatString: String, locale: Locale): String {
        val formatter = createFormatter(formatString, locale)
        val zonedDateTime = instant.toJavaInstant().atZone(java.time.ZoneId.systemDefault())
        return formatter.format(zonedDateTime)
    }

    fun formattedDate(unixTimestamp: Long, locale: Locale): String {
        if (0 >= unixTimestamp) {
            return ""
        }
        val formatString = "d MMM, HH:mm"
        val instant = Instant.fromEpochMilliseconds(unixTimestamp * 1000)
        return formatDate(instant, formatString, locale)
    }

}