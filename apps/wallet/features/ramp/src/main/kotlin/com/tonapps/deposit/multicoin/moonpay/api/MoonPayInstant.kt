package com.tonapps.deposit.multicoin.moonpay.api

import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

internal fun parseMoonPayInstantMs(value: String): Long? {
    return try {
        OffsetDateTime.parse(value).toInstant().toEpochMilli()
    } catch (_: DateTimeParseException) {
        null
    }
}
