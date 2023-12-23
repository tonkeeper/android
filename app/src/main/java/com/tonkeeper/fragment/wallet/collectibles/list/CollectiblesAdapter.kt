package com.tonkeeper.fragment.wallet.collectibles.list

import android.view.ViewGroup
import uikit.list.BaseListAdapter
import uikit.list.BaseListHolder
import uikit.list.BaseListItem

class CollectiblesAdapter: BaseListAdapter() {

    override fun createHolder(parent: ViewGroup, viewType: Int): BaseListHolder<out BaseListItem> {
        return CollectiblesHolder(parent)
    }
}