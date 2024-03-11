package com.tonapps.tonkeeper.fragment.wallet.collectibles.list

import android.view.ViewGroup
import com.tonapps.uikit.list.BaseListAdapter
import com.tonapps.uikit.list.BaseListHolder
import com.tonapps.uikit.list.BaseListItem

class Adapter: BaseListAdapter() {

    override fun createHolder(parent: ViewGroup, viewType: Int): BaseListHolder<out BaseListItem> {
        return when (viewType) {
            Item.TYPE_NFT -> Holder.Nft(parent)
            Item.TYPE_SKELETON -> Holder.Skeleton(parent)
            else -> throw IllegalArgumentException("Unknown view type: $viewType")
        }
    }
}