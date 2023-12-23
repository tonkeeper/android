package com.tonkeeper.fragment.chart

import androidx.lifecycle.viewModelScope
import com.tonkeeper.App
import com.tonkeeper.api.Tonapi
import com.tonkeeper.api.account.AccountRepository
import com.tonkeeper.api.address
import com.tonkeeper.api.chart.ChartHelper
import com.tonkeeper.api.chart.ChartPeriod
import com.tonkeeper.api.jetton.JettonRepository
import com.tonkeeper.api.withRetry
import com.tonkeeper.core.Coin
import com.tonkeeper.core.currency.CurrencyManager
import com.tonkeeper.core.currency.from
import com.tonkeeper.core.history.HistoryHelper
import com.tonkeeper.core.history.list.item.HistoryItem
import core.QueueScope
import io.tonapi.models.AccountEvents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ton.SupportedCurrency
import ton.SupportedTokens
import ton.wallet.Wallet
import uikit.mvi.AsyncState
import uikit.mvi.UiFeature

class ChartScreenFeature: UiFeature<ChartScreenState, ChartScreenEffect>(ChartScreenState()) {

    private val currencyManager = CurrencyManager.getInstance()
    private val currency: SupportedCurrency
        get() = App.settings.currency

    private val accountRepository = AccountRepository()
    private val accountsApi = Tonapi.accounts
    private val queueScope = QueueScope(viewModelScope.coroutineContext)

    fun load() {
        queueScope.submit {
            val wallet = App.walletManager.getWalletInfo() ?: return@submit
            val accountId = wallet.accountId
            val account = accountRepository.get(accountId)?.data ?: return@submit
            val balance = account.balance
            val currencyBalance = from(SupportedTokens.TON, accountId).value(balance).to(currency)

            val rate = currencyManager.getRate(accountId, SupportedTokens.TON, currency)
            val rate24h = currencyManager.getRate24h(accountId, SupportedTokens.TON, currency)

            val chart = ChartHelper.getEntity(ChartPeriod.week)
            val historyItems = getEvents(wallet)

            updateUiState {
                it.copy(
                    asyncState = AsyncState.Default,
                    balance = Coin.format(value = balance, decimals = 12),
                    currencyBalance = Coin.format(currency, currencyBalance),
                    rateFormat = Coin.format(currency, rate),
                    rate24h = rate24h,
                    historyItems = historyItems,
                    chart = chart
                )
            }
        }
    }

    fun loadChart(period: ChartPeriod) {
        queueScope.submit {
            val chart = ChartHelper.getEntity(period)

            updateUiState {
                it.copy(
                    chart = chart
                )
            }
        }
    }

    private suspend fun getEvents(
        wallet: Wallet
    ): List<HistoryItem> = withContext(Dispatchers.IO) {
        val accountId = wallet.accountId
        val events = getAccountEvent(accountId) ?: return@withContext emptyList()
        HistoryHelper.mapping(wallet, events)
    }

    private suspend fun getAccountEvent(accountId: String): AccountEvents? {
        return withRetry {
            accountsApi.getAccountEvents(accountId = accountId, limit = 30, subjectOnly = true)
        }
    }

    override fun onCleared() {
        super.onCleared()
        queueScope.cancel()
    }
}