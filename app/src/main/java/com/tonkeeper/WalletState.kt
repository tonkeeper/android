package com.tonkeeper

import com.tonkeeper.extensions.toUserLikeUSD
import com.tonkeeper.ui.list.pager.PagerItem

data class WalletState(
    val address: String,
    val amountUSD: Float,
    val pages: List<PagerItem>,
) {

    val amountUserLikeUSD: String by lazy {
        amountUSD.toUserLikeUSD()
    }

    val shortAddress: String by lazy {
        if (address.length < 8) return@lazy address

        address.substring(0, 4) + "…" + address.substring(address.length - 4, address.length)
    }

}