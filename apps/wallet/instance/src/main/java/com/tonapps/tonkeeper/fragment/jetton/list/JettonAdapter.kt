package com.tonapps.tonkeeper.fragment.jetton.list

import android.view.ViewGroup
import com.tonapps.tonkeeper.fragment.jetton.list.holder.JettonActionsHolder
import com.tonapps.tonkeeper.fragment.jetton.list.holder.JettonDividerHolder
import com.tonapps.tonkeeper.fragment.jetton.list.holder.JettonHeaderHolder

class JettonAdapter: com.tonapps.uikit.list.BaseListAdapter() {
    override fun createHolder(parent: ViewGroup, viewType: Int): com.tonapps.uikit.list.BaseListHolder<out com.tonapps.uikit.list.BaseListItem> {
        return when (viewType) {
            JettonItem.TYPE_HEADER -> JettonHeaderHolder(parent)
            JettonItem.TYPE_ACTIONS -> JettonActionsHolder(parent)
            JettonItem.TYPE_DIVIDER -> JettonDividerHolder(parent)
            else -> throw IllegalArgumentException("Unknown viewType: $viewType")
        }
    }

}