package com.tonapps.tonkeeper.ui.screen.events.list

import android.view.ViewGroup
import com.tonapps.tonkeeper.ui.screen.events.list.holder.ActionHolder
import com.tonapps.tonkeeper.ui.screen.events.list.holder.DateHolder
import com.tonapps.tonkeeper.ui.screen.events.list.holder.SpaceHolder
import com.tonapps.uikit.list.BaseListAdapter
import com.tonapps.uikit.list.BaseListHolder
import com.tonapps.uikit.list.BaseListItem

class Adapter: BaseListAdapter() {

    override fun createHolder(parent: ViewGroup, viewType: Int): BaseListHolder<out BaseListItem> {
        return when(viewType) {
            Item.TYPE_DATE -> DateHolder(parent)
            Item.TYPE_ACTION -> ActionHolder(parent)
            Item.TYPE_SPACE -> SpaceHolder(parent)
            else -> throw IllegalArgumentException("Unknown viewType: $viewType")
        }
    }

}