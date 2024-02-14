package com.tonapps.tonkeeper.ui.screen.picker.list

import com.tonapps.uikit.list.BaseListItem
import com.tonapps.uikit.list.ListCell
import ton.wallet.Wallet

data class WalletPickerItem(
    val wallet: Wallet,
    val selected: Boolean,
    val position: ListCell.Position,
): BaseListItem() {

    val color: Int
        get() = wallet.color

    val emoji: CharSequence
        get() = wallet.emoji

    val name: String
        get() = wallet.name
}