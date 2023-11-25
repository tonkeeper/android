package com.tonkeeper.core.history.list.item

import uikit.list.BaseListItem

open class HistoryItem(
    type: Int,
): BaseListItem(type) {

    companion object {
        const val TYPE_ACTION = 1
        const val TYPE_HEADER = 2
    }

}