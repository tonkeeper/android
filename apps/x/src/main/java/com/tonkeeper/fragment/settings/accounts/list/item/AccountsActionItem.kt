package com.tonkeeper.fragment.settings.accounts.list.item

import uikit.list.ListCell

data class AccountsActionItem(
    val id: Long,
    val titleRes: Int,
    val iconRes: Int,
    override val position: ListCell.Position
): AccountsItem(TYPE_ACTION, position) {

    companion object {
        const val NEW_WALLET_ID = 1L
    }
}