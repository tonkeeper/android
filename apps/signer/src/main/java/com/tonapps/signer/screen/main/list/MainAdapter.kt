package com.tonapps.signer.screen.main.list

import android.view.ViewGroup
import com.tonapps.signer.screen.main.list.holder.MainAccountHolder
import com.tonapps.signer.screen.main.list.holder.MainActionsHolder
import uikit.list.BaseListAdapter
import uikit.list.BaseListHolder
import uikit.list.BaseListItem

class MainAdapter(
    private val selectAccountCallback: (id: Long) -> Unit
): BaseListAdapter() {

    override fun createHolder(parent: ViewGroup, viewType: Int): BaseListHolder<out BaseListItem> {
        return when (viewType) {
            MainItem.TYPE_ACTIONS -> MainActionsHolder(parent)
            MainItem.TYPE_ACCOUNT -> MainAccountHolder(parent, selectAccountCallback)
            else -> throw IllegalArgumentException("Unknown view type: $viewType")
        }
    }

    override fun getItemId(position: Int): Long {
        val item = getItem(position) as MainItem
        return item.id
    }
}