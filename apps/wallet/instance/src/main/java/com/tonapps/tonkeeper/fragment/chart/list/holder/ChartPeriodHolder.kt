package com.tonapps.tonkeeper.fragment.chart.list.holder

import android.view.ViewGroup
import com.tonapps.tonkeeperx.R
import com.tonapps.tonkeeper.api.chart.ChartPeriod
import com.tonapps.tonkeeper.fragment.chart.list.ChartItem
import com.tonapps.tonkeeper.view.ChartPeriodView

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