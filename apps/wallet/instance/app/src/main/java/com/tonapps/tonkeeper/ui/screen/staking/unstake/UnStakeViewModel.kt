package com.tonapps.tonkeeper.ui.screen.staking.unstake

import android.app.Application
import android.content.Context
import androidx.lifecycle.viewModelScope
import com.tonapps.blockchain.ton.extensions.EmptyPrivateKeyEd25519
import com.tonapps.blockchain.ton.extensions.equalsAddress
import com.tonapps.extensions.MutableEffectFlow
import com.tonapps.extensions.flattenFirst
import com.tonapps.icu.Coins
import com.tonapps.icu.CurrencyFormatter
import com.tonapps.ledger.ton.Transaction
import com.tonapps.tonkeeper.api.totalFees
import com.tonapps.tonkeeper.core.SendBlockchainException
import com.tonapps.tonkeeper.core.entities.SendMetadataEntity
import com.tonapps.tonkeeper.core.entities.StakedEntity
import com.tonapps.tonkeeper.core.entities.TransferEntity
import com.tonapps.tonkeeper.extensions.signLedgerTransaction
import com.tonapps.tonkeeper.extensions.toGrams
import com.tonapps.tonkeeper.ui.base.BaseWalletVM
import com.tonapps.tonkeeper.ui.screen.send.main.SendException
import com.tonapps.wallet.api.API
import com.tonapps.wallet.api.SendBlockchainState
import com.tonapps.wallet.api.entity.TokenEntity
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.passcode.PasscodeManager
import com.tonapps.wallet.data.rates.RatesRepository
import com.tonapps.wallet.data.settings.SettingsRepository
import com.tonapps.wallet.data.staking.StakingPool
import com.tonapps.wallet.data.staking.StakingRepository
import com.tonapps.wallet.data.staking.StakingUtils
import com.tonapps.wallet.data.staking.entities.PoolEntity
import com.tonapps.wallet.data.token.TokenRepository
import com.tonapps.wallet.localization.Localization
import io.ktor.util.reflect.instanceOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.withContext
import org.ton.block.AddrStd
import org.ton.cell.Cell
import org.ton.contract.wallet.WalletTransfer
import org.ton.contract.wallet.WalletTransferBuilder
import uikit.extensions.collectFlow
import uikit.widget.ProcessTaskView

