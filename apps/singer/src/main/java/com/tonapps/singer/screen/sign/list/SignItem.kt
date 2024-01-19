package com.tonapps.singer.screen.sign.list

import uikit.list.BaseListItem
import uikit.list.ListCell

sealed class SignItem(
    type: Int,
    val position: ListCell.Position
): BaseListItem(type) {

    companion object {
        const val UNKNOWN = 1
    }

    class Unknown(position: ListCell.Position): SignItem(UNKNOWN, position)
}