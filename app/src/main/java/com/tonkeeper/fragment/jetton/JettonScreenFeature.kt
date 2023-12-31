package com.tonkeeper.fragment.jetton

import androidx.lifecycle.viewModelScope
import com.tonkeeper.App
import com.tonkeeper.api.Tonapi
import com.tonkeeper.api.address
import com.tonkeeper.api.jetton.JettonRepository
import com.tonkeeper.api.parsedBalance
import com.tonkeeper.api.withRetry
import com.tonkeeper.core.formatter.CurrencyFormatter
import com.tonkeeper.core.currency.CurrencyManager
import com.tonkeeper.core.currency.from
import com.tonkeeper.core.history.HistoryHelper
import com.tonkeeper.core.history.list.item.HistoryItem
import core.QueueScope
import io.tonapi.models.AccountEvent
import io.tonapi.models.AccountEvents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
    private val queueScope = QueueScope(viewModelScope.coroutineContext)

    private val currency: SupportedCurrency
        get() = App.settings.currency

    fun loadMore(address: String, lt: Long) {
        queueScope.submit(Dispatchers.IO) {
            delay(1000)

            updateUiState { currentState ->
                currentState.copy(
                    historyItems = HistoryHelper.withLoadingItem(currentState.historyItems)
                )
            }

            val wallet = App.walletManager.getWalletInfo() ?: return@submit
            val events = getEvents(wallet, address, lt)

            val items = HistoryHelper.removeLoadingItem(uiState.value.historyItems) + events

            updateUiState { currentState ->
                currentState.copy(
                    historyItems = items
                )
            }
        }
    }

    fun load(address: String) {
        queueScope.submit(Dispatchers.IO) {
            val wallet = App.walletManager.getWalletInfo() ?: return@submit
            val accountId = wallet.accountId
            val jetton = jettonRepository.getByAddress(accountId, address) ?: return@submit
            val balance = jetton.parsedBalance
            val currencyBalance = from(jetton.address, accountId).value(balance).to(currency)
            val rate = currencyManager.getRate(accountId, address, currency.code)
            val rate24h = currencyManager.getRate24h(accountId, address, currency.code)
            val historyItems = getEvents(wallet, jetton.address)

            updateUiState {
                it.copy(
                    asyncState = AsyncState.Default,
                    jetton = jetton,
                    currencyBalance = CurrencyFormatter.formatFiat(currencyBalance),
                    rateFormat = CurrencyFormatter.formatRate(currency.code, rate),
                    rate24h = rate24h,
                    historyItems = historyItems
                )
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        queueScope.cancel()
    }

    private suspend fun getEvents(
        wallet: Wallet,
        jettonAddress: String,
        beforeLt: Long? = null
    ): List<HistoryItem> = withContext(Dispatchers.IO) {
        val accountId = wallet.accountId
        val events = getAccountEvent(accountId, jettonAddress, beforeLt) ?: return@withContext emptyList()
        HistoryHelper.mapping(wallet, events)
    }

    private suspend fun getAccountEvent(
        accountId: String,
        jettonAddress: String,
        beforeLt: Long? = null
    ): AccountEvents? {
        return withRetry {
            accountsApi.getAccountJettonHistoryByID(accountId = accountId, jettonId = jettonAddress, beforeLt = beforeLt, limit = 30)
        }
    }
}