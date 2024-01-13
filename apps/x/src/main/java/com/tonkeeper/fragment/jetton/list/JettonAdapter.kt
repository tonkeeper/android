package com.tonkeeper.fragment.jetton.list

import android.view.ViewGroup
import com.tonkeeper.fragment.jetton.list.holder.JettonActionsHolder
import com.tonkeeper.fragment.jetton.list.holder.JettonDividerHolder
import com.tonkeeper.fragment.jetton.list.holder.JettonHeaderHolder
import uikit.list.BaseListAdapter
import uikit.list.BaseListHolder
import uikit.list.BaseListItem

class JettonAdapter: BaseListAdapter() {
    override fun createHolder(parent: ViewGroup, viewType: Int): BaseListHolder<out BaseListItem> {
        return when (viewType) {
            JettonItem.TYPE_HEADER -> JettonHeaderHolder(parent)
            JettonItem.TYPE_ACTIONS -> JettonActionsHolder(parent)
            JettonItem.TYPE_DIVIDER -> JettonDividerHolder(parent)
            else -> throw IllegalArgumentException("Unknown viewType: $viewType")
        }
    }

}