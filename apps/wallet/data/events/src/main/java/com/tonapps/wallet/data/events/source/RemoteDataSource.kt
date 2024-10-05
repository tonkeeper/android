package com.tonapps.wallet.data.events.source

import com.tonapps.wallet.api.API
import io.tonapi.models.AccountEvents

internal class RemoteDataSource(
    private val api: API
) {

    fun get(
        accountId: String,
        testnet: Boolean,
        beforeLt: Long? = null,
        limit: Int = 12
    ): AccountEvents? = api.getEvents(accountId, testnet, beforeLt, limit)

    fun getSingle(eventId: String, testnet: Boolean) = api.getSingleEvent(eventId, testnet)
}