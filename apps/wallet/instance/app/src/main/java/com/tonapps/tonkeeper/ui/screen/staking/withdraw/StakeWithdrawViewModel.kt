package com.tonapps.tonkeeper.ui.screen.staking.withdraw

import android.app.Application
import android.content.Context
import androidx.lifecycle.viewModelScope
import com.tonapps.blockchain.model.legacy.TokenEntity
import com.tonapps.blockchain.model.legacy.TransferEntity
import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.blockchain.model.legacy.toGrams
import com.tonapps.blockchain.ton.TONOpCode
import com.tonapps.blockchain.ton.TonSendMode
import com.tonapps.blockchain.ton.extensions.equalsAddress
import com.tonapps.blockchain.ton.extensions.storeCoins
import com.tonapps.blockchain.ton.extensions.storeOpCode
import com.tonapps.blockchain.ton.extensions.storeQueryId
import com.tonapps.bus.core.AnalyticsHelper
import com.tonapps.bus.generated.Events
import com.tonapps.bus.generated.opTerminal
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
import com.tonapps.tonkeeper.ui.base.BaseWalletVM
import com.tonapps.wallet.api.API
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.rates.RatesRepository
import com.tonapps.wallet.data.settings.SettingsRepository
import com.tonapps.wallet.data.staking.StakingPool
import com.tonapps.wallet.data.staking.StakingRepository
import com.tonapps.wallet.data.staking.entities.PoolDetailsEntity
import com.tonapps.wallet.data.staking.entities.PoolEntity
import com.tonapps.wallet.data.token.TokenRepository
import com.tonapps.wallet.data.tx.TransactionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
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
import uikit.widget.ProcessTaskView

class StakeWithdrawViewModel(
    app: Application,
    private val wallet: WalletEntity,
    private val poolAddress: String,
    private val stakingRepository: StakingRepository,
    private val ratesRepository: RatesRepository,
    private val settingsRepository: SettingsRepository,
    private val tokenRepository: TokenRepository,
    private val transactionManager: TransactionManager,
    private val signUseCase: SignUseCase,
    private val emulationUseCase: EmulationUseCase,
    private val accountRepository: AccountRepository,
    private val api: API
): BaseWalletVM(app) {

    val taskStateFlow = MutableEffectFlow<ProcessTaskView.State>()

    private val currency = settingsRepository.currency
    private val _poolFlow = MutableStateFlow<Pair<StakedEntity, PoolDetailsEntity>?>(null)
    private val poolFlow = _poolFlow.asStateFlow().filterNotNull()

    val stakeFlow = poolFlow.map { it.first }
    val poolDetailsFlow = poolFlow.map { it.second }

    private val amountFlow = stakeFlow.map {
        if (it.pool.implementation == StakingPool.Implementation.Whales) {
            it.readyWithdraw
        } else {
            it.balance
        }
    }

    val amountFormatFlow = amountFlow.map { amount ->
        val fiat = ratesRepository.getTONRates(wallet.network, currency).convertTON(amount)
        val amountFormat = CurrencyFormatter.format(TokenEntity.TON.symbol, amount)
        val fiatFormat = CurrencyFormatter.formatFiat(currency.code, fiat, replaceSymbol = false)
        Pair(amountFormat, fiatFormat)
    }.flowOn(Dispatchers.IO)

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val tokens = tokenRepository.get(currency, wallet.accountId, wallet.network) ?: return@launch
            val staking = stakingRepository.get(wallet.accountId, wallet.network)
            val staked = StakedEntity.create(wallet, staking, tokens, currency, ratesRepository)
            val item = staked.find { it.pool.address.equalsAddress(poolAddress) } ?: return@launch
            val details = staking.getDetails(item.pool.implementation) ?: return@launch
            _poolFlow.value = Pair(item, details)
        }
    }

    private fun requestExtra() = unsignedBodyFlow().map { message ->
        val operationId = generateUuid()
        val startedAtMs = currentTimeMillis()
        AnalyticsHelper.Default.events.redOperations.opAttempt(
            operationId = operationId,
            flow = Events.RedOperations.RedOperationsFlow.Stake,
            operation = Events.RedOperations.RedOperationsOperation.Emulate,
            attemptSource = null,
            startedAtMs = currentTimeSecondsInt(),
            otherMetadata = null,
        )
        try {
            val result = emulationUseCase(message, wallet.testnet).extra
            val finishedAtMs = currentTimeMillis()
            AnalyticsHelper.Default.events.redOperations.opTerminal(
                operationId = operationId,
                flow = Events.RedOperations.RedOperationsFlow.Stake,
                operation = Events.RedOperations.RedOperationsOperation.Emulate,
                durationMs = (finishedAtMs - startedAtMs).toDouble(),
                finishedAtMs = currentTimeSecondsInt(),
                error = null,
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
            )
            Emulated.defaultExtra
        }
    }

    fun requestFee() = combine(
        requestExtra(),
        stakeFlow
    ) { extra, stake ->
        val currency = settingsRepository.currency
        val rates = ratesRepository.getTONRates(wallet.network, currency)
        val fee = StakingPool.getTotalFee(extra.value, stake.pool.implementation)

        val amount = CurrencyFormatter.format(TokenEntity.TON.symbol, fee)
        val fiat = CurrencyFormatter.format(currency.code, rates.convertTON(fee), replaceSymbol = false)
        Pair(amount, fiat)
    }.flowOn(Dispatchers.IO)

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

    private fun buildTransfer(
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
            StakingPool.Implementation.Whales -> builder.applyWhales(pool, amount, isSendAll, stateInitRef)
            StakingPool.Implementation.TF -> builder.applyTF(pool, stateInitRef)
            else -> throw IllegalStateException("Unsupported pool implementation")
        }
        return builder.build()
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

    fun send(context: Context) = (if (wallet.isLedger) {
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
            otherMetadata = null,
        )
        try {
            val message = signUseCase(context, wallet, seqno, transaction)

            taskStateFlow.tryEmit(ProcessTaskView.State.LOADING)

            transactionManager.send(wallet, message, false, "", 0.0)
            val finishedAtMs = currentTimeMillis()
            AnalyticsHelper.Default.events.redOperations.opTerminal(
                operationId = operationId,
                flow = Events.RedOperations.RedOperationsFlow.Stake,
                operation = Events.RedOperations.RedOperationsOperation.Unstake,
                durationMs = (finishedAtMs - startedAtMs).toDouble(),
                finishedAtMs = currentTimeSecondsInt(),
                error = null,
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
            otherMetadata = null,
        )
        try {
            val cell = message.createUnsignedBody(false)

            val boc = signUseCase(context, wallet, cell, message.seqNo)

            taskStateFlow.tryEmit(ProcessTaskView.State.LOADING)

            transactionManager.send(wallet, boc, false, "", 0.0)
            val finishedAtMs = currentTimeMillis()
            AnalyticsHelper.Default.events.redOperations.opTerminal(
                operationId = operationId,
                flow = Events.RedOperations.RedOperationsFlow.Stake,
                operation = Events.RedOperations.RedOperationsOperation.Unstake,
                durationMs = (finishedAtMs - startedAtMs).toDouble(),
                finishedAtMs = currentTimeSecondsInt(),
                error = null,
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
            )
            throw e
        }
    }
}