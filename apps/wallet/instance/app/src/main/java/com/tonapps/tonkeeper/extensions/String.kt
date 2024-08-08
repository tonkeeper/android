package com.tonapps.tonkeeper.extensions

import android.text.Spannable
import android.text.SpannableStringBuilder
import android.util.Log

fun String.substringSafe(startIndex: Int, endIndex: Int): String {
    return if (startIndex > length) {
        ""
    } else if (endIndex > length) {
        substring(startIndex)
    } else {
        substring(startIndex, endIndex)
    }
}

val String.capitalized: String
    get() {
        return if (isNotEmpty()) {
            val first = this[0].uppercase()
            if (length > 1) {
                first + substring(1)
            } else {
                first
            }
        } else {
            ""
        }
    }

fun String.formatCompat(vararg args: CharSequence?): Spannable {
    val result = SpannableStringBuilder()
    val regex = "%[0-9]+\\\$s".toRegex()
    val split = this.split(regex)
    for (i in split.indices) {
        result.append(split[i])
        if (i < args.size) {
            result.append(args[i])
        }
    }
    return result
}

fun String.isVersionLowerThan(other: String): Boolean {
    val thisParts = this.split(".")
    val otherParts = other.split(".")

    val length = maxOf(thisParts.size, otherParts.size)

    for (i in 0 until length) {
        val thisPart = thisParts.getOrNull(i)?.toIntOrNull() ?: 0
        val otherPart = otherParts.getOrNull(i)?.toIntOrNull() ?: 0

        if (thisPart != otherPart) {
            return thisPart < otherPart
        }
    }
    return false // Versions are equal
}
