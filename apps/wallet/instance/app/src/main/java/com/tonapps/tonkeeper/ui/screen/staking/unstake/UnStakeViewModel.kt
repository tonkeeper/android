package com.tonapps.tonkeeper.ui.screen.staking.unstake

import android.app.Application
import android.content.Context
import androidx.lifecycle.viewModelScope
import com.tonapps.blockchain.ton.TONOpCode
import com.tonapps.blockchain.ton.TonSendMode
import com.tonapps.blockchain.ton.extensions.equalsAddress
import com.tonapps.blockchain.ton.extensions.storeAddress
import com.tonapps.blockchain.ton.extensions.storeCoins
import com.tonapps.blockchain.ton.extensions.storeMaybeRef
import com.tonapps.blockchain.ton.extensions.storeOpCode
import com.tonapps.blockchain.ton.extensions.storeQueryId
import com.tonapps.blockchain.ton.extensions.toUserFriendly
import com.tonapps.extensions.MutableEffectFlow
import com.tonapps.icu.Coins
import com.tonapps.icu.CurrencyFormatter
import com.tonapps.ledger.ton.Transaction
import com.tonapps.tonkeeper.core.SendBlockchainException
import com.tonapps.tonkeeper.core.entities.SendMetadataEntity
import com.tonapps.tonkeeper.core.entities.StakedEntity
import com.tonapps.tonkeeper.core.entities.TransferEntity
import com.tonapps.tonkeeper.extensions.toGrams
import com.tonapps.tonkeeper.helper.DateHelper
import com.tonapps.tonkeeper.manager.tx.TransactionManager
import com.tonapps.tonkeeper.ui.base.BaseWalletVM
import com.tonapps.tonkeeper.usecase.emulation.Emulated
import com.tonapps.tonkeeper.usecase.emulation.EmulationUseCase
import com.tonapps.tonkeeper.usecase.sign.SignUseCase
import com.tonapps.wallet.api.API
import com.tonapps.wallet.api.SendBlockchainState
import com.tonapps.wallet.api.entity.TokenEntity
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.core.currency.WalletCurrency
import com.tonapps.wallet.data.rates.RatesRepository
import com.tonapps.wallet.data.settings.SettingsRepository
import com.tonapps.wallet.data.staking.StakingPool
import com.tonapps.wallet.data.staking.StakingRepository
import com.tonapps.wallet.data.staking.entities.PoolEntity
import com.tonapps.wallet.data.token.TokenRepository
import com.tonapps.wallet.data.token.entities.AccountTokenEntity
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
    private val api: API
): BaseWalletVM(app) {

    data class AvailableUiState(
        val balanceFormat: CharSequence = "",
        val remainingFormat: CharSequence = "",
        val insufficientBalance: Boolean = false,
        val fiatFormat: CharSequence = "",
    )

    private val currency = settingsRepository.currency
    private val token = "TON"
    private var tickerJob: Job? = null

    private val _amountFlow = MutableStateFlow(0.0)
    private val amountFlow = _amountFlow.map { Coins.of(it) }

    private val _eventFlow = MutableEffectFlow<UnStakeEvent>()
    val eventFlow = _eventFlow.asSharedFlow().filterNotNull()

    val taskStateFlow = MutableEffectFlow<ProcessTaskView.State>()

    private val _stakeFlow = MutableStateFlow<StakedEntity?>(null)
    private val stakeFlow = _stakeFlow.asStateFlow().filterNotNull()

    private val _cycleEndFormatFlow = MutableStateFlow<String?>(null)
    val cycleEndFormatFlow = _cycleEndFormatFlow.asStateFlow().filterNotNull()

    val availableUiStateFlow = combine(
        amountFlow,
        stakeFlow
    ) { amount, stake ->
        val balance = stake.balance
        val balanceFormat = CurrencyFormatter.format(token, balance)
        val rates = ratesRepository.getRates(currency, token)
        val fiat = rates.convert(token, amount)
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
                remainingFormat = CurrencyFormatter.format(token, remaining),
                insufficientBalance = if (remaining.isZero) false else remaining.isNegative,
                fiatFormat = fiatFormat
            )
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AvailableUiState())

    val amountFormatFlow = amountFlow.map { amount ->
        CurrencyFormatter.format(TokenEntity.TON.symbol, amount)
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
            _stakeFlow.value = loadStake()
        }
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
        try {
            emulationUseCase(message, wallet.testnet, params = true).extra
        } catch (e: Throwable) {
            Emulated.defaultExtra
        }
    }.take(1).flowOn(Dispatchers.IO)

    fun requestFeeFormat() = combine(
        requestFee(),
        poolFlow
    ) { extra, pool ->
        val currency = settingsRepository.currency
        val rates = ratesRepository.getTONRates(currency)
        val fee = StakingPool.getTotalFee(extra.value, pool.implementation)

        val fiat = rates.convertTON(fee)

        Pair(
            CurrencyFormatter.format(TokenEntity.TON.symbol, fee, TokenEntity.TON.decimals),
            CurrencyFormatter.format(currency.code, fiat, currency.decimals)
        )
    }

    fun confirm() {
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

        val isSendAll = amount == staked.balance
        val pool = staked.pool
        val builder = WalletTransferBuilder()
        builder.bounceable = true
        builder.sendMode = (TonSendMode.PAY_GAS_SEPARATELY.value + TonSendMode.IGNORE_ERRORS.value)
        when (staked.pool.implementation) {
            StakingPool.Implementation.LiquidTF -> {
                val token = pool.liquidJettonMaster?.let { getTokenBalance(it) } ?: throw IllegalStateException("Liquid jetton master not found")
                builder.applyLiquid(amount, wallet.contract.address, token, stateInitRef)
            }
            StakingPool.Implementation.Whales -> builder.applyWhales(pool, amount, isSendAll, stateInitRef)
            StakingPool.Implementation.TF -> builder.applyTF(pool, stateInitRef)
            else -> throw IllegalStateException("Unsupported pool implementation")
        }
        return builder.build()
    }

    private suspend fun getTokenBalance(
        tokenAddress: String
    ): AccountTokenEntity? {
        val tokens = tokenRepository.get(
            currency = settingsRepository.currency,
            accountId = wallet.accountId,
            testnet = wallet.testnet
        ) ?: return null
        return tokens.find { it.address.equalsAddress(tokenAddress) }
    }

    private suspend fun WalletTransferBuilder.applyLiquid(amount: Coins, responseAddress: AddrStd, tsTONToken: AccountTokenEntity, stateInitRef: CellRef<StateInit>?) {
        val address = tsTONToken.balance.walletAddress.toUserFriendly(
            wallet = false,
            bounceable = true,
            testnet = wallet.testnet
        )

        val rates = ratesRepository.getRates(WalletCurrency.TON, tsTONToken.address)
        val tokenRate = rates.getRate(tsTONToken.address)
        val convertedAmount = Coins.of((amount / tokenRate).value, tsTONToken.decimals)

        val customPayload = buildCell {
            storeUInt(1, 1)
            storeUInt(0, 1)
        }

        val body = buildCell {
            storeOpCode(TONOpCode.LIQUID_TF_BURN)
            storeQueryId(TransferEntity.newWalletQueryId())
            storeCoins(convertedAmount.toGrams())
            storeAddress(responseAddress)
            storeMaybeRef(customPayload)
        }

        this.coins = Coins.ONE.toGrams()
        this.destination = AddrStd.parse(address)
        this.messageData = MessageData.raw(body, stateInitRef)
    }

    private fun WalletTransferBuilder.applyWhales(pool: PoolEntity, amount: Coins, isSendAll: Boolean, stateInitRef: CellRef<StateInit>?) {
        val body = buildCell {
            storeOpCode(TONOpCode.WHALES_WITHDRAW)
            storeQueryId(TransferEntity.newWalletQueryId())
            storeCoins(Coins.of(0.1).toGrams())
            if (isSendAll) {
                storeCoins(Coins.ZERO.toGrams())
            } else {
                storeCoins(amount.toGrams())
            }
        }

        this.coins = Coins.of(0.2).toGrams()
        this.destination = AddrStd.parse(pool.address)
        this.messageData = MessageData.raw(body, stateInitRef)
    }

    private fun WalletTransferBuilder.applyTF(pool: PoolEntity, stateInitRef: CellRef<StateInit>?) {
        val body = buildCell {
            storeUInt(0, 32)
            storeBytes("w".toByteArray())
        }

        this.coins = Coins.ONE.toGrams()
        this.destination = AddrStd.parse(pool.address)
        this.messageData = MessageData.raw(body, stateInitRef)
    }

    private suspend fun getSendParams(
        wallet: WalletEntity,
    ): SendMetadataEntity = withContext(Dispatchers.IO) {
        val seqnoDeferred = async { accountRepository.getSeqno(wallet) }
        val validUntilDeferred = async { accountRepository.getValidUntil(wallet.testnet) }

        SendMetadataEntity(
            seqno = seqnoDeferred.await(),
            validUntil = validUntilDeferred.await(),
        )
    }

    private suspend fun loadStake(): StakedEntity? {
        try {
            val tokens = tokenRepository.get(currency, wallet.accountId, wallet.testnet) ?: return null
            val staking = stakingRepository.get(wallet.accountId, wallet.testnet)
            val staked = StakedEntity.create(wallet, staking, tokens, currency, ratesRepository, api)
            return staked.find { it.pool.address.equalsAddress(poolAddress) }
        } catch (e: Throwable) {
            return null
        }
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
        val message = signUseCase(context, wallet, seqno, transaction)

        taskStateFlow.tryEmit(ProcessTaskView.State.LOADING)

        val state = transactionManager.send(wallet, message, false, "", 0.0)
        if (state != SendBlockchainState.SUCCESS) {
            throw SendBlockchainException.fromState(state)
        }
    }

    private fun createUnStakeFlow(
        wallet: WalletEntity
    ) = unsignedBodyFlow().map { message ->
        val cell = message.createUnsignedBody(false)

        val boc = signUseCase(context, wallet, cell, message.seqNo)

        taskStateFlow.tryEmit(ProcessTaskView.State.LOADING)

        val state = transactionManager.send(wallet, boc, false, "", 0.0)
        if (state != SendBlockchainState.SUCCESS) {
            throw SendBlockchainException.fromState(state)
        }
    }

    override fun onCleared() {
        super.onCleared()
        tickerJob?.cancel()
    }
}