package com.tonapps.tonkeeper.fragment.chart.list

import android.view.ViewGroup
import com.tonapps.tonkeeper.api.chart.ChartPeriod
import com.tonapps.tonkeeper.fragment.chart.list.holder.ChartActionsHolder
import com.tonapps.tonkeeper.fragment.chart.list.holder.ChartDividerHolder
import com.tonapps.tonkeeper.fragment.chart.list.holder.ChartHeaderHolder
import com.tonapps.tonkeeper.fragment.chart.list.holder.ChartLineHolder
import com.tonapps.tonkeeper.fragment.chart.list.holder.ChartPeriodHolder
import com.tonapps.tonkeeper.fragment.chart.list.holder.ChartPriceHolder

class ChartAdapter(
    private val onPeriodSelected: (period: ChartPeriod) -> Unit,
): com.tonapps.uikit.list.BaseListAdapter() {

    override fun createHolder(parent: ViewGroup, viewType: Int): com.tonapps.uikit.list.BaseListHolder<out com.tonapps.uikit.list.BaseListItem> {
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