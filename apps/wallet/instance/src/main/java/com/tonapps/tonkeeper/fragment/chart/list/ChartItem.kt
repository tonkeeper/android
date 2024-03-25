package com.tonapps.tonkeeper.fragment.chart.list

import com.tonapps.tonkeeper.api.chart.ChartEntity
import com.tonapps.tonkeeper.api.chart.ChartPeriod
import com.tonapps.wallet.data.account.WalletType

sealed class ChartItem(
    type: Int
): com.tonapps.uikit.list.BaseListItem(type) {

    companion object {
        const val TYPE_HEADER = 0
        const val TYPE_ACTIONS = 1
        const val TYPE_CHART = 2
        const val TYPE_DIVIDER = 3
        const val TYPE_PRICE = 4
        const val TYPE_PERIOD = 5
    }

    data class Header(
        val balance: String,
        val currencyBalance: String,
    ): ChartItem(TYPE_HEADER)

    data class Actions(
        val walletType: WalletType
    ): ChartItem(TYPE_ACTIONS)

    data object Period: ChartItem(TYPE_PERIOD)

    data class Chart(
        val period: ChartPeriod,
        val data: List<ChartEntity>
    ): ChartItem(TYPE_CHART)

    data class Price(
        val rateFormat: String,
        val rate24h: String
    ): ChartItem(TYPE_PRICE)

    data object Divider: ChartItem(TYPE_DIVIDER)
}