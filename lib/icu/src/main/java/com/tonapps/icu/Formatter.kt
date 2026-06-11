package com.tonapps.icu

import kotlin.math.abs

object Formatter {

    private val STRIP_PATTERN = Regex("[+%]")

    fun percent(value: Float): String {
        val format = when {
            value == 0f -> "%.2f%%"
            value > 0f -> "+ %.2f%%"
            else -> "- %.2f%%"
        }
        return format.format(abs(value))
    }

    fun percent(value: String) = percent(value.replace(STRIP_PATTERN, "").toFloatOrNull() ?: 0f)
}