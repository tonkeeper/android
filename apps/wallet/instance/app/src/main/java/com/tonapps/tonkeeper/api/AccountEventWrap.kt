package com.tonapps.tonkeeper.api

import io.tonapi.models.AccountEvent

data class AccountEventWrap(
    val event: AccountEvent,
    val cached: Boolean = false,
    val eventIds: List<String>,
) {

    val eventId: String
        get() = event.eventId

    val timestamp: Long
        get() = event.timestamp

    val lt: Long
        get() = event.lt

    val inProgress: Boolean
        get() = event.inProgress
}