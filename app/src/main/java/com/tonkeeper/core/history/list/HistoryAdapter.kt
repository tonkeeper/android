package com.tonkeeper.core.history.list

import android.view.ViewGroup
import com.tonkeeper.core.history.list.holder.HistoryActionHolder
import com.tonkeeper.core.history.list.holder.HistoryHeaderHolder
import com.tonkeeper.core.history.list.holder.HistoryLoaderHolder
import com.tonkeeper.core.history.list.holder.HistorySpaceHolder
import com.tonkeeper.core.history.list.item.HistoryItem
import uikit.list.BaseListHolder
import uikit.list.BaseListItem
import uikit.list.BaseListAdapter

open class HistoryAdapter: BaseListAdapter() {

    override fun createHolder(parent: ViewGroup, viewType: Int): BaseListHolder<out BaseListItem> {
        return when (viewType) {
            HistoryItem.TYPE_ACTION -> HistoryActionHolder(parent)
            HistoryItem.TYPE_HEADER -> HistoryHeaderHolder(parent)
            HistoryItem.TYPE_SPACE -> HistorySpaceHolder(parent)
            HistoryItem.TYPE_LOADER -> HistoryLoaderHolder(parent)
            else -> throw IllegalArgumentException("Unknown viewType: $viewType")
        }
    }

    fun getLastLt(): Long? {
        val item = getLastEvent() ?: return null
        return if (item.lt > 0) {
            item.lt
        } else {
            null
        }
    }

    fun getLastEvent(): HistoryItem.Event? {
        for (i in currentList.size - 1 downTo 0) {
            val item = currentList[i]
            if (item is HistoryItem.Event) {
                return item
            }
        }
        return null
    }

}