package com.tonapps.tonkeeper.fragment.send.amount

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.tonapps.icu.Coins
import com.tonapps.extensions.MutableEffectFlow
import com.tonapps.icu.CurrencyFormatter
import com.tonapps.tonkeeper.App
import com.tonapps.wallet.data.core.WalletCurrency
import com.tonapps.wallet.data.rates.RatesRepository
import com.tonapps.wallet.data.settings.SettingsRepository
import com.tonapps.wallet.data.token.TokenRepository
import com.tonapps.wallet.data.token.entities.AccountTokenEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uikit.mvi.UiFeature

@Deprecated("Need refactoring")
class AmountScreenFeature(
    private val tokenRepository: TokenRepository,
    private val ratesRepository: RatesRepository,
    private val settingsRepository: SettingsRepository,
): UiFeature<AmountScreenState, AmountScreenEffect>(AmountScreenState()) {

    private val currency: WalletCurrency
        get() = settingsRepository.currency

    private val currentToken: AccountTokenEntity?
        get() = uiState.value.selectedToken

    private val currentTokenCode: String
        get() = uiState.value.selectedTokenCode

    val currentTokenBalance: Coins
        get() = currentToken?.balance?.value ?: Coins.ZERO

    val decimals: Int
        get() = currentToken?.decimals ?: 9

    private val _inputValueFlow = MutableEffectFlow<Double>()
    val inputValueFlow = _inputValueFlow.asSharedFlow()

    init {
        viewModelScope.launch {
            loadData()
        }
        setInputValue(0.0)
    }

    fun toggleCurrency() {
        val state = uiState.value
        val fiat = !state.fiat
        settingsRepository.amountInputCurrency = fiat
        updateUiState {
            it.copy(
                fiat = settingsRepository.amountInputCurrency,
                currency = settingsRepository.currency
            )
        }
        setInputValue(0.0)
    }

    fun getAmountFlow(value: Double) = flow {
        val coins = Coins.of(value)
        val state = uiState.value
        val currentTokenAddress = getCurrentTokenAddress()
        val rates = ratesRepository.getRates(currency, currentTokenAddress)
        val balanceInRates = if (state.fiat) {
            rates.convertFromFiat(currentTokenAddress, coins).value
        } else {
            value
        }
        emit(balanceInRates)
    }.take(1).flowOn(Dispatchers.IO)

    fun setMaxValue() {
        viewModelScope.launch(Dispatchers.IO) {
            val state = uiState.value
            if (!state.fiat) {
                setInputValue(currentTokenBalance.value)
            } else {
                val currentTokenAddress = getCurrentTokenAddress()
                val rates = ratesRepository.getRates(currency, currentTokenAddress)
                val fiat = rates.convert(currentTokenAddress, currentTokenBalance)
                setInputValue(fiat.value)
            }
        }
    }

    private fun setInputValue(value: Double) {
        _inputValueFlow.tryEmit(value)
    }

    private fun getCurrentTokenAddress(): String {
        return currentToken?.address ?: "TON"
    }

    private suspend fun loadData() = withContext(Dispatchers.IO) {
        val wallet = App.walletManager.getWalletInfo()!!
        val accountId = wallet.accountId
        val tokens = tokenRepository.get(currency, accountId, wallet.testnet)

        updateUiState {
            it.copy(
                wallet = wallet,
                tokens = tokens,
                selectedTokenAddress = WalletCurrency.TON.code,
                fiat = settingsRepository.amountInputCurrency,
                currency = settingsRepository.currency
            )
        }

        delay(100)
        setInputValue(0.0)
    }

    fun selectToken(tokenAddress: String) {
        updateUiState {
            it.copy(
                selectedTokenAddress = tokenAddress,
                canContinue = false,
            )
        }
    }

    fun selectToken(token: AccountTokenEntity) {
        selectToken(token.address)

        viewModelScope.launch {
            updateValue(uiState.value.amount.value)
        }
    }

    private suspend fun updateValue(newValue: Double) = withContext(Dispatchers.IO) {
        val valueCoins = Coins.of(newValue)
        val currentTokenValue = currentTokenBalance.value
        val state = uiState.value
        val currentTokenAddress = getCurrentTokenAddress()

        val rates = ratesRepository.getRates(currency, currentTokenAddress)
        val balanceInRates = (if (state.fiat) {
            rates.convertFromFiat(currentTokenAddress, valueCoins)
        } else {
            rates.convert(currentTokenAddress, valueCoins)
        }).value

        val valueInToken = if (state.fiat) {
            balanceInRates
        } else {
            newValue
        }

        Log.d("AmountScreenFeature", "updateValue: $newValue ; currentTokenValue: $currentTokenValue")

        val insufficientBalance = valueInToken > currentTokenValue
        val remainingValue = currentTokenValue - valueInToken
        Log.d("AmountScreenFeature", "remainingValue: $remainingValue")
        val remaining = if (valueInToken > 0) {
            CurrencyFormatter.format(currentTokenCode, Coins.of(remainingValue))
        } else {
            ""
        }

        val rate = if (state.fiat) {
            CurrencyFormatter.format(currentTokenCode, Coins.of(balanceInRates))
        } else {
            CurrencyFormatter.formatFiat(currency.code, Coins.of(balanceInRates))
        }

        val available = CurrencyFormatter.format(currentTokenCode, currentTokenBalance)

        updateUiState { currentState ->
            currentState.copy(
                rate = rate,
                insufficientBalance = insufficientBalance,
                remaining = remaining,
                canContinue = !insufficientBalance && currentTokenBalance.isPositive && valueInToken > 0,
                maxActive = currentTokenValue == valueInToken,
                available = available,
                currency = settingsRepository.currency,
                fiat = settingsRepository.amountInputCurrency
            )
        }
    }

    fun setValue(value: Double) {
        updateUiState { currentState ->
            currentState.copy(
                canContinue = false,
                currency = settingsRepository.currency,
                fiat = settingsRepository.amountInputCurrency
            )
        }

        viewModelScope.launch {
            updateValue(value)
        }
    }
}