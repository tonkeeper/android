package com.tonkeeper.fragment.chart

import com.tonkeeper.api.chart.ChartEntity
import com.tonkeeper.api.chart.ChartPeriod
import com.tonkeeper.core.history.list.item.HistoryItem
import com.tonkeeper.fragment.chart.list.ChartItem
import ton.wallet.WalletType
import uikit.mvi.AsyncState
import uikit.mvi.UiState

data class ChartScreenState(
    val walletType: WalletType = WalletType.Default,
    val asyncState: AsyncState = AsyncState.Loading,
    val chart: List<ChartEntity> = emptyList(),
    val chartPeriod: ChartPeriod = ChartPeriod.week,
    val balance: String = "",
    val currencyBalance: String = "",
    val rateFormat: String = "",
    val rate24h: String = "",
    val historyItems: List<HistoryItem> = emptyList(),
    val loadedAll: Boolean = false,
): UiState() {


    fun getTopItems(): List<ChartItem> {
        val items = mutableListOf<ChartItem>()
        items.add(ChartItem.Header(balance, currencyBalance))
        items.add(ChartItem.Divider)
        items.add(ChartItem.Actions(walletType))
        items.add(ChartItem.Divider)
        items.add(ChartItem.Price(rateFormat, rate24h))
        items.add(ChartItem.Chart(chartPeriod, chart))
        items.add(ChartItem.Period)
        items.add(ChartItem.Divider)
        return items
    }
}