package com.tonapps.tonkeeper.extensions

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