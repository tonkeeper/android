package com.tonapps.wallet.features.events.screens

import android.content.Context
import com.tonapps.wallet.localization.Localization
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatterBuilder
import java.util.Calendar
import java.util.Date
import java.util.Locale

internal object WalletActivityDateHeaderUtils {

    private val monthYearFormatter = SimpleDateFormat("MMMM_yyyy", Locale.US)
    private val dayMonthFormatter = SimpleDateFormat("d_MMMM", Locale.US)

    fun headerKeyCalendar(epochMillis: Long): String {
        try {
            val calendar = Calendar.getInstance()
            calendar.timeInMillis = epochMillis
            val zone = ZoneId.systemDefault()
            val date = Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()
            val today = ZonedDateTime.now(zone).toLocalDate()
            val monthDiff = (today.year - date.year) * 12 + (today.monthValue - date.monthValue)
            return if (monthDiff < 1 || date == today.minusDays(1)) {
                dayMonthFormatter.format(calendar.time)
            } else {
                monthYearFormatter.format(calendar.time)
            }
        } catch (_: Throwable) {
            return "zero"
        }
    }

    fun formatGroupDateLabel(
        context: Context,
        epochMillis: Long,
        locale: Locale,
    ): String {
        val zone = ZoneId.systemDefault()
        val zdt = Instant.ofEpochMilli(epochMillis).atZone(zone)
        val date = zdt.toLocalDate()
        val today = ZonedDateTime.now(zone).toLocalDate()
        return when {
            date == today -> context.getString(Localization.today)
            date == today.minusDays(1) -> context.getString(Localization.yesterday)
            isThisMonth(zdt, ZonedDateTime.now(zone)) -> {
                android.icu.text.SimpleDateFormat("d MMMM", locale).format(Date(epochMillis))
            }
            isThisYear(zdt, ZonedDateTime.now(zone)) -> {
                val raw = DateTimeFormatterBuilder()
                    .appendPattern("MMMM")
                    .toFormatter(locale)
                    .format(date)
                raw.replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
            }
            else -> {
                val raw = DateTimeFormatterBuilder()
                    .appendPattern("MMMM yyyy")
                    .toFormatter(locale)
                    .format(date)
                raw.replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
            }
        }
    }

    private fun isThisYear(zdt: ZonedDateTime, now: ZonedDateTime): Boolean {
        return zdt.year == now.year
    }

    private fun isThisMonth(zdt: ZonedDateTime, now: ZonedDateTime): Boolean {
        return zdt.year == now.year && zdt.month == now.month
    }
}
