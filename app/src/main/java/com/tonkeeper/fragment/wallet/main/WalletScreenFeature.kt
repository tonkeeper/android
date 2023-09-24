package com.tonkeeper.fragment.wallet.main

import androidx.lifecycle.viewModelScope
import com.tonkeeper.App
import com.tonkeeper.ton.SupportedCurrency
import com.tonkeeper.ton.SupportedTokens
import com.tonkeeper.ton.Ton
import com.tonkeeper.ton.WalletInfo
import com.tonkeeper.ton.console.method.AccountMethod
import com.tonkeeper.ton.console.method.RatesMethod
import com.tonkeeper.ton.console.model.AccountModel
import com.tonkeeper.ton.console.model.RatesModel
import com.tonkeeper.uikit.mvi.AsyncState
import com.tonkeeper.uikit.mvi.UiFeature
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DecimalFormat
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

class WalletScreenFeature: UiFeature<WalletScreenState>(WalletScreenState()) {

    private var cachedWallet: WalletInfo? = null
    private var cachedAccount: AccountModel? = null

    init {
        loadWallet()
    }

    private fun loadWallet() {
        updateUiState { currentState ->
            currentState.copy(
                asyncState = AsyncState.Loading
            )
        }

        viewModelScope.launch {
            val wallet = getWallet() ?: return@launch
            val address = "EQDLjf6s4SHpsWokFm31CbiLbnMCz9ELC7zYPKPy9qwgW9d-"
            val account = getAccount(address) ?: return@launch
            val tonBalance = Ton(account.balance)
            val rates = getRates()
            val tonRates = rates.get(SupportedTokens.TON) ?: return@launch
            val prices = tonRates.prices.get(SupportedCurrency.USD) * tonBalance.coins
            val usdCurrency = Currency.getInstance(SupportedCurrency.USD.code)
            val format = NumberFormat.getCurrencyInstance(Locale.US).apply {
                val decimalFormatSymbols = (this as DecimalFormat).decimalFormatSymbols
                decimalFormatSymbols.currencySymbol = usdCurrency.symbol + " "
                this.decimalFormatSymbols = decimalFormatSymbols
            }

            updateUiState { currentState ->
                currentState.copy(
                    asyncState = AsyncState.Default,
                    currency = SupportedCurrency.USD,
                    address = address,
                    tonBalance = tonBalance,
                    displayBalance = format.format(prices),
                )
            }
        }
    }

    private suspend fun getWallet(): WalletInfo? = withContext(Dispatchers.IO) {
        if (cachedWallet == null) {
            cachedWallet = App.walletManager.getWalletInfo()
        }
        cachedWallet
    }

    private suspend fun getAccount(
        address: String
    ): AccountModel? = withContext(Dispatchers.IO) {
        if (cachedAccount == null) {
            cachedAccount = AccountMethod(address).executeOrNull()
        }
        cachedAccount
    }

    private suspend fun getRates(): RatesModel = withContext(Dispatchers.IO) {
        RatesMethod().execute()
    }
}