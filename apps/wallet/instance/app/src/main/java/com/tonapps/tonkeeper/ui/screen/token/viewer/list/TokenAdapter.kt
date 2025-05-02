package com.tonapps.tonkeeper.ui.screen.token.viewer.list

import android.view.ViewGroup
import com.tonapps.tonkeeper.ui.screen.token.viewer.list.holder.ActionsHolder
import com.tonapps.tonkeeper.ui.screen.token.viewer.list.holder.BalanceHolder
import com.tonapps.tonkeeper.ui.screen.token.viewer.list.holder.BatteryBannerHolder
import com.tonapps.tonkeeper.ui.screen.token.viewer.list.holder.ChartHolder
import com.tonapps.tonkeeper.ui.screen.token.viewer.list.holder.W5BannerHolder
import com.tonapps.uikit.list.BaseListAdapter
import com.tonapps.uikit.list.BaseListHolder
import com.tonapps.uikit.list.BaseListItem
import com.tonapps.wallet.data.settings.ChartPeriod

class TokenAdapter(
    private val chartPeriodCallback: (ChartPeriod) -> Unit,
): BaseListAdapter() {
    override fun createHolder(parent: ViewGroup, viewType: Int): BaseListHolder<out BaseListItem> {
        return when(viewType) {
            Item.TYPE_BALANCE -> BalanceHolder(parent)
            Item.TYPE_ACTIONS -> ActionsHolder(parent)
            Item.TYPE_CHART -> ChartHolder(parent, chartPeriodCallback)
            Item.TYPE_W5_BANNER -> W5BannerHolder(parent)
            Item.TYPE_BATTERY_BANNER -> BatteryBannerHolder(parent)
            else -> throw IllegalArgumentException("Unknown view type: $viewType")
        }
    }
}