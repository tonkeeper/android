package com.tonapps.tonkeeper.ui.screen.staking.unstake

import android.app.Application
import android.content.Context
import androidx.lifecycle.viewModelScope
import com.tonapps.blockchain.model.legacy.TokenEntity
import com.tonapps.blockchain.model.legacy.TransferEntity
import com.tonapps.blockchain.model.legacy.WalletCurrency
import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.blockchain.model.legacy.WalletType
import com.tonapps.blockchain.model.legacy.toGrams
import com.tonapps.blockchain.ton.TONOpCode
import com.tonapps.blockchain.ton.TonSendMode
import com.tonapps.blockchain.ton.extensions.equalsAddress
import com.tonapps.blockchain.ton.extensions.storeAddress
import com.tonapps.blockchain.ton.extensions.storeCoins
import com.tonapps.blockchain.ton.extensions.storeMaybeRef
import com.tonapps.blockchain.ton.extensions.storeOpCode
import com.tonapps.blockchain.ton.extensions.storeQueryId
import com.tonapps.blockchain.ton.extensions.toUserFriendly
import com.tonapps.bus.core.AnalyticsHelper
import com.tonapps.bus.generated.Events
import com.tonapps.bus.generated.opTerminal
import com.tonapps.core.components.isNativeTon
import com.tonapps.core.helper.TON_COIN_ASSET_ID
import com.tonapps.core.helper.TransactionSentAnalytics
import com.tonapps.core.helper.WalletRedMetadata
import com.tonapps.deposit.usecase.emulation.Emulated
import com.tonapps.deposit.usecase.emulation.EmulationUseCase
import com.tonapps.deposit.usecase.sign.SignUseCase
import com.tonapps.extensions.MutableEffectFlow
import com.tonapps.extensions.currentTimeMillis
import com.tonapps.extensions.currentTimeSecondsInt
import com.tonapps.extensions.generateUuid
import com.tonapps.icu.Coins
import com.tonapps.icu.CurrencyFormatter
import com.tonapps.ledger.ton.Transaction
import com.tonapps.legacy.enteties.SendMetadataEntity
import com.tonapps.legacy.enteties.StakedEntity
import com.tonapps.portfolio.analytics.StakingAnalytics
import com.tonapps.tonkeeper.helper.DateHelper
import com.tonapps.tonkeeper.ui.base.BaseWalletVM
import com.tonapps.wallet.api.API
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.multichain.account.AccountWithDetails
import com.tonapps.wallet.data.multichain.account.McAccountRepository
import com.tonapps.wallet.data.multichain.account.jettonAssetId
import com.tonapps.wallet.data.multichain.account.jettonToTon
import com.tonapps.wallet.data.multichain.account.matchesJettonMaster
import com.tonapps.wallet.data.multichain.account.toAccountTokenEntity
import com.tonapps.wallet.data.multichain.account.tonCoinAssetId
import com.tonapps.wallet.data.rates.RatesRepository
import com.tonapps.wallet.data.settings.SettingsRepository
import com.tonapps.wallet.data.staking.StakingPool
import com.tonapps.wallet.data.staking.StakingRepository
import com.tonapps.wallet.data.staking.entities.PoolEntity
import com.tonapps.wallet.data.staking.entities.PoolInfoEntity
import com.tonapps.wallet.data.token.TokenRepository
import com.tonapps.wallet.data.token.entities.AccountTokenEntity
import com.tonapps.wallet.data.tx.TransactionManager
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ton.block.AddrStd
import org.ton.block.StateInit
import org.ton.cell.buildCell
import org.ton.contract.wallet.MessageData
import org.ton.contract.wallet.WalletTransfer
import org.ton.contract.wallet.WalletTransferBuilder
import org.ton.tlb.CellRef
import uikit.extensions.collectFlow
import uikit.widget.ProcessTaskView
import java.math.RoundingMode
import kotlin.time.Duration.Companion.seconds

