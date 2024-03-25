package com.tonapps.signer.screen.main.list

import android.view.ViewGroup
import com.tonapps.signer.screen.main.list.holder.MainAccountHolder
import com.tonapps.signer.screen.main.list.holder.MainActionsHolder

class MainAdapter(
    private val selectAccountCallback: (id: Long) -> Unit
): com.tonapps.uikit.list.BaseListAdapter() {

    override fun createHolder(parent: ViewGroup, viewType: Int): com.tonapps.uikit.list.BaseListHolder<out com.tonapps.uikit.list.BaseListItem> {
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