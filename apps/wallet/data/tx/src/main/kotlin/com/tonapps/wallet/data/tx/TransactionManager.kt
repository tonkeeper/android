package com.tonapps.wallet.data.tx

import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.blockchain.model.legacy.errors.SendBlockchainException
import com.tonapps.blockchain.ton.extensions.base64
import com.tonapps.extensions.AppLifecycleProvider
import com.tonapps.extensions.MutableEffectFlow
import com.tonapps.extensions.toHex
import com.tonapps.log.L
import com.tonapps.wallet.api.API
import com.tonapps.wallet.api.AuthorizationProvider
import com.tonapps.wallet.api.entity.AccountEventEntity
import com.tonapps.wallet.api.entity.Authorization
import com.tonapps.wallet.api.entity.ConfigEntity
import com.tonapps.wallet.api.entity.toncenter.ToncenterEvent
import com.tonapps.wallet.api.entity.toncenter.TransactionsEvent
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.battery.BatteryRepository
import com.tonapps.wallet.data.settings.SettingsRepository
import com.tonapps.wallet.data.token.TokenRepository
import io.tonapi.models.AccountAddress
import io.tonapi.models.AccountEvent
import io.tonapi.models.Action
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.ton.cell.Cell
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class TransactionManager(
    private val accountRepository: AccountRepository,
    private val api: API,
    private val authorizationProvider: AuthorizationProvider,
    private val batteryRepository: BatteryRepository,
    private val tokenRepository: TokenRepository,
    private val settingsRepository: SettingsRepository,
    private val appLifecycleProvider: AppLifecycleProvider,
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
            accountRepository.selectedWalletFlow,
            appLifecycleProvider.observe
        ) { config, wallet, foreground ->
            if (foreground) {
                realtimeV2(config, wallet)
            } else {
                emptyFlow()
            }
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
            settingsRepository.tokenPrefsChangedFlow,
            appLifecycleProvider.observe
        ) { wallet, _, _, foreground ->
            if (!foreground) {
                return@combine null
            }
            val tronAddress = accountRepository.getTronAddress(wallet.id)
            if (tronAddress != null && wallet.hasPrivateKey && !wallet.testnet && !api.getConfig(wallet.network).flags.disableBattery) {
                Pair(wallet, tronAddress)
            } else {
                null
            }
        }.onEach { pair ->
            tronRefreshJob?.cancel()
            if (pair == null) {
                return@onEach
            }
            val (wallet, tronAddress) = pair
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
        auth: Authorization,
        headers: Map<String, String>,
    ) {
        val keyPair = authorizationProvider.getWalletKeyPair(wallet.id)
        try {
            api.sendToBlockchainWithBattery(
                boc = boc,
                auth = auth,
                keyPair = keyPair,
                network = wallet.network,
                source = source,
                confirmationTime = confirmationTime,
                headers = headers,
            )
        } finally {
            keyPair?.privateKey?.fill(0)
        }
        batteryRepository.refreshBalanceDelay(wallet)
    }

    fun createCustomSender(): TransactionSender {
        return TransactionSender(this)
    }

    /**
     * Polls TonAPI until the account event for [hash] exists and is no longer in progress.
     * Used by sequential multi-message flows (e.g. migration batches) so the next external
     * message is not broadcast before the previous one is accepted on-chain.
     *
     * Prefer [waitUntilSeqno] for battery / gasless relays: the on-chain event hash is not the
     * BOC hash the client signed.
     */
    suspend fun waitUntilTransaction(
        wallet: WalletEntity,
        hash: String,
        pollIntervalMs: Long = 2_000L,
        timeoutMs: Long = 120_000L,
    ) = withContext(Dispatchers.IO) {
        var lastError: Throwable? = null
        try {
            withTimeout(timeoutMs) {
                while (true) {
                    try {
                        val event = api.accounts(wallet.network)
                            .getAccountEvent(wallet.accountId, hash)
                        if (!event.inProgress) {
                            if (event.actions.any { it.status == Action.Status.failed }) {
                                throw SendBlockchainException.SendBlockchainStatusException
                            }
                            return@withTimeout
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: SendBlockchainException) {
                        throw e
                    } catch (e: Throwable) {
                        lastError = e
                        L.d("waitUntilTransaction", "tx status poll failed: ${e.message}")
                    }
                    delay(pollIntervalMs)
                }
            }
        } catch (e: TimeoutCancellationException) {
            throw IllegalStateException(
                "Timeout waiting for transaction $hash (account=${wallet.accountId})",
                lastError,
            )
        }
    }

    suspend fun waitUntilTransaction(
        wallet: WalletEntity,
        boc: Cell,
        pollIntervalMs: Long = 2_000L,
        timeoutMs: Long = 120_000L,
    ) = waitUntilTransaction(
        wallet = wallet,
        hash = boc.hash().toHex(),
        pollIntervalMs = pollIntervalMs,
        timeoutMs = timeoutMs,
    )

    /**
     * Waits until the source wallet's on-chain seqno reaches [minimum] before the next external
     * message is broadcast. Matches the iOS migration waiter: flat poll after a short initial
     * delay; transient seqno read failures are ignored so a partial batch is not abandoned.
     */
    suspend fun waitUntilSeqno(
        wallet: WalletEntity,
        minimum: Int,
        initialDelayMs: Long = 2_000L,
        pollIntervalMs: Long = 1_500L,
        timeoutMs: Long = 60_000L,
    ) = withContext(Dispatchers.IO) {
        val deadline = System.currentTimeMillis() + timeoutMs
        var delayMs = initialDelayMs
        while (System.currentTimeMillis() < deadline) {
            delay(delayMs)
            delayMs = pollIntervalMs
            try {
                if (api.getAccountSeqno(wallet.accountId, wallet.network) >= minimum) {
                    return@withContext
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                L.d("waitUntilSeqno", "seqno poll failed: ${e.message}")
            }
        }
        throw IllegalStateException(
            "Timeout waiting for wallet seqno $minimum (account=${wallet.accountId})",
        )
    }

    /**
     * Polls Battery `GET /status` until [Status.pendingTransactions] is empty so the next
     * sponsored message can be accepted by the relayer.
     */
    suspend fun waitUntilBatteryIdle(
        wallet: WalletEntity,
        initialDelayMs: Long = 1_000L,
        pollIntervalMs: Long = 1_500L,
        timeoutMs: Long = 60_000L,
    ) = withContext(Dispatchers.IO) {
        val auth = authorizationProvider.getAuthBy(wallet.id)
        if (auth.isEmpty) {
            throw IllegalStateException("Authorization is empty")
        }
        val deadline = System.currentTimeMillis() + timeoutMs
        var delayMs = initialDelayMs
        while (System.currentTimeMillis() < deadline) {
            delay(delayMs)
            delayMs = pollIntervalMs
            try {
                val status = api.getBatteryStatus(auth = auth, network = wallet.network)
                if (status != null && status.pendingTransactions.isEmpty()) {
                    return@withContext
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                L.d("waitUntilBatteryIdle", "battery status poll failed: ${e.message}")
            }
        }
        throw IllegalStateException(
            "Timeout waiting for battery pending_transactions to clear (wallet=${wallet.id})",
        )
    }

    /**
     * [withBattery] means the relayer pays the fee, so the battery endpoint is the only way to
     * deliver the message. [viaBattery] only asks for that endpoint as the transport — the battery
     * service needs the auth headers to attribute the message to a wallet id — and falls back to
     * the public one when the wallet has no auth to send.
     */
    internal suspend fun send(
        wallet: WalletEntity,
        boc: String,
        withBattery: Boolean,
        source: String,
        confirmationTime: Double,
        headers: Map<String, String>,
        viaBattery: Boolean = withBattery,
    ) {
        val auth = if (withBattery || viaBattery) {
            authorizationProvider.getAuthBy(wallet.id)
        } else {
            Authorization.Empty
        }

        if (withBattery && auth.isEmpty) {
            throw SendBlockchainException.SendBlockchainUnknownException
        }

        if (auth.isEmpty) {
            api.sendToBlockchain(boc, wallet.network, source, confirmationTime, headers)
        } else {
            sendWithBattery(wallet, boc, source, confirmationTime, auth, headers)
        }
        _sendingTransactionFlow.tryEmit(SendingTransaction(wallet.copy(), boc))
    }

    suspend fun send(
        wallet: WalletEntity,
        boc: String,
        withBattery: Boolean,
        source: String,
        confirmationTime: Double,
        viaBattery: Boolean = withBattery,
    ) = send(
        wallet = wallet,
        boc = boc,
        withBattery = withBattery,
        source = source,
        confirmationTime = confirmationTime,
        headers = emptyMap(),
        viaBattery = viaBattery,
    )

    suspend fun send(
        wallet: WalletEntity,
        boc: Cell,
        withBattery: Boolean,
        source: String,
        confirmationTime: Double,
        viaBattery: Boolean = withBattery,
    ) = send(
        wallet = wallet,
        boc = boc.base64(),
        withBattery = withBattery,
        source = source,
        confirmationTime = confirmationTime,
        viaBattery = viaBattery,
    )
}