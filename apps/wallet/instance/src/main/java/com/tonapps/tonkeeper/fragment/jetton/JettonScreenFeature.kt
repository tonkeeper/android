package com.tonapps.tonkeeper.fragment.jetton

import androidx.lifecycle.viewModelScope
import com.tonapps.tonkeeper.App
import com.tonapps.tonkeeper.api.Tonapi
import com.tonapps.tonkeeper.api.getAddress
import com.tonapps.tonkeeper.api.jetton.JettonRepository
import com.tonapps.tonkeeper.api.parsedBalance
import com.tonapps.tonkeeper.api.withRetry
import com.tonapps.tonkeeper.core.currency.CurrencyManager
import com.tonapps.tonkeeper.core.currency.currency
import com.tonapps.tonkeeper.core.history.HistoryHelper
import com.tonapps.tonkeeper.core.history.list.item.HistoryItem
import com.tonapps.wallet.data.core.Currency
import core.QueueScope
import core.formatter.CurrencyFormatter
import io.tonapi.models.AccountEvents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import ton.wallet.Wallet
import uikit.mvi.AsyncState
import uikit.mvi.UiFeature

class JettonScreenFeature: UiFeature<JettonScreenState, JettonScreenEffect>(JettonScreenState()) {

    private val jettonRepository = JettonRepository()
    private val currencyManager = CurrencyManager.getInstance()
    private val accountsApi = Tonapi.accounts
    private val queueScope = QueueScope(viewModelScope.coroutineContext)

    private val currency: Currency
        get() = App.settings.currency

    fun loadMore(address: String, lt: Long) {
        queueScope.submit(Dispatchers.IO) {
            delay(1000)

            updateUiState { currentState ->
                currentState.copy(
                    historyItems = HistoryHelper.withLoadingItem(currentState.historyItems)
                )
            }

            val wallet = com.tonapps.tonkeeper.App.walletManager.getWalletInfo() ?: return@submit
            val events = getEvents(wallet, address, lt)

            val items = HistoryHelper.removeLoadingItem(uiState.value.historyItems) + events

            updateUiState { currentState ->
                currentState.copy(
                    walletType = wallet.type,
                    historyItems = items,
                    loadedAll = events.isEmpty()
                )
            }
        }
    }

    fun load(address: String) {
        queueScope.submit(Dispatchers.IO) {
            val wallet = com.tonapps.tonkeeper.App.walletManager.getWalletInfo() ?: return@submit
            val accountId = wallet.accountId
            val jetton = jettonRepository.getByAddress(accountId, address, wallet.testnet) ?: return@submit
            val balance = jetton.parsedBalance
            val jettonAddress = jetton.getAddress(wallet.testnet)
            val currencyBalance = wallet.currency(jettonAddress).value(balance).convert(currency.code)
            val rate = currencyManager.getRate(accountId, wallet.testnet, address, currency.code)
            val rate24h = currencyManager.getRate24h(accountId, wallet.testnet, address, currency.code)
            val historyItems = getEvents(wallet, jettonAddress)

            updateUiState {
                it.copy(
                    walletType = wallet.type,
                    asyncState = AsyncState.Default,
                    jetton = jetton,
                    currencyBalance = CurrencyFormatter.formatFiat(currency.code, currencyBalance),
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
        val events = getAccountEvent(accountId, wallet.testnet, jettonAddress, beforeLt) ?: return@withContext emptyList()
        HistoryHelper.mapping(wallet, events)
    }

    private suspend fun getAccountEvent(
        accountId: String,
        testnet: Boolean,
        jettonAddress: String,
        beforeLt: Long? = null
    ): AccountEvents? {
        return withRetry {
            accountsApi.get(testnet).getAccountJettonHistoryByID(
                accountId = accountId,
                jettonId = jettonAddress,
                beforeLt = beforeLt,
                limit = HistoryHelper.EVENT_LIMIT
            )
        }
    }
}