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

    fun formatTransactionDetailsTime(date: Long): String {
        val instant = Instant.fromEpochMilliseconds(date * 1000)
        return formatTransactionDetailsTime(instant)
    }

    fun formatTransactionDetailsTime(date: Instant): String {
        val locale = Locale.getDefault()
        val shortMonth = formatDate(date, "MMM").replace(".", "") + ","
        val month = if (locale.language == "en") shortMonth.capitalized else shortMonth
        val time = formatDate(date, "HH:mm")
        val day = formatDate(date, "d")
        val year = formatDate(date, "yyyy")
        return if (isThisYear(date)) {
            "$day $month $time"
        } else {
            "$day $month $year, $time"
        }
    }

    fun timestampToDateString(timestamp: Long): String {
        val date = Instant.fromEpochSeconds(timestamp)
        return formatDate(date, "yyyy-MM-dd")
    }

    fun dateToTimestamp(dateString: String): Long {
        val date = LocalDate.parse(dateString)
        return date.atStartOfDayIn(TimeZone.UTC).epochSeconds
    }

    fun formatTransactionTime(date: Long): String {
        val instant = Instant.fromEpochMilliseconds(date * 1000)
        return formatTransactionTime(instant)
    }

    fun formatTransactionTime(date: Instant): String {
        val locale = Locale.getDefault()
        val shortMonth = formatDate(date, "MMM").replace(".", "") + ","
        val month = if (locale.language == "en") shortMonth.capitalized else shortMonth
        val time = formatDate(date, "HH:mm")
        val day = formatDate(date, "d")
        return if (isThisMonth(date)) {
            time
        } else {
            "$day $month $time"
        }
    }

    fun formatTransactionsGroupDate(context: Context, timestamp: Long): String {
        val date = Instant.fromEpochMilliseconds(timestamp * 1000)
        return when {
            isToday(date) -> context.getString(Localization.today)
            isYesterday(date) -> context.getString(Localization.yesterday)
            isThisMonth(date) -> formatDate(date, "d MMMM")
            isThisYear(date) -> formatDate(date, "MMMM").capitalized
            else -> formatDate(date, "MMMM yyyy").capitalized
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

    private fun createFormatter(pattern: String): DateTimeFormatter {
        return DateTimeFormatterBuilder()
            .appendPattern(pattern)
            .toFormatter(Locale.getDefault())
    }

    fun formatDate(instant: Instant, formatString: String): String {
        val formatter = createFormatter(formatString)
        val zonedDateTime = instant.toJavaInstant().atZone(java.time.ZoneId.systemDefault())
        return formatter.format(zonedDateTime)
    }

    fun formattedDate(unixTimestamp: Long): String {
        if (0 >= unixTimestamp) {
            return ""
        }
        val instant = Instant.fromEpochMilliseconds(unixTimestamp * 1000)
        val dateTime = instant.toLocalDateTime(TimeZone.currentSystemDefault())

        val month = dateTime.month.name.take(3)
        val day = dateTime.dayOfMonth
        val hour = dateTime.hour.toString().padStart(2, '0')
        val minute = dateTime.minute.toString().padStart(2, '0')

        return "$month $day, $hour:$minute"
    }

}