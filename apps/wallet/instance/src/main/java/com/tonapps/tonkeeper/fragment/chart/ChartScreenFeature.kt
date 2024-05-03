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
import com.tonapps.tonkeeper.core.history.HistoryHelper
import com.tonapps.tonkeeper.core.history.list.item.HistoryItem
import com.tonapps.wallet.api.API
import com.tonapps.wallet.data.core.WalletCurrency
import core.QueueScope
import io.tonapi.models.AccountEvents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import com.tonapps.wallet.data.account.legacy.WalletLegacy
import com.tonapps.wallet.data.rates.RatesRepository
import uikit.mvi.AsyncState
import uikit.mvi.UiFeature

@Deprecated("Need refactoring")
class ChartScreenFeature(
    private val historyHelper: HistoryHelper,
    private val ratesRepository: RatesRepository,
    private val api: API,
): UiFeature<ChartScreenState, ChartScreenEffect>(ChartScreenState()) {

    private val currency: WalletCurrency
        get() = App.settings.currency

    private val accountRepository = AccountRepository()
    private val accountsApi = Tonapi.accounts
    private val queueScope = QueueScope(viewModelScope.coroutineContext)
    private var lastLt: Long? = null

    fun load() {
        queueScope.submit {
            val wallet = App.walletManager.getWalletInfo() ?: return@submit
            val accountId = wallet.accountId

            val accountDeferred = async { accountRepository.get(accountId, wallet.testnet)?.data }
            val chartDeferred = async { ChartHelper.getEntity(uiState.value.chartPeriod) }
            val historyItemsDeferred = async { getEvents(wallet) }

            val account = accountDeferred.await() ?: return@submit
            val chart = chartDeferred.await()
            val historyItems = historyItemsDeferred.await()

            val balance = Coin.toCoins(account.balance)
            val rates = ratesRepository.getRates(currency, "TON")

            val currencyBalance = rates.convert("TON", balance)

            val rate = rates.getRate("TON")
            val rate24h = rates.getDiff24h("TON")

            updateUiState {
                it.copy(
                    disableSwap = api.config.flags.disableSwap,
                    disableBuyOrSell = api.config.flags.disableExchangeMethods,
                    swapUri = api.config.swapUri,
                    address = wallet.address,
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
                    historyItems = historyHelper.withLoadingItem(it.historyItems)
                )
            }

            val wallet = App.walletManager.getWalletInfo() ?: return@submit
            val historyItems = getEvents(wallet, lt)
            val items = historyHelper.removeLoadingItem(uiState.value.historyItems) + historyItems

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
        historyHelper.mapping(wallet, events.filter {
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