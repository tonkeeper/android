package com.tonapps.tonkeeper.ui.screen.staking.stake

import android.app.Application
import android.content.Context
import androidx.lifecycle.viewModelScope
import com.tonapps.blockchain.ton.TONOpCode
import com.tonapps.blockchain.ton.TonSendMode
import com.tonapps.blockchain.ton.extensions.storeCoins
import com.tonapps.blockchain.ton.extensions.storeOpCode
import com.tonapps.blockchain.ton.extensions.storeQueryId
import com.tonapps.icu.Coins
import com.tonapps.icu.CurrencyFormatter
import com.tonapps.ledger.ton.Transaction
import com.tonapps.tonkeeper.core.SendBlockchainException
import com.tonapps.tonkeeper.core.entities.SendMetadataEntity
import com.tonapps.tonkeeper.core.entities.TransferEntity
import com.tonapps.tonkeeper.extensions.toGrams
import com.tonapps.tonkeeper.manager.tx.TransactionManager
import com.tonapps.tonkeeper.ui.base.BaseWalletVM
import com.tonapps.tonkeeper.usecase.emulation.Emulated
import com.tonapps.tonkeeper.usecase.emulation.EmulationUseCase
import com.tonapps.tonkeeper.usecase.sign.SignUseCase
import com.tonapps.wallet.api.SendBlockchainState
import com.tonapps.wallet.api.entity.TokenEntity
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.rates.RatesRepository
import com.tonapps.wallet.data.settings.SettingsRepository
import com.tonapps.wallet.data.staking.StakingPool
import com.tonapps.wallet.data.staking.StakingRepository
import com.tonapps.wallet.data.staking.entities.PoolEntity
import com.tonapps.wallet.data.staking.entities.PoolInfoEntity
import com.tonapps.wallet.data.token.TokenRepository
import com.tonapps.wallet.data.token.entities.AccountTokenEntity
import com.tonapps.wallet.localization.Localization
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ton.block.AddrStd
import org.ton.cell.buildCell
import org.ton.contract.wallet.WalletTransfer
import org.ton.contract.wallet.WalletTransferBuilder
import uikit.extensions.collectFlow

