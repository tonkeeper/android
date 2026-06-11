package com.tonapps.wallet.data.tx

import com.tonapps.blockchain.ton.extensions.base64
import com.tonapps.core.flags.WalletFeature
import com.tonapps.extensions.MutableEffectFlow
import com.tonapps.log.L
import com.tonapps.wallet.api.API
import com.tonapps.wallet.api.entity.AccountEventEntity
import com.tonapps.wallet.api.entity.ConfigEntity
import com.tonapps.wallet.api.entity.toncenter.ToncenterEvent
import com.tonapps.wallet.api.entity.toncenter.TransactionsEvent
import io.tonapi.models.AccountAddress
import io.tonapi.models.AccountEvent
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.blockchain.model.legacy.errors.SendBlockchainException
import com.tonapps.wallet.data.battery.BatteryRepository
import com.tonapps.wallet.data.settings.SettingsRepository
import com.tonapps.wallet.data.token.TokenRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ton.cell.Cell
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class TransactionManager(
    private val accountRepository: AccountRepository,
    private val api: API,
    private val batteryRepository: BatteryRepository,
    private val tokenRepository: TokenRepository,
    private val settingsRepository: SettingsRepository,
    private val delegate: Delegate,
): BaseTransactionManager(api) {

    interface Delegate {
        fun onUpdateWidget()
    }

    private val _sendingTransactionFlow = MutableSharedFlow<SendingTransaction>(
        replay = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    private val sendingTransactionFlow = _sendingTransactionFlow.asSharedFlow()

    private val _transactionFlow = MutableSharedFlow<AccountEventEntity>(
        replay = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    private val transactionFlow = _transactionFlow.asSharedFlow()

    // TODO
    private val _tronUpdatedFlow = MutableEffectFlow<Unit>()
    val tronUpdatedFlow = _tronUpdatedFlow.asSharedFlow()

    private var tronRefreshJob: Job? = null

    init {
        _tronUpdatedFlow.tryEmit(Unit)
        sendingTransactionFlow.mapNotNull { getTransaction(it.wallet, it.hash) }
            .onEach { transaction ->
                _transactionFlow.tryEmit(transaction)
            }
            .launchIn(scope)

        combine(
            api.configFlow.filter { !it.empty },
            accountRepository.selectedWalletFlow
        ) { config, wallet ->
            realtime(config, wallet)
        }.flatMapLatest { it }
            .filterNotNull()
            .onEach { transaction -> _transactionFlow.tryEmit(transaction) }
            .launchIn(scope)

        // TODO refactor
        sendingTransactionFlow.onEach {
            delay(5000) // TODO WTF, maybe debounce?
            delegate.onUpdateWidget()
        }.launchIn(scope)

        combine(
            accountRepository.selectedWalletFlow,
            tronUpdatedFlow,
            settingsRepository.tokenPrefsChangedFlow
        ) { wallet, _, _ ->
            val tronEnabled = settingsRepository.getTronUsdtEnabled(wallet.id)
            val tronAddress = accountRepository.getTronAddress(wallet.id)
            if (tronEnabled && tronAddress != null && wallet.hasPrivateKey && !wallet.testnet && !api.getConfig(wallet.network).flags.disableBattery) {
                Pair(wallet, tronAddress)
            } else {
                null
            }
        }.filterNotNull().onEach { (wallet, tronAddress) ->
            tronRefreshJob?.cancel()
            tronRefreshJob = scope.launch {
                delay(60.seconds)
                tokenRepository.refreshTron(wallet.accountId, wallet.network, tronAddress)

                _tronUpdatedFlow.tryEmit(Unit)
            }
        }.launchIn(scope)
    }

    fun eventsFlow(wallet: WalletEntity) = transactionFlow.filter {
        it.accountId == wallet.accountId && it.testnet == wallet.testnet
    }

    private fun realtime(config: ConfigEntity, wallet: WalletEntity): Flow<AccountEventEntity?> {
        return if (WalletFeature.StreamingV2.isEnabled) {
            realtimeV2(config, wallet)
        } else {
            realtimeV1(config, wallet)
        }
    }

    private fun realtimeV1(config: ConfigEntity, wallet: WalletEntity) = api.realtime(
        accountId = wallet.accountId,
        network = wallet.network,
        config = config,
        onFailure = null
    ).map { it.data }.map { getTransaction(wallet, it) }

    private fun realtimeV2(config: ConfigEntity, wallet: WalletEntity) = api.realtimeV2(
        accountId = wallet.accountId,
        network = wallet.network,
        config = config,
        onFailure = null
    ).mapNotNull {
        L.d("realtimeV2", it.json)
        ToncenterEvent.parse(it.json)
    }.filterIsInstance<TransactionsEvent>()
        .mapNotNull { event ->
            parseTransactionsEvent(event, wallet)
        }

    private fun parseTransactionsEvent(
        event: TransactionsEvent,
        wallet: WalletEntity,
    ): AccountEventEntity? {
        val txArray = event.transactions ?: return null
        if (txArray.length() == 0) return null
        val firstTx = txArray.getJSONObject(0)
        val hash = event.traceExternalHashNorm ?: firstTx.optString("hash", "")
        val lt = firstTx.optString("lt", "0")?.toLongOrNull() ?: 0L
        val timestamp = firstTx.optLong("now", 0L)
        val inProgress = event.finality != "finalized"

        val body = AccountEvent(
            eventId = hash,
            account = AccountAddress(address = wallet.accountId, isScam = false, isWallet = true),
            timestamp = timestamp,
            actions = emptyList(),
            isScam = false,
            lt = lt,
            inProgress = inProgress,
            extra = 0,
            progress = if (inProgress) 0f else 1f,
        )
        return AccountEventEntity(wallet.accountId, wallet.testnet, hash, body)
    }

    private suspend fun getTransaction(
        wallet: WalletEntity,
        hash: String
    ): AccountEventEntity? = withContext(Dispatchers.IO) {
        api.getTransactionByHash(wallet.accountId, wallet.network, hash)
    }

    private suspend fun sendWithBattery(
        wallet: WalletEntity,
        boc: String,
        source: String,
        confirmationTime: Double,
    ) {
        val tonProofToken = accountRepository.requestTonProofToken(wallet)
            ?: throw SendBlockchainException.SendBlockchainUnknownException
        api.sendToBlockchainWithBattery(
            boc = boc,
            tonProofToken = tonProofToken,
            network = wallet.network,
            source = source,
            confirmationTime = confirmationTime
        )
        batteryRepository.refreshBalanceDelay(
            publicKey = wallet.publicKey,
            tonProofToken = tonProofToken,
            network = wallet.network,
        )
    }

    suspend fun send(
        wallet: WalletEntity,
        boc: String,
        withBattery: Boolean,
        source: String,
        confirmationTime: Double,
    ) {
        if (withBattery) {
            sendWithBattery(wallet, boc, source, confirmationTime)
        } else {
            api.sendToBlockchain(boc, wallet.network, source, confirmationTime)
        }
        _sendingTransactionFlow.tryEmit(SendingTransaction(wallet.copy(), boc))
    }

    suspend fun send(
        wallet: WalletEntity,
        boc: Cell,
        withBattery: Boolean,
        source: String,
        confirmationTime: Double,
    ) = send(
        wallet = wallet,
        boc = boc.base64(),
        withBattery = withBattery,
        source = source,
        confirmationTime = confirmationTime
    )
}