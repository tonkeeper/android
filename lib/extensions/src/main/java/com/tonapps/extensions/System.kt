package com.tonapps.extensions

fun currentTimeMillis(): Long {
    return System.currentTimeMillis()
}

fun currentTimeSeconds(): Long {
    return currentTimeMillis() / 1000
}
