package com.tonkeeper.core.history.list

import android.view.ViewGroup
import com.tonkeeper.core.history.list.holder.HistoryActionHolder
import com.tonkeeper.core.history.list.holder.HistoryHeaderHolder
import com.tonkeeper.core.history.list.item.HistoryItem
import uikit.list.BaseListHolder
import uikit.list.BaseListItem
import uikit.list.BaseListAdapter

class HistoryAdapter: BaseListAdapter() {

    override fun createHolder(parent: ViewGroup, viewType: Int): BaseListHolder<out BaseListItem> {
        return when (viewType) {
            HistoryItem.TYPE_ACTION -> HistoryActionHolder(parent)
            HistoryItem.TYPE_HEADER -> HistoryHeaderHolder(parent)
            else -> throw IllegalArgumentException("Unknown viewType: $viewType")
        }
    }
}