class StakingViewModel(
    app: Application,
    private val wallet: WalletEntity,
    private val poolAddress: String,
    private val accountRepository: AccountRepository,
    private val stakingRepository: StakingRepository,
    private val tokenRepository: TokenRepository,
    private val ratesRepository: RatesRepository,
    private val settingsRepository: SettingsRepository,
    private val transactionManager: TransactionManager,
    private val signUseCase: SignUseCase,
    private val emulationUseCase: EmulationUseCase,
) : BaseWalletVM(app) {

    data class AvailableUiState(
        val balanceFormat: CharSequence,
        val remainingFormat: CharSequence,
        val minStakeFormat: CharSequence,
        val insufficientBalance: Boolean,
        val requestMinStake: Boolean,
        val hiddenBalance: Boolean
    )

    private val _poolsFlow = MutableStateFlow<List<PoolInfoEntity>?>(null)
    val poolsFlow = _poolsFlow.asStateFlow().filterNotNull().filter { it.isNotEmpty() }

    private val _amountFlow = MutableStateFlow(0.0)
    private val amountFlow = _amountFlow.map { Coins.of(it) }

    private val _selectedPoolFlow = MutableStateFlow<PoolEntity?>(null)
    val selectedPoolFlow = _selectedPoolFlow.asStateFlow().filterNotNull()

    private val _tokenFlow = MutableStateFlow<AccountTokenEntity?>(null)
    val tokenFlow = _tokenFlow.asStateFlow().filterNotNull()

    private val ratesFlow = tokenFlow.map { token ->
        ratesRepository.getRates(settingsRepository.currency, token.address)
    }.flowOn(Dispatchers.IO)

    val availableUiStateFlow = combine(
        amountFlow,
        tokenFlow,
        selectedPoolFlow
    ) { amount, token, pool ->
        val balance = token.balance.value
        val balanceFormat = CurrencyFormatter.format(token.symbol, balance)
        val minStakeFormat = CurrencyFormatter.format(token.symbol, pool.minStake)
        if (amount == Coins.ZERO) {
            AvailableUiState(
                balanceFormat = balanceFormat,
                remainingFormat = balanceFormat,
                minStakeFormat = minStakeFormat,
                insufficientBalance = false,
                requestMinStake = false,
                hiddenBalance = settingsRepository.hiddenBalances,
            )
        } else {
            val remaining = balance - amount
            AvailableUiState(
                balanceFormat = balanceFormat,
                remainingFormat = CurrencyFormatter.format(token.symbol, remaining),
                minStakeFormat = minStakeFormat,
                insufficientBalance = if (remaining.isZero) false else remaining.isNegative,
                requestMinStake = pool.minStake > amount,
                hiddenBalance = settingsRepository.hiddenBalances,
            )
        }
    }

    val fiatFlow = combine(amountFlow, ratesFlow, tokenFlow) { amount, rates, token ->
        rates.convert(token.address, amount)
    }

    val fiatFormatFlow = fiatFlow.map {
        CurrencyFormatter.format(settingsRepository.currency.code, it, replaceSymbol = false)
    }

    val amountFormatFlow = combine(amountFlow, tokenFlow) { amount, token ->
        CurrencyFormatter.format(token.symbol, amount)
    }

    val apyFormatFlow = combine(
        amountFlow,
        selectedPoolFlow,
        poolsFlow
    ) { amount, pool, pools ->
        val info = pools.find { it.implementation == pool.implementation } ?: return@combine ""
        val apyFormat = CurrencyFormatter.formatPercent(info.apy)
        if (amount.isPositive) {
            val earning = amount.multiply(pool.apy).divide(100)
            "%s ≈ %s · %s".format(
                getString(Localization.staking_apy),
                apyFormat,
                CurrencyFormatter.format(TokenEntity.TON.symbol, earning)
            )
        } else {
            "%s ≈ %s".format(
                getString(Localization.staking_apy),
                apyFormat
            )
        }
    }

    init {
        collectFlow(poolsFlow) { pools ->
            if (_selectedPoolFlow.value != null) {
                return@collectFlow
            }

            val poolAddress = poolAddress.ifBlank {
                pools.first().pools.first().address
            }
            pools.map { it.pools }.flatten().find {
                it.address == poolAddress
            }?.let {
                selectPool(it)
            }
        }
        updateAmount(0.0)

        viewModelScope.launch(Dispatchers.IO) {
            _poolsFlow.value = stakingRepository.get(wallet.accountId, wallet.testnet).pools
            _tokenFlow.value = tokenRepository.get(settingsRepository.currency, wallet.accountId, wallet.testnet)?.firstOrNull()
        }
    }

    fun requestMax() = tokenFlow.take(1).map {
        it.balance.value
    }

    fun updateAmount(amount: Double) {
        _amountFlow.value = amount
    }

    fun selectPool(pool: PoolEntity) {
        _selectedPoolFlow.value = pool
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

    private suspend fun buildTransfer(
        wallet: WalletEntity,
        amount: Coins,
        pool: PoolEntity,
        token: TokenEntity,
        sendParams: SendMetadataEntity,
    ): WalletTransfer {
        val builder = WalletTransferBuilder()
        builder.bounceable = true
        builder.sendMode = (TonSendMode.PAY_GAS_SEPARATELY.value + TonSendMode.IGNORE_ERRORS.value)
        if (0 >= sendParams.seqno) {
            builder.stateInit = wallet.contract.stateInit
        }
        builder.destination = AddrStd.parse(pool.address)
        when (pool.implementation) {
            StakingPool.Implementation.Whales -> builder.applyWhales(amount)
            StakingPool.Implementation.TF -> builder.applyTF(amount)
            StakingPool.Implementation.LiquidTF -> builder.applyLiquid(amount)
            else -> throw IllegalStateException("Unsupported pool implementation: ${pool.implementation}")
        }
        /*val withdrawalFee = Coins.of(StakingUtils.getWithdrawalFee(pool.implementation))
        val coins = if (pool.implementation == StakingPool.Implementation.LiquidTF) {
            org.ton.block.Coins.ofNano(amount.toLong() + withdrawalFee.toLong())
        } else {
            org.ton.block.Coins.ofNano(amount.toLong())
        }

        val builder = WalletTransferBuilder()
        builder.body = buildPayload(pool)
        builder.coins = coins
        if (token.isTon) {
            builder.destination = AddrStd.parse(pool.address)
        } else {
            builder.destination = AddrStd.parse(token.address)
        }
        if (0 >= sendParams.seqno) {
            builder.stateInit = wallet.contract.stateInit
        }*/

        return builder.build()
    }

    private fun unsignedBodyFlow() = combine(
        amountFlow.take(1),
        selectedPoolFlow.take(1),
        tokenFlow.take(1),
    ) { amount, pool, token ->
        val params = getSendParams(wallet)
        val gift = buildTransfer(wallet, amount, pool, token.balance.token, params)
        accountRepository.messageBody(
            wallet = wallet,
            seqNo = params.seqno,
            validUntil = params.validUntil,
            transfers = listOf(gift),
        )
    }.flowOn(Dispatchers.IO)

    private fun ledgerTransactionFlow() = combine(
        amountFlow.take(1),
        selectedPoolFlow.take(1),
        tokenFlow.take(1),
    ) { amount, pool, token ->
        val params = getSendParams(wallet)
        val gift = buildTransfer(wallet, amount, pool, token.balance.token, params)
        val transaction = Transaction.fromWalletTransfer(gift, params.seqno, params.validUntil)
        Pair(params.seqno, transaction)
    }.flowOn(Dispatchers.IO)

    private fun requestFee() = unsignedBodyFlow().map { message ->
        try {
            emulationUseCase(message, wallet.testnet).extra
        } catch (e: Throwable) {
            Emulated.defaultExtra
        }
    }.flowOn(Dispatchers.IO)

    fun requestFeeFormat() = requestFee().map { extra ->
        val currency = settingsRepository.currency
        Pair(
            CurrencyFormatter.format(TokenEntity.TON.symbol, extra.value, TokenEntity.TON.decimals),
            CurrencyFormatter.format(currency.code, extra.fiat, currency.decimals)
        )
    }

    fun stake(
        context: Context,
    ) = (if (wallet.isLedger) {
        createLedgerStakeFlow(context, wallet)
    } else {
        createStakeFlow(wallet)
    }).flowOn(Dispatchers.IO)

    private fun WalletTransferBuilder.applyLiquid(amount: Coins) {
        val withdrawalFee = Coins.ONE
        val amountWithFee = withdrawalFee + amount

        this.coins = amountWithFee.toGrams()
        this.body = buildCell {
            storeOpCode(TONOpCode.LIQUID_TF_DEPOSIT)
            storeQueryId(TransferEntity.newWalletQueryId())
            storeUInt(0x000000000005b7ce, 64)
        }
    }

    private fun WalletTransferBuilder.applyWhales(amount: Coins) {
        this.coins = amount.toGrams()
        this.body = buildCell {
            storeOpCode(TONOpCode.WHALES_DEPOSIT)
            storeQueryId(TransferEntity.newWalletQueryId())
            storeCoins(Coins.of(0.1).toGrams())
        }
    }

    private fun WalletTransferBuilder.applyTF(amount: Coins) {
        this.coins = amount.toGrams()
        this.body = buildCell {
            storeUInt(0, 32)
            storeBytes("d".toByteArray())
        }
    }

    private fun createLedgerStakeFlow(
        context: Context,
        wallet: WalletEntity
    ) = ledgerTransactionFlow().map { (seqno, transaction) ->
        val message = signUseCase(context, wallet, seqno, transaction)

        val state = transactionManager.send(wallet, message, false)
        if (state != SendBlockchainState.SUCCESS) {
            throw SendBlockchainException.fromState(state)
        }
    }

    private fun createStakeFlow(
        wallet: WalletEntity
    ) = unsignedBodyFlow().map { message ->
        val cell = message.createUnsignedBody(false)
        val boc = signUseCase(context, wallet, cell, message.seqNo)
        val state = transactionManager.send(wallet, boc, false)
        if (state != SendBlockchainState.SUCCESS) {
            throw SendBlockchainException.fromState(state)
        }
    }
}