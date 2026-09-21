package com.tonapps.wallet.data.cache

fun StringBuilder.appendPart(value: String) {
    append(value.length)
    append(':')
    append(value)
}

fun StringBuilder.appendNullablePart(value: String?) {
    if (value == null) {
        append("-1:")
    } else {
        appendPart(value)
    }
}
