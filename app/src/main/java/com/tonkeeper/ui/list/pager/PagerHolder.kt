package com.tonkeeper.ui.list.pager

import android.view.ViewGroup
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.tonkeeper.R
import com.tonkeeper.ui.list.wallet.WalletAdapter
import com.tonkeeper.ui.list.wallet.item.WalletItem
import com.tonkeeper.ui.list.wallet.decoration.WalletItemDecoration
import com.tonkeeper.ui.list.base.BaseListHolder

class PagerHolder(
    parent: ViewGroup
): BaseListHolder<PagerItem>(parent, R.layout.view_pager_list) {

    companion object {
        val spanCount = 3
    }

    private val listView = itemView as RecyclerView
    private val layoutManager = GridLayoutManager(itemView.context, spanCount)

    init {
        listView.addItemDecoration(WalletItemDecoration(itemView.context, spanCount))
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
    }

    override fun onBind(item: PagerItem) {
        listView.layoutManager = layoutManager
        listView.adapter = WalletAdapter(item.items)
    }

}