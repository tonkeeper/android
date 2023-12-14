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