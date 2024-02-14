package com.tonapps.tonkeeper.fragment.wallet.main.list.item

abstract class WalletItem(
    type: Int
): com.tonapps.uikit.list.BaseListItem(type) {

    companion object {
        const val TYPE_TON = 0
        const val TYPE_JETTON = 1
        const val TYPE_DATA = 2
        const val TYPE_ACTIONS = 3
        const val TYPE_SPACE = 4
        const val TYPE_BANNER = 5
    }
}
