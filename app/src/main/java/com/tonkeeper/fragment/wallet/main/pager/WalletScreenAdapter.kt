package com.tonkeeper.fragment.wallet.main.pager

import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import uikit.list.BaseListHolder
import uikit.list.BaseListItem
import uikit.list.BaseListAdapter

class WalletScreenAdapter: BaseListAdapter() {

    override fun createHolder(parent: ViewGroup, viewType: Int): BaseListHolder<out BaseListItem> {
        return WalletScreenHolder(parent)
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        recyclerView.overScrollMode = RecyclerView.OVER_SCROLL_NEVER
    }
}