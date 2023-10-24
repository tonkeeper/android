package com.tonkeeper.helper

import android.icu.text.SimpleDateFormat
import java.util.Locale

object DateFormatHelper {

    private val monthWithDateFormat = SimpleDateFormat("d MMMM", Locale.getDefault())

    fun monthWithDate(
        date: Long
    ): String {
        return monthWithDateFormat.format(date * 1000)
    }

}