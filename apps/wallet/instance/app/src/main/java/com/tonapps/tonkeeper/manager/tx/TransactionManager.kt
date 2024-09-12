package com.tonapps.tonkeeper.manager.tx

import android.util.Log
import com.tonapps.blockchain.ton.extensions.base64
import com.tonapps.blockchain.ton.extensions.parseCell
import com.tonapps.tonkeeper.api.AccountEventWrap
import com.tonapps.wallet.api.API
import com.tonapps.wallet.api.SendBlockchainState
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.account.entities.WalletEntity
import io.tonapi.models.AccountEvent
import io.tonapi.models.MessageConsequences
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
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.cancellable
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.timeout
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.ton.cell.Cell
import org.ton.crypto.hex
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class TransactionManager(
    private val accountRepository: AccountRepository,
    private val api: API
) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val _sentTransactionFlow = MutableSharedFlow<SendTransaction>(
        replay = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    private val sentTransactionFlow = _sentTransactionFlow.shareIn(scope, SharingStarted.Eagerly, 1)

    @OptIn(ExperimentalCoroutinesApi::class)
    fun eventsFlow(
        wallet: WalletEntity
    ): Flow<AccountEventWrap> = sentTransactionFlow.filter {
        it.wallet.id == wallet.id
    }.flatMapLatest { transaction ->
        transactionFlow(transaction.wallet, transaction.hash)
    }.flowOn(Dispatchers.IO).cancellable()

    @OptIn(FlowPreview::class)
    fun transactionFlow(
        wallet: WalletEntity,
        hash: String
    ): Flow<AccountEventWrap> = flow {
        val event = getTransaction(wallet, hash) ?: return@flow
        emit(AccountEventWrap(
            event = event,
            hash = hash
        ))

        while (event.inProgress && currentCoroutineContext().isActive) {
            delay((1..5).random().seconds)
            val doneEvent = getTransaction(wallet, hash)
            if (doneEvent?.inProgress == false) {
                emit(AccountEventWrap(
                    event = doneEvent,
                    hash = hash,
                    previewEventId = event.eventId
                ))
                break
            }
        }
    }.flowOn(Dispatchers.IO).timeout(1.minutes)

    private suspend fun getTransaction(
        wallet: WalletEntity,
        hash: String
    ): AccountEvent? = withContext(Dispatchers.IO) {
        api.getTransactionByHash(wallet.accountId, hash, wallet.testnet)
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
    ): SendBlockchainState {
        val state = if (withBattery) {
            sendWithBattery(wallet, boc)
        } else {
            api.sendToBlockchain(boc, wallet.testnet)
        }
        if (state != SendBlockchainState.SUCCESS) {
            return state
        }

        _sentTransactionFlow.tryEmit(SendTransaction(wallet.copy(), boc))
        return state
    }

    suspend fun send(
        wallet: WalletEntity,
        boc: Cell,
        withBattery: Boolean
    ) = send(wallet, boc.base64(), withBattery)
}