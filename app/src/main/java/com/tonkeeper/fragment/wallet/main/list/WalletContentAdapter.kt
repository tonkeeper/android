package com.tonkeeper.fragment.wallet.main.list

import android.view.ViewGroup
import com.tonkeeper.fragment.wallet.main.list.holder.WalletCellJettonHolder
import com.tonkeeper.fragment.wallet.main.list.holder.WalletCellTonHolder
import com.tonkeeper.fragment.wallet.main.list.holder.WalletNftHolder
import com.tonkeeper.fragment.wallet.main.list.item.WalletItem
import uikit.list.BaseListHolder
import uikit.list.BaseListItem
import uikit.list.BaseListAdapter

class WalletContentAdapter: BaseListAdapter() {

    override fun createHolder(parent: ViewGroup, viewType: Int): BaseListHolder<out BaseListItem> {
        return when (viewType) {
            WalletItem.TYPE_JETTON -> WalletCellJettonHolder(parent)
            WalletItem.TYPE_TON -> WalletCellTonHolder(parent)
            WalletItem.TYPE_NFT -> WalletNftHolder(parent)
            else -> throw IllegalArgumentException("Unknown view type: $viewType")
        }
    }

}