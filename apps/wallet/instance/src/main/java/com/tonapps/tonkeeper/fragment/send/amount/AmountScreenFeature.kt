package com.tonapps.tonkeeper.fragment.send.amount

import androidx.lifecycle.viewModelScope
import com.tonapps.icu.CurrencyFormatter
import com.tonapps.tonkeeper.App
import com.tonapps.tonkeeper.core.currency.from
import com.tonapps.wallet.data.core.WalletCurrency
import com.tonapps.wallet.data.token.TokenRepository
import com.tonapps.wallet.data.token.entities.AccountTokenEntity
import core.QueueScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uikit.mvi.UiFeature

@Deprecated("Need refactoring")
class AmountScreenFeature(
    private val tokenRepository: TokenRepository
): UiFeature<AmountScreenState, AmountScreenEffect>(AmountScreenState()) {

    private val currency: WalletCurrency
        get() = App.settings.currency

    private val currentToken: AccountTokenEntity?
        get() = uiState.value.selectedToken

    private val currentTokenCode: String
        get() = uiState.value.selectedTokenCode

    val currentBalance: Float
        get() = currentToken?.balance?.value ?: 0f

    val decimals: Int
        get() = currentToken?.decimals ?: 9

    private val queueScope = QueueScope(Dispatchers.IO)

    init {
        viewModelScope.launch {
            loadData()
        }
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
            )
        }
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
            updateValue(uiState.value.amount)
        }
    }

    private suspend fun updateValue(newValue: Float) {
        val wallet = App.walletManager.getWalletInfo() ?: return
        val accountId = wallet.accountId
        val currentTokenAddress = getCurrentTokenAddress()

        val balanceInCurrency = from(currentTokenAddress, accountId, wallet.testnet)
            .value(currentBalance)
            .convert(currency.code)

        val insufficientBalance = newValue > currentBalance
        var remaining = ""
        if (newValue > 0) {
            val value = currentBalance - newValue
            remaining = CurrencyFormatter.format(currentTokenCode, value)
        }

        updateUiState { currentState ->
            currentState.copy(
                rate = CurrencyFormatter.formatFiat(currency.code, balanceInCurrency),
                insufficientBalance = insufficientBalance,
                remaining = remaining,
                canContinue = !insufficientBalance && currentBalance > 0 && newValue > 0,
                maxActive = currentBalance == newValue,
                available = CurrencyFormatter.format(currentTokenCode, currentBalance)
            )
        }
    }

    fun setValue(value: Float) {
        updateUiState { currentState ->
            currentState.copy(
                canContinue = false
            )
        }

        viewModelScope.launch {
            updateValue(value)
        }
    }

    override fun onCleared() {
        super.onCleared()
        queueScope.cancel()
    }
}