package com.tonkeeper.fragment.jetton

import androidx.lifecycle.viewModelScope
import com.tonkeeper.App
import com.tonkeeper.api.Tonapi
import com.tonkeeper.api.address
import com.tonkeeper.api.jetton.JettonRepository
import com.tonkeeper.api.parsedBalance
import com.tonkeeper.core.Coin
import com.tonkeeper.core.currency.CurrencyManager
import com.tonkeeper.core.currency.from
import com.tonkeeper.core.history.HistoryHelper
import com.tonkeeper.core.history.list.item.HistoryItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ton.SupportedCurrency
import ton.wallet.Wallet
import uikit.mvi.AsyncState
import uikit.mvi.UiFeature

class JettonScreenFeature: UiFeature<JettonScreenState, JettonScreenEffect>(JettonScreenState()) {

    private val jettonRepository = JettonRepository()
    private val currencyManager = CurrencyManager.getInstance()
    private val accountsApi = Tonapi.accounts

    private val currency: SupportedCurrency
        get() = App.settings.currency

    fun load(address: String) {
        viewModelScope.launch {
            val wallet = App.walletManager.getWalletInfo() ?: return@launch
            val accountId = wallet.accountId
            val jetton = jettonRepository.getByAddress(accountId, address) ?: return@launch
            val balance = jetton.parsedBalance
            val currencyBalance = from(jetton.address, accountId).value(balance).to(currency)
            val rate = currencyManager.getRate(accountId, address, currency.code)
            val rate24h = currencyManager.getRate24h(accountId, address, currency.code)
            val historyItems = getEvents(wallet, jetton.address)

            updateUiState {
                it.copy(
                    asyncState = AsyncState.Default,
                    jetton = jetton,
                    currencyBalance = Coin.format(currency, currencyBalance),
                    rateFormat = Coin.format(currency, rate),
                    rate24h = rate24h,
                    historyItems = historyItems
                )
            }
        }
    }

    private suspend fun getEvents(
        wallet: Wallet,
        jettonAddress: String
    ): List<HistoryItem> = withContext(Dispatchers.IO) {
        val accountId = wallet.accountId
        val events = accountsApi.getAccountJettonHistoryByID(accountId = accountId, jettonId = jettonAddress, limit = 100)
        HistoryHelper.mapping(wallet, events)
    }
}