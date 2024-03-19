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
        beforeLt: Long? = null
    ): List<EventEntity> {
        val events = api.getEvents(accountId, testnet, beforeLt).map { EventEntity(it, testnet) }.filter {
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
}