package com.tonkeeper.extensions

fun String.substringSafe(startIndex: Int, endIndex: Int): String {
    return if (startIndex > length) {
        ""
    } else if (endIndex > length) {
        substring(startIndex)
    } else {
        substring(startIndex, endIndex)
    }
}

val String.colorForChange: Int
    get() {
        return when {
            startsWith("-") -> uikit.R.color.accentRed
            startsWith("+") -> uikit.R.color.accentGreen
            else -> uikit.R.color.textSecondary
        }
    }