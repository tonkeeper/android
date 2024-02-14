package com.tonapps.tonkeeper.fragment.settings.accounts.list.item

open class AccountsItem(
    type: Int,
    override val position: com.tonapps.uikit.list.ListCell.Position
): com.tonapps.uikit.list.BaseListItem(type), com.tonapps.uikit.list.ListCell {

    companion object {
        const val TYPE_WALLET = 1
        const val TYPE_ACTION = 2
    }
}
