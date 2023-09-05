package com.tonkeeper.ui.list.wallet.item

import com.tonkeeper.ui.list.base.BaseListItem

open class WalletItem(
    type: Int
): BaseListItem(type) {

    companion object {
        const val TYPE_TON = 0
        const val TYPE_STAKING = 1
        const val TYPE_JETTON = 2
        const val TYPE_NFT = 3
        const val TYPE_GHOST = 4
    }
}