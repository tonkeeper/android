package com.tonkeeper.fragment.wallet.main.list.item

import uikit.list.BaseListItem

open class WalletItem(
    type: Int
): BaseListItem(type) {

    companion object {
        const val TYPE_TON = 0
        const val TYPE_JETTON = 1
        const val TYPE_DATA = 2
        const val TYPE_ACTIONS = 3
        const val TYPE_SPACE = 4
    }
}
