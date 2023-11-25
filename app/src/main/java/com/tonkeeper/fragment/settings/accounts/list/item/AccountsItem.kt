package com.tonkeeper.fragment.settings.accounts.list.item

import uikit.list.BaseListItem
import uikit.list.ListCell

open class AccountsItem(
    type: Int,
    override val position: ListCell.Position
): BaseListItem(type), ListCell {

    companion object {
        const val TYPE_WALLET = 1
        const val TYPE_ACTION = 2
    }
}
