package com.tonkeeper.fragment.wallet.creating.list

import android.view.ViewGroup
import uikit.list.BaseListAdapter
import uikit.list.BaseListHolder
import uikit.list.BaseListItem

internal object PagerAdapter: BaseListAdapter<PagerItem>(
    listOf(PagerItem.Generating, PagerItem.Created, PagerItem.Attention)
) {
    override fun createHolder(parent: ViewGroup, viewType: Int): BaseListHolder<out BaseListItem> {
        return when(viewType) {
            PagerItem.Generating.type -> PagerHolder.Generating(parent)
            PagerItem.Created.type -> PagerHolder.Created(parent)
            PagerItem.Attention.type -> PagerHolder.Attention(parent)
            else -> throw IllegalArgumentException("Unknown viewType: $viewType")
        }
    }

}