class UnStakeViewModel(
    app: Application,
    private val address: String,
    private val accountRepository: AccountRepository,
    private val stakingRepository: StakingRepository,
    private val tokenRepository: TokenRepository,
    private val settingsRepository: SettingsRepository,
    private val ratesRepository: RatesRepository,
    private val api: API,
    private val passcodeManager: PasscodeManager,
): BaseWalletVM(app) {

    data class AvailableUiState(
        val balanceFormat: CharSequence = "",
        val remainingFormat: CharSequence = "",
        val insufficientBalance: Boolean = false,
        val fiatFormat: CharSequence = "",
    )

    private val currency = settingsRepository.currency
    private val token = "TON"

    private val _amountFlow = MutableStateFlow(0.0)
    private val amountFlow = _amountFlow.map { Coins.of(it) }

    private val _eventFlow = MutableEffectFlow<UnStakeEvent>()
    val eventFlow = _eventFlow.asSharedFlow().filterNotNull()

    val walletFlow = accountRepository.selectedWalletFlow

    val taskStateFlow = MutableEffectFlow<ProcessTaskView.State>()

    private val stakeFlow = walletFlow.map { wallet ->
        getStake(wallet)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null).filterNotNull()

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
        _eventFlow.tryEmit(UnStakeEvent.RouteToAmount)
        updateAmount(0.0)
    }

    fun requestMax() = stakeFlow.take(1).map {
        it.balance.value
    }

    fun requestFee() = combine(
        walletFlow.take(1),
        unsignedBodyFlow()
    ) { wallet, (seqno, unsignedBody) ->
        val contract = wallet.contract
        val message = contract.createTransferMessageCell(
            address = contract.address,
            privateKey = EmptyPrivateKeyEd25519,
            seqno = seqno,
            unsignedBody = unsignedBody,
        )
        api.emulate(message, wallet.testnet)?.totalFees ?: 0L
    }.take(1).flowOn(Dispatchers.IO)

    fun requestFeeFormat() = combine(
        requestFee(),
        stakeFlow
    ) { fee, stake ->
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
        val gift = buildTransfer(wallet, amount, stake.pool, params)
        val body = wallet.contract.createTransferUnsignedBody(
            validUntil = params.validUntil,
            seqno = params.seqno,
            gifts = arrayOf(gift),
        )
        Pair(params.seqno, body)
    }.flowOn(Dispatchers.IO)

    private fun ledgerTransactionFlow() = combine(
        walletFlow.take(1),
        amountFlow.take(1),
        stakeFlow.take(1),
    ) { wallet, amount, stake ->
        val params = getSendParams(wallet)
        val gift = buildTransfer(wallet, amount, stake.pool, params)
        val transaction = Transaction.fromWalletTransfer(gift, params.seqno, params.validUntil)
        Pair(params.seqno, transaction)
    }.flowOn(Dispatchers.IO)

    private suspend fun buildTransfer(
        wallet: WalletEntity,
        amount: Coins,
        pool: PoolEntity,
        sendParams: SendMetadataEntity,
    ): WalletTransfer {
        val isJetton = pool.liquidJettonMaster != null

        val withdrawalFee = Coins.of(StakingUtils.getWithdrawalFee(pool.implementation))

        val coins = if (pool.isTonstakers) {
            org.ton.block.Coins.ofNano(amount.toLong() + withdrawalFee.toLong())
        } else {
            org.ton.block.Coins.ofNano(amount.toLong())
        }

        val builder = WalletTransferBuilder()
        builder.body = buildPayload(pool, amount, AddrStd.parse(pool.address))
        builder.coins = coins
        builder.destination = AddrStd.parse(pool.address)
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
            StakingPool.Implementation.Whales -> StakingUtils.createWhalesWithdrawStakeCell(queryId, amount.toGrams())
            StakingPool.Implementation.LiquidTF -> StakingUtils.createLiquidTfWithdrawStakeCell(queryId, amount.toGrams(), address)
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

    private suspend fun getStake(wallet: WalletEntity): StakedEntity {
        val tokens = tokenRepository.get(currency, wallet.accountId, wallet.testnet) ?: throw IllegalArgumentException("Tokens not found")
        val staking = stakingRepository.get(wallet.accountId, wallet.testnet)
        val staked = StakedEntity.create(staking, tokens, currency, ratesRepository) ?: throw IllegalArgumentException("Staked not found")
        return staked.find { it.pool.address.equalsAddress(address) } ?: throw IllegalArgumentException("Pool not found")
    }

    fun unStake(context: Context) = walletFlow.take(1).map { wallet ->
        if (wallet.isLedger) {
            createLedgerStakeFlow(context, wallet)
        } else {
            createUnStakeFlow(wallet)
        }
    }.flattenFirst().flowOn(Dispatchers.IO).catch { e ->
        taskStateFlow.tryEmit(
            if (e.instanceOf(SendException.Cancelled::class)) {
                ProcessTaskView.State.DEFAULT
            } else {
                ProcessTaskView.State.FAILED
            }
        )
    }.onEach {
        taskStateFlow.tryEmit(ProcessTaskView.State.SUCCESS)
        delay(3000)
        _eventFlow.tryEmit(UnStakeEvent.Finish)
    }

    private fun createLedgerStakeFlow(
        context: Context,
        wallet: WalletEntity
    ) = ledgerTransactionFlow().map { (seqno, transaction) ->
        taskStateFlow.tryEmit(ProcessTaskView.State.LOADING)

        val signedBody = context.signLedgerTransaction(transaction, wallet.id)
            ?: throw SendException.Cancelled()

        val contract = wallet.contract
        val message = contract.createTransferMessageCell(contract.address, seqno, signedBody)

        val state = api.sendToBlockchain(message, wallet.testnet)
        if (state != SendBlockchainState.SUCCESS) {
            throw SendBlockchainException.fromState(state)
        }
    }

    private fun createUnStakeFlow(wallet: WalletEntity) = unsignedBodyFlow().map { (seqno, unsignedBody) ->
        if (!passcodeManager.confirmation(context, context.getString(Localization.app_name))) {
            throw SendException.Cancelled()
        }

        taskStateFlow.tryEmit(ProcessTaskView.State.LOADING)

        val contract = wallet.contract
        val privateKey = accountRepository.getPrivateKey(wallet.id)
        val message = contract.createTransferMessageCell(
            address = contract.address,
            privateKey = privateKey,
            seqno = seqno,
            unsignedBody = unsignedBody
        )

        val state = api.sendToBlockchain(message, wallet.testnet)
        if (state != SendBlockchainState.SUCCESS) {
            throw SendBlockchainException.fromState(state)
        }
    }
}