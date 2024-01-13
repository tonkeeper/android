package com.tonkeeper.helper

import android.icu.text.SimpleDateFormat
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.util.Locale

object DateFormat {

    private val monthWithDateFormat = SimpleDateFormat("d MMMM", Locale.getDefault())

    fun monthWithDate(
        date: Long
    ): String {
        return monthWithDateFormat.format(date * 1000)
    }

    fun isToday(timestamp: Long): Boolean {
        val localDate = Instant.ofEpochSecond(timestamp).atZone(ZoneId.systemDefault()).toLocalDate()
        val today = Instant.now().atZone(ZoneId.systemDefault()).toLocalDate()

        return localDate == today
    }

    fun isYesterday(timestamp: Long): Boolean {
        val localDate = Instant.ofEpochSecond(timestamp).atZone(ZoneId.systemDefault()).toLocalDate()
        val yesterday = Instant.now().atZone(ZoneId.systemDefault()).toLocalDate().minusDays(1)

        return localDate == yesterday
    }

    fun isSameDay(timestamp1: Long, timestamp2: Long): Boolean {
        val localDate1 = Instant.ofEpochSecond(timestamp1).atZone(ZoneId.systemDefault()).toLocalDate()
        val localDate2 = Instant.ofEpochSecond(timestamp2).atZone(ZoneId.systemDefault()).toLocalDate()

        return localDate1 == localDate2
    }

    fun isSameMonth(timestamp1: Long, timestamp2: Long): Boolean {
        val yearMonth1 = YearMonth.from(Instant.ofEpochSecond(timestamp1).atZone(ZoneId.systemDefault()).toLocalDate())
        val yearMonth2 = YearMonth.from(Instant.ofEpochSecond(timestamp2).atZone(ZoneId.systemDefault()).toLocalDate())

        return yearMonth1 == yearMonth2
    }

    fun isSameYear(timestamp1: Long, timestamp2: Long): Boolean {
        val localDate1 = Instant.ofEpochSecond(timestamp1).atZone(ZoneId.systemDefault()).toLocalDate()
        val localDate2 = Instant.ofEpochSecond(timestamp2).atZone(ZoneId.systemDefault()).toLocalDate()

        return localDate1.year == localDate2.year
    }

}