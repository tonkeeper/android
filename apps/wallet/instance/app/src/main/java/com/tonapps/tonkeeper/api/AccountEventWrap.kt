package com.tonapps.tonkeeper.api

import com.tonapps.wallet.api.entity.AccountEventEntity
import io.tonapi.models.AccountEvent

data class AccountEventWrap(
    val event: AccountEvent,
    val cached: Boolean = false,
    val eventIds: List<String>,
) {

    companion object {

        fun cached(event: AccountEvent): AccountEventWrap {
            return AccountEventWrap(event, cached = true, eventIds = listOf(event.eventId))
        }

        fun cached(event: AccountEventEntity): AccountEventWrap {
            return cached(event.body)
        }
    }

    val eventId: String
        get() = event.eventId

    val timestamp: Long
        get() = if (inProgress) {
            System.currentTimeMillis()
        } else {
            event.timestamp
        }

    val lt: Long
        get() = event.lt

    val inProgress: Boolean
        get() = event.inProgress

    constructor(event: AccountEvent) : this(
        event = event,
        cached = false,
        eventIds = listOf(event.eventId)
    )

    constructor(event: AccountEventEntity) : this(
        event = event.body,
        cached = false,
        eventIds = listOf(event.body.eventId)
    )
}