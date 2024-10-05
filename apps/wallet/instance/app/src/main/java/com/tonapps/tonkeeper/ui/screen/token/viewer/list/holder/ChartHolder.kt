package com.tonapps.tonkeeper.ui.screen.token.viewer.list.holder

import android.view.ViewGroup
import com.tonapps.tonkeeper.ui.screen.token.viewer.list.Item
import com.tonapps.tonkeeper.view.ChartPeriodView
import com.tonapps.tonkeeper.view.ChartView
import com.tonapps.tonkeeperx.R
import com.tonapps.wallet.data.settings.ChartPeriod

class ChartHolder(
    parent: ViewGroup,
    private val chartPeriodCallback: (ChartPeriod) -> Unit
): Holder<Item.Chart>(parent, R.layout.view_token_chart) {

    private val chartView = findViewById<ChartView>(R.id.chart)
    private val periodView = findViewById<ChartPeriodView>(R.id.period)

    override fun onBind(item: Item.Chart) {
        chartView.setData(item.data, item.square)
        periodView.selectedPeriod = item.period
        periodView.doOnPeriodSelected = chartPeriodCallback
    }

}