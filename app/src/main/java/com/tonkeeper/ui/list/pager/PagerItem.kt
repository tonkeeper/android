package com.tonkeeper.ui.list.pager

import com.tonkeeper.ui.list.wallet.item.WalletItem
import com.tonkeeper.ui.list.base.BaseListItem

data class PagerItem(
    val title: String,
    val items: List<WalletItem>
): BaseListItem()