package com.tonkeeper.fragment.wallet.main.pager

import com.tonkeeper.fragment.wallet.main.list.item.WalletItem
import uikit.list.BaseListItem

data class WalletScreenItem(
    val titleRes: Int,
    val items: List<WalletItem> = emptyList()
): BaseListItem()