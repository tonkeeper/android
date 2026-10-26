package com.tonapps.tonkeeper.ui.screen.tronfees.list

import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.tonapps.bus.generated.Events.DepositFlow.DepositFlowFrom
import com.tonapps.uikit.list.BaseListAdapter
import com.tonapps.uikit.list.BaseListHolder
import com.tonapps.uikit.list.BaseListItem

class Adapter(private val analyticsFrom: DepositFlowFrom) : BaseListAdapter() {

    override fun createHolder(parent: ViewGroup, viewType: Int): BaseListHolder<out BaseListItem> {
        return when (viewType) {
            Item.TYPE_HEADER -> HeaderHolder(parent)
            Item.TYPE_TOKEN -> TokenHolder(parent, analyticsFrom)
            Item.TYPE_BATTERY -> BatteryHolder(parent)
            Item.TYPE_LEARN_MORE -> LearnMoreHolder(parent)
            else -> throw IllegalArgumentException("Unknown viewType: $viewType")
        }
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        recyclerView.isNestedScrollingEnabled = true
        recyclerView.setHasFixedSize(false)
    }
}


