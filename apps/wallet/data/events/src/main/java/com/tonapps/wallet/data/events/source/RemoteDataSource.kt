package com.tonapps.wallet.data.events.source

import com.tonapps.blockchain.ton.TonNetwork
import com.tonapps.blockchain.ton.extensions.equalsAddress
import com.tonapps.wallet.api.API
import com.tonapps.blockchain.model.legacy.BlockchainAddress
import com.tonapps.wallet.api.entity.value.Timestamp
import com.tonapps.wallet.data.events.tx.TxActionMapper
import com.tonapps.wallet.data.events.entities.LatestRecipientEntity
import com.tonapps.wallet.data.events.tx.model.TxEvent
import com.tonapps.wallet.data.events.isOutTransfer
import com.tonapps.wallet.data.events.recipient
import com.tonapps.wallet.data.events.tx.TxFetchQuery
import io.tonapi.models.AccountEvents
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

internal class RemoteDataSource(
    private val api: API,
    private val mapper: TxActionMapper,
) {

    suspend fun events(query: TxFetchQuery): List<TxEvent> = coroutineScope {
        val fetchLimit = query.limit
        val tonDeferred = async {
            tonEvents(
                address = query.tonAddress,
                network = query.tonAddress.network,
                beforeTimestamp = query.beforeTimestamp,
                afterTimestamp = query.afterTimestamp,
                limit = fetchLimit
            )
        }

        val tronDeferred = async {
            val address = query.tronAddress ?: return@async emptyList<TxEvent>()
            val tonProof = query.tonProofToken ?: return@async emptyList<TxEvent>()
            tronEvents(address, tonProof, query.beforeTimestamp, query.afterTimestamp, fetchLimit)
        }

        val tonEvents = tonDeferred.await()
        val tronEvents = tronDeferred.await()
        (tonEvents + tronEvents).sortedByDescending { it.timestamp }.take(query.limit)
    }

    suspend fun tonEvents(
        address: BlockchainAddress,
        network: TonNetwork,
        beforeTimestamp: Timestamp?,
        afterTimestamp: Timestamp?,
        limit: Int,
    ): List<TxEvent> {
        val events = api.fetchTonEvents(
            accountId =  address.value,
            network = network,
            beforeTimestamp = beforeTimestamp,
            afterTimestamp = afterTimestamp,
            limit = limit
        )
        return mapper.events(address, events)
    }

    suspend fun tronEvents(
        address: BlockchainAddress,
        tonProofToken: String,
        beforeTimestamp: Timestamp?,
        afterTimestamp: Timestamp?,
        limit: Int
    ): List<TxEvent> {
        val events = api.fetchTronTransactions(
            tronAddress = address.value,
            tonProofToken = tonProofToken,
            beforeTimestamp = beforeTimestamp,
            afterTimestamp = afterTimestamp,
            limit = limit
        )
        return mapper.tronEvents(address, events)
    }

    fun get(
        accountId: String,
        network: TonNetwork,
        beforeLt: Long? = null,
        limit: Int = 12
    ): AccountEvents? = api.getEvents(accountId, network, beforeLt, limit)

    suspend fun getSingle(eventId: String, network: TonNetwork) = api.getSingleEvent(eventId, network)

    fun getLatestRecipients(accountId: String, network: TonNetwork): List<LatestRecipientEntity> {
        val events = api.getEvents(
            accountId = accountId,
            network = network,
            limit = 100
        )?.events ?: return emptyList()

        val recipients = mutableListOf<LatestRecipientEntity>()

        events.forEach { event ->
            event.actions.forEach { action ->
                if (action.isOutTransfer(accountId) && action.recipient != null && action.recipient!!.address != accountId) {
                    recipients.add(
                        LatestRecipientEntity(
                            account = action.recipient!!,
                            timestamp = event.timestamp
                        )
                    )
                }
            }
        }

        return recipients.filter {
            it.account.isWallet && !it.account.address.equalsAddress(accountId)
        }.distinctBy { it.account.address }.take(6)
    }
}