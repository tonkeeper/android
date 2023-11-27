package com.tonkeeper.fragment.wallet.main.pager

import android.view.ViewGroup
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.tonkeeper.R
import com.tonkeeper.fragment.wallet.main.list.WalletContentAdapter
import com.tonkeeper.fragment.wallet.main.list.WalletItemDecoration
import com.tonkeeper.fragment.wallet.main.list.item.WalletItem
import uikit.list.BaseListHolder

class WalletScreenHolder(
    parent: ViewGroup
): BaseListHolder<WalletScreenItem>(parent, R.layout.view_wallet_pager) {

    companion object {
        private const val spanCount = 3
        private val decoration = WalletItemDecoration()
    }

    private val layoutManager = object : GridLayoutManager(itemView.context, spanCount) {
        override fun supportsPredictiveItemAnimations() = false
    }

    private val adapter = WalletContentAdapter()
    private val listView = findViewById<RecyclerView>(R.id.list)

    init {
        listView.layoutManager = layoutManager
        layoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                val viewType = listView.adapter?.getItemViewType(position) ?: 0
                return if (viewType == WalletItem.TYPE_NFT) {
                    1
                } else {
                    spanCount
                }
            }

        }
        listView.adapter = adapter
        listView.addItemDecoration(decoration)
    }

    override fun onBind(item: WalletScreenItem) {
        adapter.submitList(item.items)
    }
}