package com.tonapps.tonkeeper.ui.screen.staking.unstake

import androidx.lifecycle.ViewModel
import com.tonapps.blockchain.ton.extensions.EmptyPrivateKeyEd25519
import com.tonapps.blockchain.ton.extensions.toRawAddress
import com.tonapps.extensions.MutableEffectFlow
import com.tonapps.icu.Coins
import com.tonapps.icu.CurrencyFormatter
import com.tonapps.tonkeeper.api.totalFees
import com.tonapps.tonkeeper.core.entities.SendMetadataEntity
import com.tonapps.tonkeeper.core.entities.StakedEntity
import com.tonapps.tonkeeper.core.entities.TransferEntity
import com.tonapps.tonkeeper.ui.screen.staking.stake.StakingEvent
import com.tonapps.tonkeeper.ui.screen.staking.stake.StakingViewModel.AvailableUiState
import com.tonapps.wallet.api.API
import com.tonapps.wallet.api.entity.TokenEntity
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.rates.RatesRepository
import com.tonapps.wallet.data.settings.SettingsRepository
import com.tonapps.wallet.data.staking.StakingPool
import com.tonapps.wallet.data.staking.StakingRepository
import com.tonapps.wallet.data.staking.StakingUtils
import com.tonapps.wallet.data.staking.entities.PoolEntity
import com.tonapps.wallet.data.token.TokenRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.withContext
import org.ton.block.AddrStd
import org.ton.cell.Cell
import org.ton.contract.wallet.WalletTransfer
import org.ton.contract.wallet.WalletTransferBuilder
import uikit.extensions.collectFlow

