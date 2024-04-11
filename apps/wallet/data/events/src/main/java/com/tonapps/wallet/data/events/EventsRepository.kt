package com.tonapps.wallet.data.events

import android.content.Context
import com.tonapps.wallet.api.API
import com.tonapps.wallet.data.collectibles.CollectiblesRepository
import com.tonapps.wallet.data.core.WalletCurrency
import com.tonapps.wallet.data.events.entities.EventEntity
import com.tonapps.wallet.data.events.source.LocalDataSource
import com.tonapps.wallet.data.events.source.RemoteDataSource
import com.tonapps.wallet.data.rates.entity.RatesEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class EventsRepository(
    private val context: Context,
    private val api: API,
    private val collectiblesRepository: CollectiblesRepository
) {

    private val localDataSource = LocalDataSource(context)
    private val remoteDataSource = RemoteDataSource(api, collectiblesRepository)

    suspend fun getSingleRemote(
        accountId: String,
        testnet: Boolean,
        eventId: String
    ): EventEntity = withContext(Dispatchers.IO) {
        remoteDataSource.getSingle(accountId, testnet, eventId)
    }

    suspend fun getRemote(
        accountId: String,
        testnet: Boolean
    ): List<EventEntity> = withContext(Dispatchers.IO) {
        val events = remoteDataSource.get(accountId, testnet)
        localDataSource.setCache(cacheKey(accountId, testnet), events)
        events
    }

    suspend fun getRemoteOffset(
        accountId: String,
        testnet: Boolean,
        beforeLt: Long
    ): List<EventEntity> = withContext(Dispatchers.IO) {
        remoteDataSource.get(accountId, testnet, beforeLt, 100)
    }

    suspend fun getLocal(
        accountId: String,
        testnet: Boolean
    ): List<EventEntity> = withContext(Dispatchers.IO) {
        localDataSource.getCache(cacheKey(accountId, testnet)) ?: emptyList()
    }

    private fun cacheKey(accountId: String, testnet: Boolean): String {
        if (!testnet) {
            return accountId
        }
        return "${accountId}_testnet"
    }
}