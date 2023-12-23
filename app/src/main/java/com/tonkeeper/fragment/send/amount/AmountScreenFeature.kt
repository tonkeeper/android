package com.tonkeeper.fragment.send.amount

import androidx.lifecycle.viewModelScope
import com.tonkeeper.App
import com.tonkeeper.api.account.AccountRepository
import com.tonkeeper.api.address
import com.tonkeeper.api.asJettonBalance
import com.tonkeeper.api.jetton.JettonRepository
import com.tonkeeper.api.parsedBalance
import com.tonkeeper.api.symbol
import com.tonkeeper.core.Coin
import com.tonkeeper.core.currency.from
import core.QueueScope
import io.tonapi.models.JettonBalance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ton.SupportedCurrency
import ton.SupportedTokens
import uikit.mvi.UiFeature

class AmountScreenFeature: UiFeature<AmountScreenState, AmountScreenEffect>(AmountScreenState()) {

    private val currency: SupportedCurrency
        get() = App.settings.currency

    private val currentJetton: JettonBalance?
        get() = uiState.value.selectedJetton

    private val currentTokenAddress: String
        get() = currentJetton?.address ?: SupportedTokens.TON.code

    private val currentTokenCode: String
        get() = currentJetton?.symbol ?: SupportedTokens.TON.code

    val currentBalance: Float
        get() = currentJetton?.parsedBalance ?: uiState.value.tonBalance

    private val queueScope = QueueScope(Dispatchers.IO)
    private val accountRepository = AccountRepository()
    private val jettonRepository = JettonRepository()

    init {
        viewModelScope.launch {
            loadData()
        }
    }

    private suspend fun loadData() = withContext(Dispatchers.IO) {
        val wallet = App.walletManager.getWalletInfo()!!
        val accountId = wallet.accountId

        val accountDeferred = async { accountRepository.get(accountId) }
        val jettonsDeferred = async { jettonRepository.get(accountId) }

        val account = accountDeferred.await()?.data ?: return@withContext
        val jettons = jettonsDeferred.await()?.data ?: return@withContext
        val tonAsJetton = account.asJettonBalance()

        updateUiState {
            it.copy(
                wallet = wallet,
                tonBalance = Coin.toCoins(account.balance),
                jettons = listOf(tonAsJetton) + jettons,
                selectedJetton = tonAsJetton,
            )
        }
    }

    fun selectJetton(jettonBalance: JettonBalance) {
        updateUiState {
            it.copy(
                selectedJetton = jettonBalance,
                canContinue = false,
            )
        }

        viewModelScope.launch {
            updateValue(uiState.value.amount)
        }
    }

    private suspend fun updateValue(newValue: Float) {
        val wallet = App.walletManager.getWalletInfo() ?: return
        val accountId = wallet.accountId

        val balanceInCurrency = from(currentTokenAddress, accountId)
            .value(currentBalance)
            .to(currency)

        val insufficientBalance = newValue > currentBalance
        var remaining = ""
        if (newValue > 0) {
            remaining = Coin.format(
                currency = currentTokenCode,
                value = currentBalance - newValue,
                useCurrencyCode = true,
            )
        }

        updateUiState { currentState ->
            currentState.copy(
                rate = Coin.format(currency, balanceInCurrency, true),
                insufficientBalance = insufficientBalance,
                remaining = remaining,
                canContinue = !insufficientBalance && currentBalance > 0 && newValue > 0,
                maxActive = currentBalance == newValue,
                available = Coin.format(
                    currency = currentTokenCode,
                    value = currentBalance,
                    useCurrencyCode = true,
                )
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