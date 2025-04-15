package com.tonapps.tonkeeper.manager.tx

import com.tonapps.blockchain.ton.extensions.base64
import com.tonapps.tonkeeper.App
import com.tonapps.tonkeeper.worker.WidgetUpdaterWorker
import com.tonapps.wallet.api.API
import com.tonapps.wallet.api.SendBlockchainState
import com.tonapps.wallet.api.entity.AccountEventEntity
import com.tonapps.wallet.api.entity.ConfigEntity
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.battery.BatteryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext
import org.ton.cell.Cell
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class TransactionManager(
    private val accountRepository: AccountRepository,
    private val api: API,
    private val batteryRepository: BatteryRepository,
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

        combine(
            api.configFlow.filter { !it.empty },
            accountRepository.selectedWalletFlow
        ) { config, wallet ->
            realtime(config, wallet)
        }.flatMapLatest { it }.filterNotNull().onEach { transaction ->
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

    private fun realtime(config: ConfigEntity, wallet: WalletEntity) = api.realtime(
        accountId = wallet.accountId,
        testnet = wallet.testnet,
        config = config,
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
        boc: String,
        source: String,
        confirmationTime: Double,
    ): SendBlockchainState {
        val tonProofToken = accountRepository.requestTonProofToken(wallet) ?: return SendBlockchainState.UNKNOWN_ERROR
        val state = api.sendToBlockchainWithBattery(boc, tonProofToken, wallet.testnet, source, confirmationTime)
        if (state == SendBlockchainState.SUCCESS) {
            batteryRepository.refreshBalanceDelay(
                publicKey = wallet.publicKey,
                tonProofToken = tonProofToken,
                testnet = wallet.testnet,
            )
        }
        return state
    }

    suspend fun send(
        wallet: WalletEntity,
        boc: String,
        withBattery: Boolean,
        source: String,
        confirmationTime: Double,
    ) = send(wallet, boc, withBattery, source, confirmationTime, 0)

    private suspend fun send(
        wallet: WalletEntity,
        boc: String,
        withBattery: Boolean,
        source: String,
        confirmationTime: Double,
        attempt: Int
    ): SendBlockchainState {
        val state = if (withBattery) {
            sendWithBattery(wallet, boc, source, confirmationTime)
        } else {
            api.sendToBlockchain(boc, wallet.testnet, source, confirmationTime)
        }

        if (state == SendBlockchainState.SUCCESS) {
            _sendingTransactionFlow.tryEmit(SendingTransaction(wallet.copy(), boc))
            return state
        }

        return if (attempt > 3) {
            state
        } else {
            delay(5.seconds)
            send(wallet, boc, withBattery, source, confirmationTime, attempt + 1)
        }
    }

    suspend fun send(
        wallet: WalletEntity,
        boc: Cell,
        withBattery: Boolean,
        source: String,
        confirmationTime: Double,
    ) = send(wallet, boc.base64(), withBattery, source, confirmationTime)
}