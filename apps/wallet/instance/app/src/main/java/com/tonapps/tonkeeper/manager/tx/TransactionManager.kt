package com.tonapps.tonkeeper.manager.tx

import com.tonapps.blockchain.ton.extensions.base64
import com.tonapps.extensions.join
import com.tonapps.wallet.api.API
import com.tonapps.wallet.api.SendBlockchainState
import com.tonapps.wallet.api.entity.AccountEventEntity
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.account.entities.WalletEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.timeout
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.ton.cell.Cell
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class TransactionManager(
    private val accountRepository: AccountRepository,
    private val api: API
) {

    private val eventFlowMap = ConcurrentHashMap<String, Flow<AccountEventEntity>>()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val _sentTransactionFlow = MutableSharedFlow<PendingTransaction>(
        replay = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    private val sentTransactionFlow = _sentTransactionFlow.shareIn(scope, SharingStarted.Eagerly, 1)

    fun getEventsFlow(
        wallet: WalletEntity
    ): Flow<AccountEventEntity> {
        val key = eventFlowMapKey(wallet)
        return eventFlowMap.getOrPut(key) {
            createEventsFlow(wallet)
        }
    }

    private fun createEventsFlow(
        wallet: WalletEntity
    ) = join(
        pendingEventsFlow(wallet),
        realtime(wallet)
    ).distinctUntilChanged { old, new ->
        old.pending == new.pending && old.lt == new.lt
    }.flowOn(Dispatchers.IO).shareIn(scope, SharingStarted.Eagerly, 1)

    private fun realtime(wallet: WalletEntity) = api.realtime(
        accountId = wallet.accountId,
        testnet = wallet.testnet
    ).map {
        TransactionEvent(it.json)
    }.flatMapLatest {
        transactionFlow(wallet, it.hash)
    }

    private fun pendingEventsFlow(
        wallet: WalletEntity
    ): Flow<AccountEventEntity> = sentTransactionFlow.filter {
        it.wallet.id == wallet.id
    }.flatMapLatest { transactionFlow(it.wallet, it.hash) }

    @OptIn(FlowPreview::class)
    private fun transactionFlow(
        wallet: WalletEntity,
        hash: String,
    ): Flow<AccountEventEntity> = flow {
        val initialTx = getTransaction(wallet, hash) ?: return@flow
        emit(initialTx)

        if (initialTx.pending) {
            delay(20.seconds)
            while (currentCoroutineContext().isActive) {
                val finalTx = getTransaction(wallet, hash)
                if (finalTx == null || finalTx.pending) {
                    delay(5.seconds)
                    continue
                }
                finalTx.addEventId(hash)
                finalTx.addEventId(initialTx.body.eventId)
                emit(finalTx)
                break
            }
        }
    }.timeout(2.minutes)

    private suspend fun getTransaction(
        wallet: WalletEntity,
        hash: String
    ): AccountEventEntity? = withContext(Dispatchers.IO) {
        api.getTransactionByHash(wallet.accountId, wallet.testnet, hash)
    }

    private suspend fun sendWithBattery(
        wallet: WalletEntity,
        boc: String
    ): SendBlockchainState {
        val tonProofToken = accountRepository.requestTonProofToken(wallet) ?: return SendBlockchainState.UNKNOWN_ERROR
        return api.sendToBlockchainWithBattery(boc, tonProofToken, wallet.testnet)
    }

    suspend fun send(
        wallet: WalletEntity,
        boc: String,
        withBattery: Boolean
    ) = send(wallet, boc, withBattery, 0)

    private suspend fun send(
        wallet: WalletEntity,
        boc: String,
        withBattery: Boolean,
        attempt: Int
    ): SendBlockchainState {
        val state = if (withBattery) {
            sendWithBattery(wallet, boc)
        } else {
            api.sendToBlockchain(boc, wallet.testnet)
        }
        if (state == SendBlockchainState.SUCCESS) {
            _sentTransactionFlow.tryEmit(PendingTransaction(wallet.copy(), boc))
            return state
        }

        return if (attempt > 3) {
            state
        } else {
            delay(2.seconds)
            send(wallet, boc, withBattery, attempt + 1)
        }
    }

    suspend fun send(
        wallet: WalletEntity,
        boc: Cell,
        withBattery: Boolean
    ) = send(wallet, boc.base64(), withBattery)

    private fun eventFlowMapKey(wallet: WalletEntity): String {
        if (wallet.testnet) {
            return wallet.accountId + "_testnet"
        }
        return wallet.accountId
    }
}