class UnStakeViewModel(
    private val address: String,
    private val accountRepository: AccountRepository,
    private val stakingRepository: StakingRepository,
    private val tokenRepository: TokenRepository,
    private val settingsRepository: SettingsRepository,
    private val ratesRepository: RatesRepository,
    private val api: API,
): ViewModel() {

    data class AvailableUiState(
        val balanceFormat: CharSequence,
        val remainingFormat: CharSequence,
        val insufficientBalance: Boolean,
        val fiatFormat: CharSequence,
    )

    private val currency = settingsRepository.currency
    private val token = "TON"

    private val _amountFlow = MutableStateFlow(0.0)
    private val amountFlow = _amountFlow.map { Coins.of(it) }

    private val _eventFlow = MutableEffectFlow<UnStakeEvent>()
    val eventFlow = _eventFlow.asSharedFlow().filterNotNull()

    val walletFlow = accountRepository.selectedWalletFlow

    private val stakeFlow = walletFlow.map { wallet ->
        getStake(wallet)
    }.filterNotNull()

    val availableUiStateFlow = combine(
        amountFlow,
        stakeFlow
    ) { amount, stake ->
        val balance = stake.balance.value
        val balanceFormat = CurrencyFormatter.format(token, balance)
        val rates = ratesRepository.getRates(currency, token)
        val fiat = rates.convert(token, amount)
        val fiatFormat = CurrencyFormatter.format(currency.code, fiat)

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
                insufficientBalance = !remaining.isPositive,
                fiatFormat = fiatFormat
            )
        }
    }

    val poolFlow = stakeFlow.map { it.pool }

    init {
        updateAmount(0.0)
    }

    fun requestFee() = combine(walletFlow.take(1), unsignedBodyFlow()) { wallet, (seqno, unsignedBody) ->
        val contract = wallet.contract
        val message = contract.createTransferMessageCell(
            address = contract.address,
            privateKey = EmptyPrivateKeyEd25519,
            seqno = seqno,
            unsignedBody = unsignedBody,
        )
        api.emulate(message, wallet.testnet)?.totalFees ?: 0L
    }.take(1).flowOn(Dispatchers.IO)

    fun requestFeeFormat() = combine(requestFee(), stakeFlow) { fee, stake ->
        val coins = Coins.of(fee)
        val rates = ratesRepository.getRates(currency, token)
        val converted = rates.convert("TON", coins)
        Pair(
            CurrencyFormatter.format(TokenEntity.TON.symbol, coins, TokenEntity.TON.decimals),
            CurrencyFormatter.format(currency.code, converted, currency.decimals)
        )
    }.take(1)

    fun confirm() {
        collectFlow(poolFlow.take(1)) { pool ->
            _eventFlow.tryEmit(UnStakeEvent.OpenConfirm(pool, Coins.of(_amountFlow.value)))
        }
    }

    fun updateAmount(amount: Double) {
        _amountFlow.value = amount
    }

    private fun unsignedBodyFlow() = combine(
        walletFlow.take(1),
        amountFlow.take(1),
        stakeFlow.take(1),
    ) { wallet, amount, stake ->
        val params = getSendParams(wallet)
        val gift = buildTransfer(wallet, amount, stake.pool, stake.balance.token, params)
        val body = wallet.contract.createTransferUnsignedBody(
            validUntil = params.validUntil,
            seqno = params.seqno,
            gifts = arrayOf(gift),
        )
        Pair(params.seqno, body)
    }.flowOn(Dispatchers.IO)

    private suspend fun buildTransfer(
        wallet: WalletEntity,
        amount: Coins,
        pool: PoolEntity,
        token: TokenEntity,
        sendParams: SendMetadataEntity,
    ): WalletTransfer {
        val withdrawalFee = Coins.of(StakingUtils.getWithdrawalFee(pool.implementation))
        val coins = if (pool.implementation == StakingPool.Implementation.LiquidTF) {
            org.ton.block.Coins.ofNano(amount.toLong() + withdrawalFee.toLong())
        } else {
            org.ton.block.Coins.ofNano(amount.toLong())
        }

        val builder = WalletTransferBuilder()
        builder.body = buildPayload(pool, amount, AddrStd.parse(pool.address))
        builder.coins = coins
        if (token.isTon) {
            builder.destination = AddrStd.parse(pool.address)
        } else {
            builder.destination = AddrStd.parse(token.address)
        }
        if (0 >= sendParams.seqno) {
            builder.stateInit = wallet.contract.stateInit
        }
        return builder.build()
    }

    private suspend fun buildPayload(
        pool: PoolEntity,
        amount: Coins,
        address: AddrStd
    ): Cell = withContext(Dispatchers.IO) {
        val queryId = TransferEntity.newWalletQueryId()
        when (pool.implementation) {
            StakingPool.Implementation.Whales -> StakingUtils.createWhalesWithdrawStakeCell(queryId, org.ton.block.Coins.ofNano(amount.toLong()))
            StakingPool.Implementation.LiquidTF -> StakingUtils.createLiquidTfWithdrawStakeCell(queryId, org.ton.block.Coins.ofNano(amount.toLong()), address)
            StakingPool.Implementation.TF -> StakingUtils.createTfWithdrawStakeCell()
            else -> throw IllegalStateException("Unsupported pool implementation: ${pool.implementation}")
        }
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

    private suspend fun getStake(wallet: WalletEntity): StakedEntity? {
        val staking = stakingRepository.get(wallet.accountId, wallet.testnet)
        val tokens = tokenRepository.getLocal(currency, wallet.accountId, wallet.testnet)
        val staked = StakedEntity.create(staking, tokens)
        val stakedTokens = staked.map { it.balance.token }.filter { !it.isTon }.map { it.address }
        val rates = ratesRepository.getRates(currency, stakedTokens + "TON")
        val stakedWithFiat = staked.map {
            val tokenAddress = it.balance.token.address
            it.copy(
                fiatBalance = rates.convert(tokenAddress, it.balance.value),
                fiatReadyWithdraw = rates.convert(tokenAddress, it.readyWithdraw),
            )
        }
        return stakedWithFiat.find { it.pool.address == address }
    }
}