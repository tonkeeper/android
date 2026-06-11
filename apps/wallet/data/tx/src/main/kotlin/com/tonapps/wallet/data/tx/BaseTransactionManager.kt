package com.tonapps.wallet.data.tx

import com.tonapps.async.Async
import com.tonapps.extensions.MutableEffectFlow
import com.tonapps.blockchain.ton.TonNetwork
import com.tonapps.wallet.data.tx.model.PendingWrapEvent
import com.tonapps.wallet.api.API
import com.tonapps.wallet.api.withRetry
import com.tonapps.wallet.data.tx.model.PendingHash
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.withContext

open class BaseTransactionManager(
    private val api: API
) {

    val scope = Async.ioScope()

    private val _pendingHashFlow = MutableEffectFlow<PendingHash>()
    private val pendingTxFlow = _pendingHashFlow.mapNotNull(::fetchPendingTx)
        .flowOn(Dispatchers.IO)


    fun addPendingHash(accountId: String, network: TonNetwork, hash: String) {
        _pendingHashFlow.tryEmit(PendingHash(accountId, network, hash))
    }

    private suspend fun fetchTx(
        accountId: String,
        network: TonNetwork,
        hash: String
    ) = withContext(Dispatchers.IO) {
        withRetry {
            api.accounts(network).getAccountEvent(accountId, hash)
        }
    }

    private suspend fun fetchPendingTx(hash: PendingHash): PendingWrapEvent? {
        val event = fetchTx(
            accountId = hash.accountId,
            network = hash.network,
            hash = hash.hash
        ) ?: return null
        return PendingWrapEvent(hash, event)
    }


}