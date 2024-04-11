package com.tonapps.wallet.data.events.source

import com.tonapps.wallet.api.API
import com.tonapps.wallet.data.collectibles.CollectiblesRepository
import com.tonapps.wallet.data.events.entities.EventEntity

internal class RemoteDataSource(
    private val api: API,
    private val collectiblesRepository: CollectiblesRepository
) {

    fun get(
        accountId: String,
        testnet: Boolean,
        beforeLt: Long? = null,
        limit: Int = 20
    ): List<EventEntity> {
        val events = api.getEvents(accountId, testnet, beforeLt, limit).map { EventEntity(it, testnet) }.filter {
            it.actions.isNotEmpty()
        }
        for (event in events) {
            for (action in event.actions) {
                if (action.nftAddress != null) {
                    action.nftEntity = collectiblesRepository.getNft(accountId, testnet, action.nftAddress)
                }
            }
        }
        return events
    }

    fun getSingle(
        accountId: String,
        testnet: Boolean,
        eventId: String
    ): EventEntity {
        val response = api.getEvent(accountId, testnet, eventId)
        val event = EventEntity(response, testnet)
        for (action in event.actions) {
            if (action.nftAddress != null) {
                action.nftEntity = collectiblesRepository.getNft(accountId, testnet, action.nftAddress)
            }
        }
        return event
    }
}