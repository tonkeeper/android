package com.tonapps.tonkeeper.ui.screen.collectibles.main.list

import android.view.ViewGroup
import com.tonapps.tonkeeper.ui.screen.collectibles.main.list.holder.NftHolder
import com.tonapps.tonkeeper.ui.screen.collectibles.main.list.holder.SkeletonHolder
import com.tonapps.uikit.list.BaseListAdapter
import com.tonapps.uikit.list.BaseListHolder
import com.tonapps.uikit.list.BaseListItem

class Adapter: BaseListAdapter() {

    init {
        applySkeleton()
    }

    fun applySkeleton() {
        submitList(listOf(
            Item.Skeleton(),
            Item.Skeleton(),
            Item.Skeleton(),
            Item.Skeleton(),
            Item.Skeleton(),
            Item.Skeleton(),
            Item.Skeleton(),
            Item.Skeleton(),
            Item.Skeleton(),
            Item.Skeleton(),
            Item.Skeleton(),
            Item.Skeleton(),
            Item.Skeleton()
        ))
    }

    override fun createHolder(parent: ViewGroup, viewType: Int): BaseListHolder<out BaseListItem> {
        return when(viewType) {
            Item.TYPE_NFT -> NftHolder(parent)
            Item.TYPE_SKELETON -> SkeletonHolder(parent)
            else -> throw IllegalArgumentException("Unknown view type: $viewType")
        }
    }
}