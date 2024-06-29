package com.tonapps.tonkeeper.ui.screen.staking

import android.util.Log
import androidx.lifecycle.ViewModel
import com.tonapps.extensions.MutableEffectFlow
import com.tonapps.icu.Coins
import com.tonapps.icu.CurrencyFormatter
import com.tonapps.tonkeeper.ui.screen.init.InitEvent
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.passcode.PasscodeManager
import com.tonapps.wallet.data.rates.RatesRepository
import com.tonapps.wallet.data.settings.SettingsRepository
import com.tonapps.wallet.data.staking.StakingRepository
import com.tonapps.wallet.data.staking.entities.PoolInfoEntity
import com.tonapps.wallet.data.token.TokenRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import uikit.extensions.collectFlow
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.abs
import kotlin.time.times

class StakingViewModel(
    private val accountRepository: AccountRepository,
    private val stakingRepository: StakingRepository,
    private val tokenRepository: TokenRepository,
    private val ratesRepository: RatesRepository,
    private val passcodeManager: PasscodeManager,
    private val settingsRepository: SettingsRepository,
): ViewModel() {

    data class AvailableUiState(
        val balanceFormat: CharSequence,
        val remainingFormat: CharSequence,
        val minStakeFormat: CharSequence,
        val insufficientBalance: Boolean,
        val requestMinStake: Boolean,
    )

    val poolsFlow = accountRepository.selectedWalletFlow.map { wallet ->
        stakingRepository.pools(wallet.accountId, wallet.testnet)
    }.flowOn(Dispatchers.IO).filter { it.isNotEmpty() }

    private val _amountFlow = MutableStateFlow(0.0)
    private val amountFlow = _amountFlow.map { Coins.of(it) }

    private val _selectedPoolFlow = MutableStateFlow<PoolInfoEntity?>(null)
    val selectedPoolFlow = _selectedPoolFlow.asStateFlow().filterNotNull()

    private val _eventFlow = MutableEffectFlow<StakingEvent>()
    val eventFlow = _eventFlow.asSharedFlow().filterNotNull()

    val tokenFlow = accountRepository.selectedWalletFlow.map { wallet ->
        tokenRepository.get(settingsRepository.currency, wallet.accountId, wallet.testnet)
    }.map { it.firstOrNull() }.filterNotNull().flowOn(Dispatchers.IO)

    private val ratesFlow = tokenFlow.map { token ->
        ratesRepository.getRates(settingsRepository.currency, token.address)
    }.flowOn(Dispatchers.IO)

    val availableUiStateFlow = combine(amountFlow, tokenFlow, selectedPoolFlow) { amount, token, pool ->
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
                insufficientBalance = 0.0 > remaining.value,
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

    val apyFormatFlow = combine(amountFlow, selectedPoolFlow, tokenFlow) { amount, pool, token ->
        if (amount == Coins.ZERO) {
            pool.apyFormat
        } else {
            val earnings = BigDecimal(amount.value)
                .multiply(pool.apy)
                .divide(BigDecimal(100), RoundingMode.HALF_UP)

            val coinsFormat = CurrencyFormatter.format(token.symbol, Coins.of(earnings))
            "${pool.apyFormat} · $coinsFormat"
        }
    }

    init {
        collectFlow(poolsFlow) {
            if (_selectedPoolFlow.value == null) {
                selectPool(it.first())
            }
        }
        updateAmount(0.0)
    }

    fun updateAmount(amount: Double) {
        _amountFlow.value = amount
    }

    fun selectPool(pool: PoolInfoEntity) {
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

}