package com.tonapps.extensions

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

fun currentTimeMillis(): Long {
    return System.currentTimeMillis()
}

fun currentTimeSeconds(): Long {
    return currentTimeMillis() / 1000
}

@OptIn(ExperimentalUuidApi::class)
fun generateUuid(): String {
    return Uuid.generateV4().toString()
}