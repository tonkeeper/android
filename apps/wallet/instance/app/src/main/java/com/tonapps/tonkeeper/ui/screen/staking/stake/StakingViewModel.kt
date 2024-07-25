package com.tonapps.tonkeeper.ui.screen.staking.stake

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tonapps.blockchain.ton.extensions.EmptyPrivateKeyEd25519
import com.tonapps.extensions.MutableEffectFlow
import com.tonapps.icu.Coins
import com.tonapps.icu.CurrencyFormatter
import com.tonapps.tonkeeper.api.totalFees
import com.tonapps.tonkeeper.core.entities.SendMetadataEntity
import com.tonapps.tonkeeper.core.entities.TransferEntity
import com.tonapps.tonkeeper.ui.screen.send.SendException
import com.tonapps.wallet.api.API
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
import com.tonapps.wallet.data.staking.entities.PoolInfoEntity
import com.tonapps.wallet.data.token.TokenRepository
import com.tonapps.wallet.localization.Localization
import io.tonapi.models.PoolInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ton.block.AddrStd
import org.ton.cell.Cell
import org.ton.contract.wallet.WalletTransfer
import org.ton.contract.wallet.WalletTransferBuilder
import uikit.extensions.collectFlow
import uikit.widget.ProcessTaskView
import java.math.BigDecimal
import java.math.RoundingMode

