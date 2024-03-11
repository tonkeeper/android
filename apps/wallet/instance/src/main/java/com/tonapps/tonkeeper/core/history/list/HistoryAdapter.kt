package com.tonapps.tonkeeper.core.history.list

import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.tonapps.tonkeeper.core.history.list.holder.HistoryActionHolder
import com.tonapps.tonkeeper.core.history.list.holder.HistoryHeaderHolder
import com.tonapps.tonkeeper.core.history.list.holder.HistoryLoaderHolder
import com.tonapps.tonkeeper.core.history.list.holder.HistorySpaceHolder
import com.tonapps.tonkeeper.core.history.list.item.HistoryItem
import com.tonapps.uikit.list.BaseListAdapter

open class HistoryAdapter(
    private val disableOpenAction: Boolean = false,
): BaseListAdapter() {

    init {
        super.setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long {
        val item = super.getItem(position) as? HistoryItem ?: return RecyclerView.NO_ID
        return item.id
    }

    override fun createHolder(parent: ViewGroup, viewType: Int): com.tonapps.uikit.list.BaseListHolder<out com.tonapps.uikit.list.BaseListItem> {
        return when (viewType) {
            HistoryItem.TYPE_ACTION -> HistoryActionHolder(parent, disableOpenAction)
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