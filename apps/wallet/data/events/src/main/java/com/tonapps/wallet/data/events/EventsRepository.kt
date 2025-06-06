package com.tonapps.wallet.data.events

import android.content.Context
import android.util.Log
import com.tonapps.wallet.api.API
import com.tonapps.wallet.api.entity.TokenEntity
import com.tonapps.wallet.api.tron.entity.TronEventEntity
import com.tonapps.wallet.data.events.entities.AccountEventsResult
import com.tonapps.wallet.data.events.entities.LatestRecipientEntity
import com.tonapps.wallet.data.events.source.LocalDataSource
import com.tonapps.wallet.data.events.source.RemoteDataSource
import io.tonapi.models.AccountAddress
import io.tonapi.models.AccountEvent
import io.tonapi.models.AccountEvents
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.cancellable
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
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

    suspend fun tronLatestSentTransactions(
        tronWalletAddress: String, tonProofToken: String
    ): List<TronEventEntity> {
        val events =
            getTronLocal(tronWalletAddress) ?: loadTronEvents(tronWalletAddress, tonProofToken)
            ?: emptyList()

        val sentTransactions =
            events.filter { it.from == tronWalletAddress && it.to != tronWalletAddress }
                .distinctBy { it.to }

        return sentTransactions.take(6)
    }

    fun latestRecipientsFlow(accountId: String, testnet: Boolean) = flow {
        localDataSource.getLatestRecipients(cacheLatestRecipientsKey(accountId, testnet))?.let {
            emit(it)
        }

        val remote = loadLatestRecipients(accountId, testnet)
        emit(remote)
    }.flowOn(Dispatchers.IO)

    private fun loadLatestRecipients(accountId: String, testnet: Boolean): List<LatestRecipientEntity> {
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

    suspend fun loadTronEvents(
        tronWalletAddress: String,
        tonProofToken: String,
        beforeLt: Long? = null,
        limit: Int = 30
    ) = withContext(Dispatchers.IO) {
        try {
            val events = api.tron.getTronHistory(tronWalletAddress, tonProofToken, limit, beforeLt)

            if (beforeLt == null) {
                localDataSource.setTronEvents(tronWalletAddress, events)
            }

            events
        } catch (e: Throwable) {
            Log.d("API", "loadTronEvents: error", e)
            null
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
        limit: Int = 10
    ): AccountEvents? = withContext(Dispatchers.IO) {
        try {
            val accountEvents = if (beforeLt != null) {
                remoteDataSource.get(accountId, testnet, beforeLt, limit)
            } else {
                val events = remoteDataSource.get(accountId, testnet, null, limit)?.also {
                    localDataSource.setEvents(cacheEventsKey(accountId, testnet), it)
                }
                events
            } ?: return@withContext null

            localDataSource.addSpam(accountId, testnet, accountEvents.events.filter {
                it.isScam
            })

            accountEvents
        } catch (e: Throwable) {
            null
        }
    }

    suspend fun getLocalSpam(accountId: String, testnet: Boolean) = withContext(Dispatchers.IO) {
        localDataSource.getSpam(accountId, testnet)
    }

    suspend fun markAsSpam(
        accountId: String,
        testnet: Boolean,
        eventId: String,
    ) = withContext(Dispatchers.IO) {
        val events = getSingle(eventId, testnet) ?: return@withContext
        localDataSource.addSpam(accountId, testnet, events)
    }

    suspend fun removeSpam(
        accountId: String,
        testnet: Boolean,
        eventId: String,
    ) = withContext(Dispatchers.IO) {
        localDataSource.removeSpam(accountId, testnet, eventId)
    }

    suspend fun getRemoteSpam(
        accountId: String,
        testnet: Boolean,
        startBeforeLt: Long? = null
    ) = withContext(Dispatchers.IO) {
        val list = mutableListOf<AccountEvent>()
        var beforeLt: Long? = startBeforeLt
        for (i in 0 until 10) {
            val events = remoteDataSource.get(
                accountId = accountId,
                testnet = testnet,
                beforeLt = beforeLt,
                limit = 50
            )?.events ?: emptyList()

            if (events.isEmpty() || events.size >= 500) {
                break
            }

            list.addAll(events)
            beforeLt = events.lastOrNull()?.lt ?: break
        }
        val spamList = list.filter { it.isScam }
        localDataSource.addSpam(accountId, testnet, spamList)
        spamList
    }

    suspend fun getTronLocal(
        tronWalletAddress: String,
    ): List<TronEventEntity>? = withContext(Dispatchers.IO) {
        localDataSource.getTronEvents(tronWalletAddress)
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