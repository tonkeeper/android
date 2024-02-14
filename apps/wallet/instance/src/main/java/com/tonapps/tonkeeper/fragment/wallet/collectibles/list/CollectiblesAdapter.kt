package com.tonapps.tonkeeper.fragment.wallet.collectibles.list

import android.view.ViewGroup

class CollectiblesAdapter: com.tonapps.uikit.list.BaseListAdapter() {

    override fun createHolder(parent: ViewGroup, viewType: Int): com.tonapps.uikit.list.BaseListHolder<out com.tonapps.uikit.list.BaseListItem> {
        return CollectiblesHolder(parent)
    }
}