class StakingViewModel(
    address: String,
    private val accountRepository: AccountRepository,
    private val stakingRepository: StakingRepository,
    private val tokenRepository: TokenRepository,
    private val ratesRepository: RatesRepository,
    private val passcodeManager: PasscodeManager,
    private val settingsRepository: SettingsRepository,
    private val api: API,
): ViewModel() {

    data class AvailableUiState(
        val balanceFormat: CharSequence,
        val remainingFormat: CharSequence,
        val minStakeFormat: CharSequence,
        val insufficientBalance: Boolean,
        val requestMinStake: Boolean,
    )

    val poolsFlow = accountRepository.selectedWalletFlow.map { wallet ->
        stakingRepository.get(wallet.accountId, wallet.testnet).pools
    }.flowOn(Dispatchers.IO).filter { it.isNotEmpty() }

    private val _amountFlow = MutableStateFlow(0.0)
    private val amountFlow = _amountFlow.map { Coins.of(it) }

    private val _selectedPoolFlow = MutableStateFlow<PoolEntity?>(null)
    val selectedPoolFlow = _selectedPoolFlow.asStateFlow().filterNotNull()

    private val _eventFlow = MutableEffectFlow<StakingEvent>()
    val eventFlow = _eventFlow.asSharedFlow().filterNotNull()

    val tokenFlow = accountRepository.selectedWalletFlow.map { wallet ->
        tokenRepository.get(settingsRepository.currency, wallet.accountId, wallet.testnet)
    }.map { it.firstOrNull() }.filterNotNull().flowOn(Dispatchers.IO)

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
            )
        } else {
            val remaining = balance - amount
            AvailableUiState(
                balanceFormat = balanceFormat,
                remainingFormat = CurrencyFormatter.format(token.symbol, remaining),
                minStakeFormat = minStakeFormat,
                insufficientBalance = !remaining.isPositive,
                requestMinStake = pool.minStake > amount
            )
        }
    }

    val fiatFlow = combine(amountFlow, ratesFlow, tokenFlow) { amount, rates, token ->
        rates.convert(token.address, amount)
    }

    val fiatFormatFlow = fiatFlow.map {
        CurrencyFormatter.format(settingsRepository.currency.code, it)
    }

    val amountFormatFlow = combine(amountFlow, tokenFlow) { amount, token ->
        CurrencyFormatter.format(token.symbol, amount)
    }

    val apyFormatFlow = combine(
        amountFlow,
        selectedPoolFlow,
        tokenFlow,
        poolsFlow
    ) { amount, pool, token, pools ->
        val info = pools.find { it.implementation == pool.implementation } ?: return@combine ""
        if (amount == Coins.ZERO) {
            info.apyFormat
        } else {
            val earnings = amount.value.multiply(pool.apy)
                .divide(BigDecimal(100), RoundingMode.HALF_UP)

            val coinsFormat = CurrencyFormatter.format(token.symbol, Coins.of(earnings))
            "${info.apyFormat} · $coinsFormat"
        }
    }

    val walletFlow = accountRepository.selectedWalletFlow

    val taskStateFlow = MutableEffectFlow<ProcessTaskView.State>()

    init {
        collectFlow(poolsFlow) { pools ->
            if (_selectedPoolFlow.value != null) {
                return@collectFlow
            }

            val poolAddress = address.ifBlank {
                pools.first().pools.first().address
            }
            pools.map { it.pools }.flatten().find {
                it.address == poolAddress
            }?.let {
                selectPool(it)
            }
        }
        updateAmount(0.0)
    }

    fun updateAmount(amount: Double) {
        _amountFlow.value = amount
    }

    fun selectPool(pool: PoolEntity) {
        _selectedPoolFlow.value = pool
    }

    fun details(pool: PoolInfoEntity) {
        _eventFlow.tryEmit(StakingEvent.OpenDetails(pool))
    }

    fun openOptions() {
        _eventFlow.tryEmit(StakingEvent.OpenOptions)
    }

    fun confirm() {
        val pool = _selectedPoolFlow.value ?: return
        _eventFlow.tryEmit(StakingEvent.OpenConfirm(pool, Coins.of(_amountFlow.value)))
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

    private suspend fun buildPayload(pool: PoolEntity): Cell = withContext(Dispatchers.IO) {
        val queryId = TransferEntity.newWalletQueryId()
        when (pool.implementation) {
            StakingPool.Implementation.Whales -> StakingUtils.createWhalesAddStakeCommand(queryId)
            StakingPool.Implementation.LiquidTF -> StakingUtils.createLiquidTfAddStakeCommand(queryId)
            StakingPool.Implementation.TF -> StakingUtils.createTfAddStakeCommand()
            else -> throw IllegalStateException("Unsupported pool implementation: ${pool.implementation}")
        }
    }

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
        builder.body = buildPayload(pool)
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

    private fun unsignedBodyFlow() = combine(
        walletFlow.take(1),
        amountFlow.take(1),
        selectedPoolFlow.take(1),
        tokenFlow.take(1),
    ) { wallet, amount, pool, token ->
        val params = getSendParams(wallet)
        val gift = buildTransfer(wallet, amount, pool, token.balance.token, params)
        val body = wallet.contract.createTransferUnsignedBody(
            validUntil = params.validUntil,
            seqno = params.seqno,
            gifts = arrayOf(gift),
        )
        Pair(params.seqno, body)
    }.flowOn(Dispatchers.IO)

    fun requestFee() = combine(walletFlow.take(1), unsignedBodyFlow()) { wallet, (seqno, unsignedBody) ->
        val contract = wallet.contract
        val message = contract.createTransferMessageCell(
            address = contract.address,
            privateKey = EmptyPrivateKeyEd25519,
            seqno = seqno,
            unsignedBody = unsignedBody,
        )
        api.emulate(message, wallet.testnet).totalFees
    }.take(1).flowOn(Dispatchers.IO)

    fun requestFeeFormat() = combine(ratesFlow.take(1), requestFee(), tokenFlow.take(1)) { rates, fee, token ->
        val currency = settingsRepository.currency
        val coins = Coins.of(fee)
        val converted = rates.convert(token.address, coins)
        Pair(
            CurrencyFormatter.format(TokenEntity.TON.symbol, coins, TokenEntity.TON.decimals),
            CurrencyFormatter.format(currency.code, converted, currency.decimals)
        )
    }.take(1)

    fun stake(context: Context) = combine(
        passcodeManager.confirmationFlow(context, context.getString(Localization.app_name)).take(1),
        walletFlow.take(1),
        unsignedBodyFlow().take(1)
    ) { _, wallet, (seqno, unsignedBody) ->
        taskStateFlow.tryEmit(ProcessTaskView.State.LOADING)
        val contract = wallet.contract
        val privateKey = accountRepository.getPrivateKey(wallet.id)
        val message = contract.createTransferMessageCell(
            address = contract.address,
            privateKey = privateKey,
            seqno = seqno,
            unsignedBody = unsignedBody,
        )
        if (!api.sendToBlockchain(message, wallet.testnet)) {
            throw SendException.FailedToSendTransaction()
        }
    }.catch {
        taskStateFlow.tryEmit(ProcessTaskView.State.FAILED)
    }.flowOn(Dispatchers.IO).onEach {
        taskStateFlow.tryEmit(ProcessTaskView.State.SUCCESS)
        delay(3000)
        _eventFlow.tryEmit(StakingEvent.Finish)
    }.take(1)
}