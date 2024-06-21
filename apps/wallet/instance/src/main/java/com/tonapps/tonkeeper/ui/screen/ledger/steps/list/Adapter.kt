package com.tonapps.tonkeeper.ui.screen.ledger.steps.list

import android.view.ViewGroup
import com.tonapps.tonkeeper.ui.screen.ledger.steps.list.holder.StepHolder
import com.tonapps.uikit.list.BaseListAdapter
import com.tonapps.uikit.list.BaseListHolder
import com.tonapps.uikit.list.BaseListItem

class Adapter(
) : BaseListAdapter() {
    override fun createHolder(parent: ViewGroup, viewType: Int): BaseListHolder<out BaseListItem> {
        return when (viewType) {
            Item.TYPE_STEP -> StepHolder(parent)
            else -> throw IllegalArgumentException("Unknown view type: $viewType")
        }
    }

}