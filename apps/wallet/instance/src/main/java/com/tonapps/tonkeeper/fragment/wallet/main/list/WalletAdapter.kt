package com.tonapps.tonkeeper.fragment.wallet.main.list

import android.view.ViewGroup
import com.tonapps.tonkeeper.fragment.wallet.main.list.holder.WalletActionsHolder
import com.tonapps.tonkeeper.fragment.wallet.main.list.holder.WalletBannerHolder
import com.tonapps.tonkeeper.fragment.wallet.main.list.holder.WalletCellJettonHolder
import com.tonapps.tonkeeper.fragment.wallet.main.list.holder.WalletCellTonHolder
import com.tonapps.tonkeeper.fragment.wallet.main.list.holder.WalletDataHolder
import com.tonapps.tonkeeper.fragment.wallet.main.list.holder.WalletSpaceHolder
import com.tonapps.tonkeeper.fragment.wallet.main.list.item.WalletItem
import com.tonapps.uikit.list.BaseListAdapter

class WalletAdapter: BaseListAdapter() {

    override fun createHolder(parent: ViewGroup, viewType: Int): com.tonapps.uikit.list.BaseListHolder<out com.tonapps.uikit.list.BaseListItem> {
        return when (viewType) {
            WalletItem.TYPE_JETTON -> WalletCellJettonHolder(parent)
            WalletItem.TYPE_TON -> WalletCellTonHolder(parent)
            WalletItem.TYPE_DATA -> WalletDataHolder(parent)
            WalletItem.TYPE_ACTIONS -> WalletActionsHolder(parent)
            WalletItem.TYPE_SPACE -> WalletSpaceHolder(parent)
            WalletItem.TYPE_BANNER -> WalletBannerHolder(parent)
            else -> throw IllegalArgumentException("Unknown view type: $viewType")
        }
    }

}