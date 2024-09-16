package com.tonapps.wallet.api.entity

import io.tonapi.models.AccountEvent

data class AccountEventEntity(
    val accountId: String,
    val testnet: Boolean,
    val hash: String,
    val body: AccountEvent
) {

    private val additionalEventIds = mutableSetOf<String>()

    val eventIds: List<String>
        get() = (listOf(hash, body.eventId) + additionalEventIds).distinct()

    val pending: Boolean
        get() = body.inProgress

    val lt: Long
        get() = body.lt

    fun addEventId(eventId: String) {
        additionalEventIds.add(eventId)
    }
}