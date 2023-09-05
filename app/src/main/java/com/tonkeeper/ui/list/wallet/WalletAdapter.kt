package com.tonkeeper.ui.list.wallet

import android.view.ViewGroup
import com.tonkeeper.ui.list.wallet.holder.WalletHolder
import com.tonkeeper.ui.list.wallet.holder.WalletCellJettonHolder
import com.tonkeeper.ui.list.wallet.holder.WalletNftHolder
import com.tonkeeper.ui.list.wallet.holder.WalletCellStakingHolder
import com.tonkeeper.ui.list.wallet.holder.WalletCellTonHolder
import com.tonkeeper.ui.list.wallet.item.WalletItem
import com.tonkeeper.ui.list.base.BaseListHolder
import com.tonkeeper.ui.list.base.BaseListItem
import com.tonkeeper.ui.list.base.BaseListAdapter
import com.tonkeeper.ui.list.wallet.holder.WalletGhostHolder
import com.tonkeeper.ui.list.wallet.item.WalletGhostItem

class WalletAdapter(
    items: List<WalletItem>
): BaseListAdapter<WalletHolder<out WalletItem>>(items) {

    override fun createHolder(parent: ViewGroup, viewType: Int): BaseListHolder<out BaseListItem> {
        return when (viewType) {
            WalletItem.TYPE_JETTON -> WalletCellJettonHolder(parent)
            WalletItem.TYPE_TON -> WalletCellTonHolder(parent)
            WalletItem.TYPE_STAKING -> WalletCellStakingHolder(parent)
            WalletItem.TYPE_NFT -> WalletNftHolder(parent)
            WalletItem.TYPE_GHOST -> WalletGhostHolder(parent)
            else -> throw IllegalArgumentException("Unknown view type: $viewType")
        }
    }
}