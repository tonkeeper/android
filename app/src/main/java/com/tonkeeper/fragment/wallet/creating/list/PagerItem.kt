package com.tonkeeper.fragment.wallet.creating.list

import com.tonkeeper.uikit.list.BaseListItem

internal sealed class PagerItem(
    type: Int
): BaseListItem(type) {

    companion object {
        const val TYPE_GENERATING = 0
        const val TYPE_CREATED = 1
        const val TYPE_ATTENTION = 2
    }

    data object Generating: PagerItem(TYPE_GENERATING)
    data object Created: PagerItem(TYPE_CREATED)
    data object Attention: PagerItem(TYPE_ATTENTION)
}