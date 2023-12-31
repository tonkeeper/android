package com.tonkeeper.fragment.chart.list.holder

import android.view.ViewGroup
import com.tonapps.tonkeeperx.R
import com.tonkeeper.api.chart.ChartPeriod
import com.tonkeeper.fragment.chart.list.ChartItem
import com.tonkeeper.view.ChartPeriodView

class ChartPeriodHolder(
    parent: ViewGroup,
    private val onPeriodSelected: (period: ChartPeriod) -> Unit,
): ChartHolder<ChartItem.Period>(parent, R.layout.view_chart_period) {

    private val periodView = findViewById<ChartPeriodView>(R.id.period)

    init {
        periodView.doOnPeriodSelected = onPeriodSelected
    }

    override fun onBind(item: ChartItem.Period) {

    }

}