package com.tonapps.tonkeeper.fragment.chart

import androidx.lifecycle.viewModelScope
import com.tonapps.icu.CurrencyFormatter
import com.tonapps.tonkeeper.App
import com.tonapps.wallet.api.Tonapi
import com.tonapps.tonkeeper.api.account.AccountRepository
import com.tonapps.tonkeeper.api.chart.ChartHelper
import com.tonapps.tonkeeper.api.chart.ChartPeriod
import com.tonapps.tonkeeper.api.withRetry
import com.tonapps.tonkeeper.api.withTON
import com.tonapps.blockchain.Coin
import com.tonapps.tonkeeper.core.currency.CurrencyManager
import com.tonapps.tonkeeper.core.currency.ton
import com.tonapps.tonkeeper.core.history.HistoryHelper
import com.tonapps.tonkeeper.core.history.list.item.HistoryItem
import com.tonapps.wallet.data.core.WalletCurrency
import core.QueueScope
import io.tonapi.models.AccountEvents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import com.tonapps.wallet.data.account.legacy.WalletLegacy
import uikit.mvi.AsyncState
import uikit.mvi.UiFeature

class ChartScreenFeature: UiFeature<ChartScreenState, ChartScreenEffect>(ChartScreenState()) {

    private val currencyManager = CurrencyManager.getInstance()
    private val currency: WalletCurrency
        get() = App.settings.currency

    private val accountRepository = AccountRepository()
    private val accountsApi = Tonapi.accounts
    private val queueScope = QueueScope(viewModelScope.coroutineContext)
    private var lastLt: Long? = null

    fun load() {
        queueScope.submit {
            val wallet = com.tonapps.tonkeeper.App.walletManager.getWalletInfo() ?: return@submit
            val accountId = wallet.accountId

            val accountDeferred = async { accountRepository.get(accountId, wallet.testnet)?.data }
            val chartDeferred = async { ChartHelper.getEntity(uiState.value.chartPeriod) }
            val historyItemsDeferred = async { getEvents(wallet) }

            val account = accountDeferred.await() ?: return@submit
            val chart = chartDeferred.await()
            val historyItems = historyItemsDeferred.await()

            val balance = Coin.toCoins(account.balance)
            val currencyBalance = wallet.ton(balance).convert(currency.code)

            val rate = currencyManager.getRate(accountId, wallet.testnet, "TON", currency.code)
            val rate24h = currencyManager.getRate24h(accountId, wallet.testnet, "TON", currency.code)

            updateUiState {
                it.copy(
                    walletType = wallet.type,
                    asyncState = AsyncState.Default,
                    balance = CurrencyFormatter.format("TON", balance),
                    currencyBalance = CurrencyFormatter.formatFiat(currency.code, currencyBalance),
                    rateFormat = CurrencyFormatter.formatRate(currency.code, rate),
                    rate24h = rate24h,
                    historyItems = historyItems,
                    chart = chart
                )
            }
        }
    }

    fun loadMore() {
        queueScope.submit {
            val lt = lastLt ?: return@submit
            if (lt == 0L) {
                return@submit
            }

            updateUiState {
                it.copy(
                    historyItems = HistoryHelper.withLoadingItem(it.historyItems)
                )
            }

            val wallet = App.walletManager.getWalletInfo() ?: return@submit
            val historyItems = getEvents(wallet, lt)
            val items = HistoryHelper.removeLoadingItem(uiState.value.historyItems) + historyItems

            updateUiState {
                it.copy(
                    walletType = wallet.type,
                    historyItems = items,
                    loadedAll = historyItems.isEmpty()
                )
            }
        }
    }

    fun loadChart(period: ChartPeriod) {
        queueScope.submit {
            val chart = ChartHelper.getEntity(period)

            updateUiState {
                it.copy(
                    chart = chart,
                    chartPeriod = period
                )
            }
        }
    }

    private suspend fun getEvents(
        wallet: WalletLegacy,
        beforeLt: Long? = null
    ): List<HistoryItem> = withContext(Dispatchers.IO) {
        val accountId = wallet.accountId
        val events = getAccountEvent(accountId, wallet.testnet, beforeLt)?.events ?: return@withContext emptyList()
        lastLt = events.lastOrNull()?.lt
        HistoryHelper.mapping(wallet, events.filter {
            it.withTON
        })
    }

    private suspend fun getAccountEvent(
        accountId: String,
        testnet: Boolean,
        beforeLt: Long?,
    ): AccountEvents? {
        return withRetry {
            accountsApi.get(testnet).getAccountEvents(accountId = accountId, limit = 100, subjectOnly = true, beforeLt = beforeLt)
        }
    }

    override fun onCleared() {
        super.onCleared()
        queueScope.cancel()
    }
}