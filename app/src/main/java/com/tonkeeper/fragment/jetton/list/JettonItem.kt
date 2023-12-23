package com.tonkeeper.fragment.jetton.list

import io.tonapi.models.JettonBalance
import uikit.list.BaseListItem

sealed class JettonItem(
    type: Int
): BaseListItem(type) {

    companion object {
        const val TYPE_HEADER = 0
        const val TYPE_ACTIONS = 1
        const val TYPE_DIVIDER = 2
    }

    data class Header(
        val balance: String,
        val currencyBalance: String,
        val iconUrl: String,
        val rate: String,
        val diff24h: String
    ): JettonItem(TYPE_HEADER)

    data class Actions(
        val jetton: JettonBalance
    ): JettonItem(TYPE_ACTIONS)

    data object Divider: JettonItem(TYPE_DIVIDER)
}