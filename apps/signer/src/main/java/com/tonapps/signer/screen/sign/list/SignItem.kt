package com.tonapps.signer.screen.sign.list

import uikit.list.BaseListItem
import uikit.list.ListCell

sealed class SignItem(
    type: Int,
    val position: ListCell.Position
): BaseListItem(type) {

    companion object {
        const val UNKNOWN = 1
        const val SEND = 2
    }

    class Unknown(position: ListCell.Position): SignItem(UNKNOWN, position)

    class Send(
        val target: String,
        val value: String,
        val value2: String?,
        val comment: String?,
        position: ListCell.Position
    ): SignItem(SEND, position)
}