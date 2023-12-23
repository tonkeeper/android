package com.tonkeeper.fragment.chart.list.holder

import android.view.ViewGroup
import com.tonkeeper.R
import com.tonkeeper.fragment.chart.list.ChartItem
import com.tonkeeper.view.ChartView

class ChartLineHolder(
    parent: ViewGroup
): ChartHolder<ChartItem.Chart>(parent, R.layout.view_chart_line) {

    private val chartView = findViewById<ChartView>(R.id.chart)

    override fun onBind(item: ChartItem.Chart) {
        chartView.data = item.data
    }

}