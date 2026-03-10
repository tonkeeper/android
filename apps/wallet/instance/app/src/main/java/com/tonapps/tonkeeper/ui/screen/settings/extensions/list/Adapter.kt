package com.tonapps.tonkeeper.ui.screen.settings.extensions.list

import android.view.ViewGroup
import com.tonapps.tonkeeper.ui.screen.settings.extensions.list.holder.PluginHolder
import com.tonapps.uikit.list.BaseListAdapter
import com.tonapps.uikit.list.BaseListHolder
import com.tonapps.uikit.list.BaseListItem

class Adapter(): BaseListAdapter() {
    override fun createHolder(parent: ViewGroup, viewType: Int): BaseListHolder<out BaseListItem> {
        return when (viewType) {
            Item.TYPE_PLUGIN -> PluginHolder(parent)
            else -> throw IllegalArgumentException("Unknown viewType: $viewType")
        }
    }
}



