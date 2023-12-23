package com.tonkeeper.fragment.chart.list

import android.view.ViewGroup
import com.tonkeeper.api.chart.ChartPeriod
import com.tonkeeper.fragment.chart.list.holder.ChartActionsHolder
import com.tonkeeper.fragment.chart.list.holder.ChartDividerHolder
import com.tonkeeper.fragment.chart.list.holder.ChartHeaderHolder
import com.tonkeeper.fragment.chart.list.holder.ChartLineHolder
import com.tonkeeper.fragment.chart.list.holder.ChartPeriodHolder
import com.tonkeeper.fragment.chart.list.holder.ChartPriceHolder
import uikit.list.BaseListAdapter
import uikit.list.BaseListHolder
import uikit.list.BaseListItem

class ChartAdapter(
    private val onPeriodSelected: (period: ChartPeriod) -> Unit,
): BaseListAdapter() {

    override fun createHolder(parent: ViewGroup, viewType: Int): BaseListHolder<out BaseListItem> {
        return when (viewType) {
            ChartItem.TYPE_HEADER -> ChartHeaderHolder(parent)
            ChartItem.TYPE_ACTIONS -> ChartActionsHolder(parent)
            ChartItem.TYPE_CHART -> ChartLineHolder(parent)
            ChartItem.TYPE_DIVIDER -> ChartDividerHolder(parent)
            ChartItem.TYPE_PRICE -> ChartPriceHolder(parent)
            ChartItem.TYPE_PERIOD -> ChartPeriodHolder(parent, onPeriodSelected)
            else -> throw IllegalArgumentException("Unknown viewType: $viewType")
        }
    }

}