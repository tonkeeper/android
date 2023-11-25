package com.tonkeeper.fragment.wallet.main.pager

import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.tonkeeper.fragment.wallet.main.list.item.WalletItem
import uikit.list.BaseListAdapter
import uikit.list.BaseListHolder
import uikit.list.BaseListItem

class WalletScreenAdapter(
    items: List<WalletScreenItem>
): BaseListAdapter<WalletScreenHolder>(items.toMutableList()) {
    override fun createHolder(parent: ViewGroup, viewType: Int): BaseListHolder<out BaseListItem> {
        return WalletScreenHolder(parent)
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        recyclerView.overScrollMode = RecyclerView.OVER_SCROLL_NEVER
    }

}