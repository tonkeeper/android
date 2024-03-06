package com.tonapps.tonkeeper.fragment.send.amount

import androidx.lifecycle.viewModelScope
import com.tonapps.icu.CurrencyFormatter
import com.tonapps.tonkeeper.App
import com.tonapps.tonkeeper.api.account.AccountRepository
import com.tonapps.tonkeeper.api.asJettonBalance
import com.tonapps.tonkeeper.api.getAddress
import com.tonapps.tonkeeper.api.jetton.JettonRepository
import com.tonapps.tonkeeper.api.parsedBalance
import com.tonapps.tonkeeper.api.symbol
import com.tonapps.blockchain.Coin
import com.tonapps.tonkeeper.core.currency.from
import com.tonapps.wallet.data.core.WalletCurrency
import core.QueueScope
import io.tonapi.models.JettonBalance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uikit.mvi.UiFeature

class AmountScreenFeature: UiFeature<AmountScreenState, AmountScreenEffect>(AmountScreenState()) {

    private val currency: WalletCurrency
        get() = App.settings.currency

    private val currentJetton: JettonBalance?
        get() = uiState.value.selectedJetton

    private val currentTokenCode: String
        get() = currentJetton?.symbol ?: "TON"

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

    private fun getCurrentTokenAddress(testnet: Boolean): String {
        val jetton = currentJetton ?: return "TON"
        return jetton.getAddress(testnet)
    }

    private suspend fun loadData() = withContext(Dispatchers.IO) {
        val wallet = App.walletManager.getWalletInfo()!!
        val accountId = wallet.accountId

        val accountDeferred = async { accountRepository.get(accountId, wallet.testnet) }
        val jettonsDeferred = async { jettonRepository.get(accountId, wallet.testnet) }

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

    fun selectJetton(jettonAddress: String) {
        viewModelScope.launch {
            val wallet = com.tonapps.tonkeeper.App.walletManager.getWalletInfo() ?: return@launch
            val jettonBalance = jettonRepository.getByAddress(wallet.accountId, jettonAddress, wallet.testnet) ?: return@launch

            updateUiState {
                it.copy(
                    selectedJetton = jettonBalance,
                    decimals = jettonBalance.jetton.decimals,
                    canContinue = false,
                )
            }
        }
    }

    fun selectJetton(jettonBalance: JettonBalance) {
        updateUiState {
            it.copy(
                selectedJetton = jettonBalance,
                decimals = jettonBalance.jetton.decimals,
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
        val currentTokenAddress = getCurrentTokenAddress(wallet.testnet)

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