package com.tonapps.wallet.data.events

import android.content.Context
import com.tonapps.blockchain.ton.TonNetwork
import com.tonapps.wallet.api.API
import com.tonapps.blockchain.model.legacy.TokenEntity
import com.tonapps.blockchain.model.legacy.BlockchainAddress
import com.tonapps.wallet.api.entity.value.Timestamp
import com.tonapps.wallet.api.tron.entity.TronEventEntity
import com.tonapps.wallet.data.collectibles.CollectiblesRepository
import com.tonapps.wallet.data.events.entities.LatestRecipientEntity
import com.tonapps.wallet.data.events.source.LocalDataSource
import com.tonapps.wallet.data.events.source.RemoteDataSource
import com.tonapps.wallet.data.events.tx.TxActionMapper
import com.tonapps.wallet.data.events.tx.TxFetchQuery
import com.tonapps.wallet.data.events.tx.TxPage
import com.tonapps.wallet.data.events.tx.db.TxDatabase
import com.tonapps.wallet.data.rates.RatesRepository
import io.tonapi.models.AccountEvent
import io.tonapi.models.AccountEvents
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlin.collections.emptyList

class EventsRepository(
    scope: CoroutineScope,
    context: Context,
    private val api: API,
    private val collectiblesRepository: CollectiblesRepository,
    private val ratesRepository: RatesRepository
) {

    private val txDatabase = TxDatabase.instance(context)

    private val localDataSource: LocalDataSource by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        LocalDataSource(scope, context)
    }

    private val txActionMapper = TxActionMapper(collectiblesRepository, ratesRepository, api)
    private val remoteDataSource = RemoteDataSource(api, txActionMapper)

    val decryptedCommentFlow: Flow<Map<String, String>>
        get() = localDataSource.decryptedCommentFlow

    private val _hiddenTxIdsFlow = MutableStateFlow<Set<String>>(emptySet())
    val hiddenTxIdsFlow = _hiddenTxIdsFlow.stateIn(scope, SharingStarted.WhileSubscribed(), emptySet())

    fun getDecryptedComment(txId: String) = localDataSource.getDecryptedComment(txId)

    fun saveDecryptedComment(txId: String, comment: String) {
        localDataSource.saveDecryptedComment(txId, comment)
    }

    suspend fun fetch(query: TxFetchQuery): TxPage {
        val events = remoteDataSource.events(query)
        return TxPage(
            source = TxPage.Source.REMOTE,
            events = events,
            beforeTimestamp = query.beforeTimestamp,
            afterTimestamp = query.afterTimestamp,
            limit = query.limit
        )
    }

    suspend fun tronLatestSentTransactions(
        tronWalletAddress: String, tonProofToken: String
    ): List<TronEventEntity> {
        val events = loadTronEvents(tronWalletAddress, tonProofToken) ?: return emptyList()

        val sentTransactions =
            events.filter { it.from == tronWalletAddress && it.to != tronWalletAddress }
                .distinctBy { it.to }

        return sentTransactions.take(6)
    }

    fun latestRecipientsFlow(accountId: String, network: TonNetwork) = flow {
        localDataSource.getLatestRecipients(cacheLatestRecipientsKey(accountId, network))?.let {
            emit(it)
        }

        val remote = loadLatestRecipients(accountId, network)
        emit(remote)
    }.flowOn(Dispatchers.IO)

    private fun loadLatestRecipients(accountId: String, network: TonNetwork): List<LatestRecipientEntity> {
        val list = remoteDataSource.getLatestRecipients(accountId, network)
        localDataSource.setLatestRecipients(cacheLatestRecipientsKey(accountId, network), list)
        return list
    }

    suspend fun getSingle(eventId: String, network: TonNetwork) = remoteDataSource.getSingle(eventId, network)

    suspend fun loadForToken(
        tokenAddress: String,
        accountId: String,
        network: TonNetwork,
        beforeLt: Long? = null
    ): AccountEvents? = withContext(Dispatchers.IO) {
        if (tokenAddress == TokenEntity.TON.address) {
            getRemote(accountId, network, beforeLt)
        } else {
            try {
                api.getTokenEvents(tokenAddress, accountId, network, beforeLt)
            } catch (e: Throwable) {
                null
            }
        }
    }

    suspend fun loadTronEvents(
        tronWalletAddress: String,
        tonProofToken: String,
        maxTimestamp: Long? = null,
        limit: Int = 30
    ) = withContext(Dispatchers.IO) {
        try {
            val events = api.tron.getTronHistory(tronWalletAddress, tonProofToken, limit, maxTimestamp?.let { Timestamp.from(it) })

            if (maxTimestamp == null) {
                localDataSource.setTronEvents(tronWalletAddress, events)
            }

            events
        } catch (e: Throwable) {
            null
        }
    }

    suspend fun get(
        accountId: String,
        network: TonNetwork
    ) = getLocal(accountId, network) ?: getRemote(accountId, network)

    suspend fun getRemote(
        accountId: String,
        network: TonNetwork,
        beforeLt: Long? = null,
        limit: Int = 10
    ): AccountEvents? = withContext(Dispatchers.IO) {
        try {
            val accountEvents = if (beforeLt != null) {
                remoteDataSource.get(accountId, network, beforeLt, limit)
            } else {
                val events = remoteDataSource.get(accountId, network, null, limit)?.also {
                    localDataSource.setEvents(cacheEventsKey(accountId, network), it)
                }
                events
            } ?: return@withContext null

            localDataSource.addSpam(accountId, network.isTestnet, accountEvents.events.filter {
                it.isScam
            })

            accountEvents
        } catch (e: Throwable) {
            null
        }
    }

    suspend fun getLocalSpam(accountId: String, network: TonNetwork) = withContext(Dispatchers.IO) {
        localDataSource.getSpam(accountId, network.isTestnet)
    }

    suspend fun markAsSpam(
        accountId: String,
        network: TonNetwork,
        eventId: String,
    ) = withContext(Dispatchers.IO) {
        val events = getSingle(eventId, network) ?: return@withContext
        localDataSource.addSpam(accountId, network.isTestnet, events)
        _hiddenTxIdsFlow.update {
            it.plus(eventId)
        }
    }

    suspend fun removeSpam(
        accountId: String,
        network: TonNetwork,
        eventId: String,
    ) = withContext(Dispatchers.IO) {
        localDataSource.removeSpam(accountId, network.isTestnet, eventId)
        _hiddenTxIdsFlow.update {
            it.minus(eventId)
        }
    }

    suspend fun getRemoteSpam(
        accountId: String,
        network: TonNetwork,
        startBeforeLt: Long? = null
    ) = withContext(Dispatchers.IO) {
        val list = mutableListOf<AccountEvent>()
        var beforeLt: Long? = startBeforeLt
        for (i in 0 until 10) {
            val events = remoteDataSource.get(
                accountId = accountId,
                network = network,
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
        localDataSource.addSpam(accountId, network.isTestnet, spamList)
        spamList
    }

    suspend fun getLocal(
        accountId: String,
        network: TonNetwork
    ): AccountEvents? = withContext(Dispatchers.IO) {
        localDataSource.getEvents(cacheEventsKey(accountId, network))
    }

    private fun cacheEventsKey(accountId: String, network: TonNetwork): String {
        return "${accountId}_${network.name.lowercase()}"
    }

    private fun cacheLatestRecipientsKey(accountId: String, network: TonNetwork): String {
        return "${accountId}_${network.name.lowercase()}"
    }

}
