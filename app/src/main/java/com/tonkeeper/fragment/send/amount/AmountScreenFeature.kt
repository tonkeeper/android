package com.tonkeeper.fragment.send.amount

import androidx.lifecycle.viewModelScope
import com.tonkeeper.App
import com.tonkeeper.api.account.AccountRepository
import com.tonkeeper.api.jetton.JettonRepository
import com.tonkeeper.core.Coin
import com.tonkeeper.core.currency.from
import core.QueueScope
import io.tonapi.models.Account
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

    private var selectedToken = SupportedTokens.TON.code
    private var walletData: WalletData? = null

    private val queueScope = QueueScope(Dispatchers.IO)
    private val accountRepository = AccountRepository()
    private val jettonRepository = JettonRepository()

    init {
        viewModelScope.launch { initWallet() }
    }

    suspend fun getMaxValue(): Float {
        return Coin.toCoins(getWalletData().account.balance)
    }

    fun setValue(value: Float) {
        updateUiState { currentState ->
            currentState.copy(
                canContinue = false
            )
        }

        queueScope.submit {
            val data = getWalletData()
            val balance = Coin.toCoins(data.account.balance)

            val tonInCurrency = getTonInCurrency(data.accountId, value)
            val insufficientBalance = value > balance
            val remaining = if (value > 0) {
                Coin.format(
                    currency = selectedToken,
                    value = balance - value
                )
            } else ""

            updateUiState { currentState ->
                currentState.copy(
                    rate = getRateDisplay(tonInCurrency),
                    insufficientBalance = insufficientBalance,
                    remaining = remaining,
                    canContinue = !insufficientBalance && value > 0,
                    maxActive = balance == value
                )
            }
        }
    }

    private suspend fun getTonInCurrency(accountId: String, value: Float): Float {
        return from(SupportedTokens.TON, accountId)
            .value(value)
            .to(currency)
    }

    private fun getRateDisplay(value: Float): String {
        return Coin.format(currency, value, true)
    }

    private fun getAvailableDisplay(balance: Long): String {
        return Coin.format(
            currency = selectedToken,
            value = balance
        )
    }

    private suspend fun initWallet() = withContext(Dispatchers.IO) {
        val data = getWalletData()
        val tonInCurrency = getTonInCurrency(data.accountId, uiState.value.value)

        updateUiState { currentState ->
            currentState.copy(
                available = getAvailableDisplay(data.account.balance),
                rate = getRateDisplay(tonInCurrency),
                canContinue = false
            )
        }
    }

    private suspend fun getWalletData(
    ): WalletData = withContext(Dispatchers.IO) {
        if (walletData == null) {
            val wallet = App.walletManager.getWalletInfo()!!
            val address = wallet.address

            val accountDeferred = async { accountRepository.get(wallet.accountId) }
            val jettonsDeferred = async { jettonRepository.get(wallet.accountId) }

            val account = accountDeferred.await()
            val jettons = jettonsDeferred.await()

            walletData = WalletData(
                accountId = address,
                account = account,
                jettons = jettons,
            )
        }

        return@withContext walletData!!
    }

    override fun onCleared() {
        super.onCleared()
        queueScope.cancel()
    }

    private data class WalletData(
        val accountId: String,
        val account: Account,
        val jettons: List<JettonBalance>
    )
}