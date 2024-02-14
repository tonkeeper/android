package com.tonapps.tonkeeper.fragment.settings.accounts.list.item

data class AccountsActionItem(
    val id: Long,
    val titleRes: Int,
    val iconRes: Int,
    override val position: com.tonapps.uikit.list.ListCell.Position
): AccountsItem(TYPE_ACTION, position) {

    companion object {
        const val NEW_WALLET_ID = 1L
    }
}