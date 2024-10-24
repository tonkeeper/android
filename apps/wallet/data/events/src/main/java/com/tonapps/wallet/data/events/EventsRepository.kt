package com.tonapps.wallet.data.events

import android.content.Context
import android.util.Log
import com.tonapps.extensions.MutableEffectFlow
import com.tonapps.extensions.prefs
import com.tonapps.wallet.api.API
import com.tonapps.wallet.api.entity.TokenEntity
import com.tonapps.wallet.data.collectibles.CollectiblesRepository
import com.tonapps.wallet.data.core.WalletCurrency
import com.tonapps.wallet.data.events.entities.AccountEventsResult
import com.tonapps.wallet.data.events.entities.EventEntity
import com.tonapps.wallet.data.events.source.LocalDataSource
import com.tonapps.wallet.data.events.source.RemoteDataSource
import com.tonapps.wallet.data.rates.entity.RatesEntity
import io.tonapi.models.AccountAddress
import io.tonapi.models.AccountEvents
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.cancellable
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext

class EventsRepository(
    scope: CoroutineScope,
    context: Context,
    private val api: API
) {

    private val localDataSource: LocalDataSource by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        LocalDataSource(scope, context)
    }

    private val remoteDataSource = RemoteDataSource(api)

    val decryptedCommentFlow = localDataSource.decryptedCommentFlow

    fun getDecryptedComment(txId: String) = localDataSource.getDecryptedComment(txId)

    fun saveDecryptedComment(txId: String, comment: String) {
        localDataSource.saveDecryptedComment(txId, comment)
    }

    fun latestRecipientsFlow(accountId: String, testnet: Boolean) = flow {
        val local = localDataSource.getLatestRecipients(cacheLatestRecipientsKey(accountId, testnet))
        emit(local ?: emptyList())

        val remote = loadLatestRecipients(accountId, testnet)
        emit(remote)
    }.flowOn(Dispatchers.IO)

    private fun loadLatestRecipients(accountId: String, testnet: Boolean): List<AccountAddress> {
        val list = remoteDataSource.getLatestRecipients(accountId, testnet)
        localDataSource.setLatestRecipients(cacheLatestRecipientsKey(accountId, testnet), list)
        return list
    }

    suspend fun getSingle(eventId: String, testnet: Boolean) = remoteDataSource.getSingle(eventId, testnet)

    suspend fun getLast(
        accountId: String,
        testnet: Boolean
    ): AccountEvents? = withContext(Dispatchers.IO) {
        try {
            remoteDataSource.get(accountId, testnet, limit = 2)
        } catch (e: Throwable) {
            null
        }
    }

    suspend fun loadForToken(
        tokenAddress: String,
        accountId: String,
        testnet: Boolean,
        beforeLt: Long? = null
    ): AccountEvents? = withContext(Dispatchers.IO) {
        if (tokenAddress == TokenEntity.TON.address) {
            getRemote(accountId, testnet, beforeLt)
        } else {
            try {
                api.getTokenEvents(tokenAddress, accountId, testnet, beforeLt)
            } catch (e: Throwable) {
                null
            }
        }
    }

    fun getFlow(
        accountId: String,
        testnet: Boolean
    ) = flow {
        try {
            val local = getLocal(accountId, testnet)
            if (local != null && local.events.isNotEmpty()) {
                emit(AccountEventsResult(cache = true, events = local))
            }

            val remote = getRemote(accountId, testnet) ?: return@flow
            emit(AccountEventsResult(cache = false, events = remote))
        } catch (ignored: Throwable) { }
    }.cancellable()

    suspend fun get(
        accountId: String,
        testnet: Boolean
    ) = getLocal(accountId, testnet) ?: getRemote(accountId, testnet)

    suspend fun getRemote(
        accountId: String,
        testnet: Boolean,
        beforeLt: Long? = null,
        limit: Int = 12
    ): AccountEvents? = withContext(Dispatchers.IO) {
        try {
            if (beforeLt != null) {
                remoteDataSource.get(accountId, testnet, beforeLt, limit)
            } else {
                val events = remoteDataSource.get(accountId, testnet)?.also {
                    localDataSource.setEvents(cacheEventsKey(accountId, testnet), it)
                }
                events
            }
        } catch (e: Throwable) {
            null
        }
    }

    suspend fun getLocal(
        accountId: String,
        testnet: Boolean
    ): AccountEvents? = withContext(Dispatchers.IO) {
        localDataSource.getEvents(cacheEventsKey(accountId, testnet))
    }

    private fun cacheEventsKey(accountId: String, testnet: Boolean): String {
        if (!testnet) {
            return accountId
        }
        return "${accountId}_testnet"
    }

    private fun cacheLatestRecipientsKey(accountId: String, testnet: Boolean): String {
        if (!testnet) {
            return accountId
        }
        return "${accountId}_testnet"
    }

}