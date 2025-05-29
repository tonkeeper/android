package com.tonapps.tonkeeper.ui.screen.events.main.filters

import android.view.ViewGroup
import com.tonapps.tonkeeper.ui.screen.events.main.filters.holder.AppHolder
import com.tonapps.tonkeeper.ui.screen.events.main.filters.holder.FilterHolder
import com.tonapps.uikit.list.BaseListAdapter
import com.tonapps.uikit.list.BaseListHolder
import com.tonapps.uikit.list.BaseListItem

class FiltersAdapter(private val onClick: (item: FilterItem) -> Unit): BaseListAdapter() {

    override fun createHolder(parent: ViewGroup, viewType: Int): BaseListHolder<out BaseListItem> {
        return when(viewType) {
            FilterItem.TYPE_SEND, FilterItem.TYPE_RECEIVE, FilterItem.TYPE_SPAM, FilterItem.TYPE_DAPPS -> FilterHolder(parent, onClick)
            FilterItem.TYPE_APP -> AppHolder(parent, onClick)
            else -> throw IllegalArgumentException("Unknown viewType: $viewType")
        }
    }

}