class UnStakeViewModel(
    app: Application,
    private val wallet: WalletEntity,
    private val poolAddress: String,
    private val accountRepository: AccountRepository,
    private val stakingRepository: StakingRepository,
    private val tokenRepository: TokenRepository,
    private val settingsRepository: SettingsRepository,
    private val ratesRepository: RatesRepository,
    private val transactionManager: TransactionManager,
    private val signUseCase: SignUseCase,
    private val emulationUseCase: EmulationUseCase,
    private val mcAccountRepository: McAccountRepository,
    private val api: API
) : BaseWalletVM(app) {

    data class AvailableUiState(
        val balanceFormat: CharSequence = "",
        val remainingFormat: CharSequence = "",
        val insufficientBalance: Boolean = false,
        val fiatFormat: CharSequence = "",
    )

    private val currency = settingsRepository.currency
    private val tokenSymbol = TokenEntity.TON.symbol
    private var tickerJob: Job? = null

    private val _amountFlow = MutableStateFlow(0.0)
    private val amountFlow = _amountFlow.map { Coins.of(it) }

    private val _eventFlow = MutableEffectFlow<UnStakeEvent>()
    val eventFlow = _eventFlow.asSharedFlow().filterNotNull()

    val taskStateFlow = MutableEffectFlow<ProcessTaskView.State>()

    private val _stakeFlow = MutableStateFlow<StakedEntity?>(null)
    private val stakeFlow = _stakeFlow.asStateFlow().filterNotNull()

    private val _poolInfoFlow = MutableStateFlow<PoolInfoEntity?>(null)
    val poolInfoFlow = _poolInfoFlow.asStateFlow().filterNotNull()

    private val _cycleEndFormatFlow = MutableStateFlow<String?>(null)
    val cycleEndFormatFlow = _cycleEndFormatFlow.asStateFlow().filterNotNull()

    val availableUiStateFlow = combine(
        amountFlow,
        stakeFlow
    ) { amount, stake ->
        val balance = stake.balance
        val balanceFormat = CurrencyFormatter.format(tokenSymbol, balance)
        val rates = ratesRepository.getRates(wallet.network, currency, TokenEntity.TON.address)
        val fiat = rates.convert(TokenEntity.TON.address, amount)
        val fiatFormat = CurrencyFormatter.format(currency.code, fiat, replaceSymbol = false)
        if (amount == Coins.ZERO) {
            AvailableUiState(
                balanceFormat = balanceFormat,
                remainingFormat = balanceFormat,
                insufficientBalance = false,
                fiatFormat = fiatFormat
            )
        } else {
            val remaining = balance - amount
            AvailableUiState(
                balanceFormat = balanceFormat,
                remainingFormat = CurrencyFormatter.format(tokenSymbol, remaining),
                insufficientBalance = if (remaining.isZero) {
                    false
                } else {
                    remaining.isNegative
                },
                fiatFormat = fiatFormat
            )
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AvailableUiState())

    val amountFormatFlow = amountFlow.map { amount ->
        CurrencyFormatter.formatFull(tokenSymbol, amount, 9)
    }

    val fiatFormatFlow = availableUiStateFlow.map { it.fiatFormat }

    val poolFlow = stakeFlow.map { it.pool }

    init {
        collectFlow(stakeFlow) { entity ->
            startTicker(entity.cycleEnd)
        }

        _eventFlow.tryEmit(UnStakeEvent.RouteToAmount)
        updateAmount(0.0)

        viewModelScope.launch(Dispatchers.IO) {
            val staked = loadStake()
            _stakeFlow.value = staked
            _poolInfoFlow.value = stakingRepository.get(
                wallet.accountId,
                wallet.network
            ).pools.find { it.implementation == staked?.pool?.implementation }
            analyticsProps()?.let { props ->
                StakingAnalytics.unstakeInput(from = "staking_viewer", props = props)
            }
        }
    }

    private fun analyticsProps(): StakingAnalytics.Props? {
        val poolInfo = _poolInfoFlow.value ?: return null
        return StakingAnalytics.Props(
            jettonSymbol = TokenEntity.TON.symbol,
            providerName = poolInfo.implementation.title,
            providerDomain = poolInfo.details.url,
        )
    }

    private fun trackUnstakeSuccess() {
        analyticsProps()?.let(StakingAnalytics::unstakeSuccess)
        val pool = _stakeFlow.value?.pool
        TransactionSentAnalytics.transactionSent(
            wallet = wallet,
            category = Events.TransactionSent.TransactionSentCategory.Staking,
            categoryDetail = Events.TransactionSent.TransactionSentCategoryDetail.Unstake,
            asset = TON_COIN_ASSET_ID,
            amount = _amountFlow.value,
            feeAsset = Events.TransactionSent.TransactionSentFeeAsset.Coin,
            initiatedBy = Events.TransactionSent.TransactionSentInitiatedBy.User,
            stakingProvider = pool?.address,
            isLiquid = pool?.let { it.implementation == StakingPool.Implementation.LiquidTF },
        )
    }

    private fun startTicker(timestamp: Long) {
        tickerJob = viewModelScope.launch(Dispatchers.IO) {
            while (true) {
                _cycleEndFormatFlow.value = DateHelper.formatCycleEnd(timestamp)
                delay(1.seconds)
            }
        }
    }

    fun requestMax() = stakeFlow.take(1).map {
        it.balance.value
    }

    fun requestFee() = unsignedBodyFlow().map { message ->
        val operationId = generateUuid()
        val startedAtMs = currentTimeMillis()
        AnalyticsHelper.Default.events.redOperations.opAttempt(
            operationId = operationId,
            flow = Events.RedOperations.RedOperationsFlow.Stake,
            operation = Events.RedOperations.RedOperationsOperation.Emulate,
            attemptSource = null,
            startedAtMs = currentTimeSecondsInt(),
            otherMetadata = WalletRedMetadata.walletKit(),
        )
        try {
            val result = emulationUseCase(message, wallet.testnet, params = true).extra
            val finishedAtMs = currentTimeMillis()
            AnalyticsHelper.Default.events.redOperations.opTerminal(
                operationId = operationId,
                flow = Events.RedOperations.RedOperationsFlow.Stake,
                operation = Events.RedOperations.RedOperationsOperation.Emulate,
                durationMs = (finishedAtMs - startedAtMs).toDouble(),
                finishedAtMs = currentTimeSecondsInt(),
                error = null,
                otherMetadata = WalletRedMetadata.walletKit(),
            )
            result
        } catch (e: Throwable) {
            val finishedAtMs = currentTimeMillis()
            AnalyticsHelper.Default.events.redOperations.opTerminal(
                operationId = operationId,
                flow = Events.RedOperations.RedOperationsFlow.Stake,
                operation = Events.RedOperations.RedOperationsOperation.Emulate,
                durationMs = (finishedAtMs - startedAtMs).toDouble(),
                finishedAtMs = currentTimeSecondsInt(),
                error = e,
                otherMetadata = WalletRedMetadata.walletKit(),
            )
            Emulated.defaultExtra
        }
    }.take(1).flowOn(Dispatchers.IO)

    fun requestFeeFormat() = combine(
        requestFee(),
        poolFlow
    ) { extra, pool ->
        val currency = settingsRepository.currency
        val rates = ratesRepository.getTONRates(wallet.network, currency)
        val fee = StakingPool.getTotalFee(extra.value, pool.implementation)

        val fiat = rates.convertTON(fee)

        Pair(
            CurrencyFormatter.format(TokenEntity.TON.symbol, fee),
            CurrencyFormatter.format(currency.code, fiat)
        )
    }

    fun confirm() {
        analyticsProps()?.let(StakingAnalytics::unstakeConfirm)
        collectFlow(poolFlow.take(1)) { pool ->
            _eventFlow.tryEmit(UnStakeEvent.OpenConfirm(pool, Coins.of(_amountFlow.value)))
        }
    }

    fun updateAmount(amount: Double) {
        _amountFlow.value = amount
    }

    private fun unsignedBodyFlow() = combine(
        amountFlow.take(1),
        stakeFlow.take(1),
    ) { amount, stake ->
        val params = getSendParams(wallet)
        val gift = buildTransfer(wallet, amount, stake, params)
        accountRepository.messageBody(
            wallet = wallet,
            seqNo = params.seqno,
            validUntil = params.validUntil,
            transfers = listOf(gift),
        )
    }.flowOn(Dispatchers.IO)

    private fun ledgerTransactionFlow() = combine(
        amountFlow.take(1),
        stakeFlow.take(1),
    ) { amount, stake ->
        val params = getSendParams(wallet)
        val gift = buildTransfer(wallet, amount, stake, params)
        val transaction = Transaction.fromWalletTransfer(gift, params.seqno, params.validUntil)
        Pair(params.seqno, transaction)
    }.flowOn(Dispatchers.IO)

    private suspend fun buildTransfer(
        wallet: WalletEntity,
        amount: Coins,
        staked: StakedEntity,
        sendParams: SendMetadataEntity,
    ): WalletTransfer {
        val stateInitRef = if (0 >= sendParams.seqno) {
            wallet.contract.stateInitRef
        } else {
            null
        }

        val isSendAll = amount >= staked.balance
        val pool = staked.pool
        val builder = WalletTransferBuilder()
        builder.bounceable = true
        builder.sendMode = (TonSendMode.PAY_GAS_SEPARATELY.value + TonSendMode.IGNORE_ERRORS.value)
        when (staked.pool.implementation) {
            StakingPool.Implementation.LiquidTF -> {
                val token = requireLiquidJettonToken(pool)
                builder.applyLiquid(
                    amount = amount,
                    responseAddress = wallet.contract.address,
                    tsTONToken = token,
                    isSendAll = isSendAll,
                    stateInitRef = stateInitRef,
                )
            }

            StakingPool.Implementation.Whales -> builder.applyWhales(
                pool,
                amount,
                isSendAll,
                stateInitRef
            )

            StakingPool.Implementation.TF -> builder.applyTF(pool, stateInitRef)
            else -> throw IllegalStateException("Unsupported pool implementation")
        }
        return builder.build()
    }

    private suspend fun requireLiquidJettonToken(pool: PoolEntity): AccountTokenEntity {
        val master = pool.liquidJettonMaster
            ?: throw IllegalStateException("Liquid jetton master not found")
        val tokens = runCatching {
            tokenRepository.get(
                currency = currency,
                accountId = wallet.accountId,
                network = wallet.network,
                refresh = true,
            )
        }.getOrNull() ?: tokenRepository.getLocal(currency, wallet.accountId, wallet.network)
        val token = tokens.find { it.address.equalsAddress(master) }
            ?: throw IllegalStateException("Liquid jetton balance not found")
        if (token.balance.walletAddress.isBlank()) {
            throw IllegalStateException("Liquid jetton wallet address not found")
        }
        return token
    }

    private suspend fun WalletTransferBuilder.applyLiquid(
        amount: Coins,
        responseAddress: AddrStd,
        tsTONToken: AccountTokenEntity,
        isSendAll: Boolean,
        stateInitRef: CellRef<StateInit>?
    ) {
        val address = tsTONToken.balance.walletAddress.toUserFriendly(
            wallet = false,
            bounceable = true,
            testnet = wallet.testnet
        )

        val balance = tsTONToken.balance.value
        val jettonAmount = if (isSendAll) {
            balance
        } else {
            val rates = ratesRepository.getRates(
                wallet.network,
                WalletCurrency.TON,
                tsTONToken.address,
            )
            val tokenRate = rates.getRate(tsTONToken.address)
            if (tokenRate.isZero) {
                throw IllegalStateException("Liquid jetton rate not found")
            }
            val converted = amount.div(
                other = tokenRate,
                scale = tsTONToken.decimals,
                roundingMode = RoundingMode.DOWN,
            )
            minOf(converted, balance)
        }

        val customPayload = buildCell {
            storeUInt(1, 1)
            storeUInt(0, 1)
        }

        val body = buildCell {
            storeOpCode(TONOpCode.LIQUID_TF_BURN)
            storeQueryId(TransferEntity.newWalletQueryId())
            storeCoins(jettonAmount.toGrams())
            storeAddress(responseAddress)
            storeMaybeRef(customPayload)
        }

        this.coins = Coins.ONE.toGrams() // TODO fees: hardcoded value
        this.destination = AddrStd.parse(address)
        this.messageData = MessageData.raw(body, stateInitRef)
    }

    private fun WalletTransferBuilder.applyWhales(
        pool: PoolEntity,
        amount: Coins,
        isSendAll: Boolean,
        stateInitRef: CellRef<StateInit>?
    ) {
        val body = buildCell {
            storeOpCode(TONOpCode.WHALES_WITHDRAW)
            storeQueryId(TransferEntity.newWalletQueryId())
            storeCoins(Coins.of(0.1).toGrams()) // TODO fees: internal fee should be less as well
            if (isSendAll) {
                storeCoins(Coins.ZERO.toGrams())
            } else {
                storeCoins(amount.toGrams())
            }
        }

        this.coins = Coins.of(0.2).toGrams() // TODO fees: hardcoded value
        this.destination = AddrStd.parse(pool.address)
        this.messageData = MessageData.raw(body, stateInitRef)
    }

    private fun WalletTransferBuilder.applyTF(pool: PoolEntity, stateInitRef: CellRef<StateInit>?) {
        val body = buildCell {
            storeUInt(0, 32)
            storeBytes("w".toByteArray())
        }

        this.coins = Coins.ONE.toGrams() // TODO fees: hardcoded value
        this.destination = AddrStd.parse(pool.address)
        this.messageData = MessageData.raw(body, stateInitRef)
    }

    private suspend fun getSendParams(
        wallet: WalletEntity,
    ): SendMetadataEntity = withContext(Dispatchers.IO) {
        val seqnoDeferred = async { accountRepository.getSeqno(wallet) }
        val validUntilDeferred = async { accountRepository.getValidUntil(wallet.network) }

        SendMetadataEntity(
            seqno = seqnoDeferred.await(),
            validUntil = validUntilDeferred.await(),
        )
    }

    private suspend fun loadStake(): StakedEntity? {
        return try {
            if (wallet.type == WalletType.Multichain) {
                loadMultichainStake()
            } else {
                loadLegacyStake()
            }
        } catch (e: Throwable) {
            null
        }
    }

    private suspend fun loadLegacyStake(): StakedEntity? {
        val tokens =
            tokenRepository.get(currency, wallet.accountId, wallet.network) ?: return null
        val staking = stakingRepository.get(wallet.accountId, wallet.network)
        val staked =
            StakedEntity.create(wallet, staking, tokens, currency, ratesRepository)
        return staked.find { it.pool.address.equalsAddress(poolAddress) }
    }

    private suspend fun loadMultichainStake(): StakedEntity? {
        val staking = stakingRepository.get(
            accountId = wallet.accountId,
            network = wallet.network,
            initializedAccount = wallet.initialized,
        )
        val pool = staking.findPoolByAddress(poolAddress) ?: return null
        val pendingWithdraw = staking.getPendingWithdraw(pool)
        val pendingDeposit = staking.getPendingDeposit(pool)
        val readyWithdraw = staking.getReadyWithdraw(pool)

        if (pool.implementation == StakingPool.Implementation.LiquidTF) {
            val liquidAccount = loadLiquidAccount(pool)
            val liquidToken = liquidAccount?.toAccountTokenEntity(currency)
            val tonBalance = liquidAccount?.toTonBalance() ?: Coins.ZERO
            return StakedEntity(
                pool = pool,
                balance = tonBalance,
                readyWithdraw = readyWithdraw,
                fiatBalance = liquidToken?.fiat ?: Coins.ZERO,
                fiatReadyWithdraw = Coins.ZERO,
                liquidToken = null,
                pendingDeposit = pendingDeposit,
                pendingWithdraw = pendingWithdraw,
                cycleStart = pool.cycleStart,
                cycleEnd = pool.cycleEnd,
            )
        }

        val amount = staking.getAmount(pool)
        val fiatRates = ratesRepository.getTONRates(wallet.network, currency)
        return StakedEntity(
            pool = pool,
            balance = amount,
            readyWithdraw = readyWithdraw,
            fiatBalance = fiatRates.convertTON(amount),
            fiatReadyWithdraw = fiatRates.convertTON(readyWithdraw),
            liquidToken = null,
            pendingDeposit = pendingDeposit,
            pendingWithdraw = pendingWithdraw,
            cycleStart = pool.cycleStart,
            cycleEnd = pool.cycleEnd,
        )
    }

    private suspend fun loadLiquidAccount(pool: PoolEntity): AccountWithDetails? {
        val master = pool.liquidJettonMaster ?: return null
        if (pool.implementation != StakingPool.Implementation.LiquidTF) {
            return null
        }
        val assetId = jettonAssetId(master, wallet.testnet)
        val currencyCode = currency.code
        val cached = runCatching {
            mcAccountRepository.getCachedAccounts(
                walletId = wallet.id,
                currency = currencyCode,
            )
        }.getOrNull()?.accounts?.firstOrNull { it.matchesJettonMaster(master) }

        return cached
            ?: runCatching {
                mcAccountRepository.findAccount(wallet.id, assetId, currencyCode)
            }.getOrNull()
    }

    private suspend fun AccountWithDetails.toTonBalance(): Coins {
        val currencyCode = currency.code
        val tonAccount = runCatching {
            mcAccountRepository.getCachedAccounts(wallet.id, currencyCode)
        }.getOrNull()?.accounts?.firstOrNull { it.asset.isNativeTon() }
            ?: runCatching {
                mcAccountRepository.findAccount(
                    wallet.id,
                    tonCoinAssetId(wallet.testnet),
                    currencyCode,
                )
            }.getOrNull()

        val jettonPrice = rate?.value?.value?.takeIf { it > BigDecimal.ZERO }
        val tonPrice = tonAccount?.rate?.value?.value?.takeIf { it > BigDecimal.ZERO }
        if (jettonPrice == null || tonPrice == null) {
            return Coins.ZERO
        }
        val tonAmount = jettonToTon(displayBalance.value, jettonPrice, tonPrice)
        return Coins.of(java.math.BigDecimal(tonAmount.toPlainString()))
    }

    fun unStake(context: Context) = (if (wallet.isLedger) {
        createLedgerStakeFlow(context, wallet)
    } else {
        createUnStakeFlow(wallet)
    }).flowOn(Dispatchers.IO)

    private fun createLedgerStakeFlow(
        context: Context,
        wallet: WalletEntity
    ) = ledgerTransactionFlow().map { (seqno, transaction) ->
        val operationId = generateUuid()
        val startedAtMs = currentTimeMillis()
        AnalyticsHelper.Default.events.redOperations.opAttempt(
            operationId = operationId,
            flow = Events.RedOperations.RedOperationsFlow.Stake,
            operation = Events.RedOperations.RedOperationsOperation.Unstake,
            attemptSource = null,
            startedAtMs = currentTimeSecondsInt(),
            otherMetadata = WalletRedMetadata.walletKit(),
        )
        try {
            val message = signUseCase(context, wallet, seqno, transaction)

            taskStateFlow.tryEmit(ProcessTaskView.State.LOADING)

            transactionManager.send(wallet, message, false, "", 0.0)
            trackUnstakeSuccess()
            val finishedAtMs = currentTimeMillis()
            AnalyticsHelper.Default.events.redOperations.opTerminal(
                operationId = operationId,
                flow = Events.RedOperations.RedOperationsFlow.Stake,
                operation = Events.RedOperations.RedOperationsOperation.Unstake,
                durationMs = (finishedAtMs - startedAtMs).toDouble(),
                finishedAtMs = currentTimeSecondsInt(),
                error = null,
                otherMetadata = WalletRedMetadata.walletKit(),
            )
        } catch (e: Throwable) {
            val finishedAtMs = currentTimeMillis()
            AnalyticsHelper.Default.events.redOperations.opTerminal(
                operationId = operationId,
                flow = Events.RedOperations.RedOperationsFlow.Stake,
                operation = Events.RedOperations.RedOperationsOperation.Unstake,
                durationMs = (finishedAtMs - startedAtMs).toDouble(),
                finishedAtMs = currentTimeSecondsInt(),
                error = e,
                otherMetadata = WalletRedMetadata.walletKit(),
            )
            throw e
        }
    }

    private fun createUnStakeFlow(
        wallet: WalletEntity
    ) = unsignedBodyFlow().map { message ->
        val operationId = generateUuid()
        val startedAtMs = currentTimeMillis()
        AnalyticsHelper.Default.events.redOperations.opAttempt(
            operationId = operationId,
            flow = Events.RedOperations.RedOperationsFlow.Stake,
            operation = Events.RedOperations.RedOperationsOperation.Unstake,
            attemptSource = null,
            startedAtMs = currentTimeSecondsInt(),
            otherMetadata = WalletRedMetadata.walletKit(),
        )
        try {
            val cell = message.createUnsignedBody(false)

            val boc = signUseCase(context, wallet, cell, message.seqNo)

            taskStateFlow.tryEmit(ProcessTaskView.State.LOADING)

            transactionManager.send(wallet, boc, false, "", 0.0)
            trackUnstakeSuccess()
            val finishedAtMs = currentTimeMillis()
            AnalyticsHelper.Default.events.redOperations.opTerminal(
                operationId = operationId,
                flow = Events.RedOperations.RedOperationsFlow.Stake,
                operation = Events.RedOperations.RedOperationsOperation.Unstake,
                durationMs = (finishedAtMs - startedAtMs).toDouble(),
                finishedAtMs = currentTimeSecondsInt(),
                error = null,
                otherMetadata = WalletRedMetadata.walletKit(),
            )
        } catch (e: Throwable) {
            val finishedAtMs = currentTimeMillis()
            AnalyticsHelper.Default.events.redOperations.opTerminal(
                operationId = operationId,
                flow = Events.RedOperations.RedOperationsFlow.Stake,
                operation = Events.RedOperations.RedOperationsOperation.Unstake,
                durationMs = (finishedAtMs - startedAtMs).toDouble(),
                finishedAtMs = currentTimeSecondsInt(),
                error = e,
                otherMetadata = WalletRedMetadata.walletKit(),
            )
            throw e
        }
    }

    override fun onCleared() {
        super.onCleared()
        tickerJob?.cancel()
    }
}