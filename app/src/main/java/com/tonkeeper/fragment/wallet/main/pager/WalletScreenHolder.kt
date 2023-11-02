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
        const val spanCount = 3
    }

    private val listView = findViewById<RecyclerView>(R.id.list)
    private val layoutManager = GridLayoutManager(itemView.context, spanCount)

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
        listView.addItemDecoration(WalletItemDecoration(context, spanCount))
    }

    override fun onBind(item: WalletScreenItem) {
        listView.adapter = WalletContentAdapter(item.items)
    }

}