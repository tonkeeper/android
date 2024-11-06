package com.tonapps.tonkeeper.manager.tx

import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.tonapps.blockchain.ton.extensions.base64
import com.tonapps.extensions.MutableEffectFlow
import com.tonapps.extensions.join
import com.tonapps.tonkeeper.App
import com.tonapps.tonkeeper.worker.WidgetUpdaterWorker
import com.tonapps.wallet.api.API
import com.tonapps.wallet.api.SendBlockchainState
import com.tonapps.wallet.api.entity.AccountEventEntity
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.account.entities.WalletEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.withContext
import org.ton.cell.Cell
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class TransactionManager(
    private val accountRepository: AccountRepository,
    private val api: API
) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _sendingTransactionFlow = MutableSharedFlow<SendingTransaction>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private val sendingTransactionFlow = _sendingTransactionFlow.asSharedFlow()

    private val _transactionFlow = MutableSharedFlow<AccountEventEntity>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private val transactionFlow = _transactionFlow.asSharedFlow()

    init {
        sendingTransactionFlow.mapNotNull { getTransaction(it.wallet, it.hash) }.onEach { transaction ->
            _transactionFlow.tryEmit(transaction)
        }.launchIn(scope)

        accountRepository.selectedWalletFlow.flatMapLatest { wallet ->
            realtime(wallet)
        }.filterNotNull().onEach { transaction ->
            _transactionFlow.tryEmit(transaction)
        }.launchIn(scope)

        sendingTransactionFlow.onEach {
            delay(5000)
            WidgetUpdaterWorker.update(App.instance)
        }.launchIn(scope)
    }

    fun eventsFlow(wallet: WalletEntity) = transactionFlow.filter {
        it.accountId == wallet.accountId && it.testnet == wallet.testnet
    }

    private fun realtime(wallet: WalletEntity) = api.realtime(
        accountId = wallet.accountId,
        testnet = wallet.testnet,
        onFailure = null
    ).map { it.data }.map { getTransaction(wallet, it) }

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
            _sendingTransactionFlow.tryEmit(SendingTransaction(wallet.copy(), boc))
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
}