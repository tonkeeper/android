package com.tonkeeper.fragment.wallet.main.list

import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.tonkeeper.fragment.wallet.main.list.holder.WalletActionsHolder
import com.tonkeeper.fragment.wallet.main.list.holder.WalletCellJettonHolder
import com.tonkeeper.fragment.wallet.main.list.holder.WalletCellTonHolder
import com.tonkeeper.fragment.wallet.main.list.holder.WalletDataHolder
import com.tonkeeper.fragment.wallet.main.list.holder.WalletNftHolder
import com.tonkeeper.fragment.wallet.main.list.item.WalletDataItem
import com.tonkeeper.fragment.wallet.main.list.item.WalletItem
import uikit.extensions.setPaddingTop
import uikit.list.BaseListHolder
import uikit.list.BaseListItem
import uikit.list.BaseListAdapter

class WalletContentAdapter: BaseListAdapter() {

    override fun createHolder(parent: ViewGroup, viewType: Int): BaseListHolder<out BaseListItem> {
        return when (viewType) {
            WalletItem.TYPE_JETTON -> WalletCellJettonHolder(parent)
            WalletItem.TYPE_TON -> WalletCellTonHolder(parent)
            WalletItem.TYPE_NFT -> WalletNftHolder(parent)
            WalletItem.TYPE_DATA -> WalletDataHolder(parent)
            WalletItem.TYPE_ACTIONS -> WalletActionsHolder(parent)
            else -> throw IllegalArgumentException("Unknown view type: $viewType")
        }
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        recyclerView.setHasFixedSize(true)
        recyclerView.isNestedScrollingEnabled = false
    }

}