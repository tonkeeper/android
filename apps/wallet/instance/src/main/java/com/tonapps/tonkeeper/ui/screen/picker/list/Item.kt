package com.tonapps.tonkeeper.ui.screen.picker.list

import com.tonapps.uikit.list.BaseListItem
import com.tonapps.uikit.list.ListCell
import com.tonapps.wallet.data.account.WalletType
import com.tonapps.wallet.data.account.entities.WalletLabel

data class Item(
    val accountId: String,
    val walletId: Long,
    val walletLabel: WalletLabel,
    val walletType: WalletType,
    val selected: Boolean,
    val position: ListCell.Position,
    val balance: String
): BaseListItem() {

    val color: Int
        get() = walletLabel.color

    val emoji: CharSequence
        get() = walletLabel.emoji

    val name: String
        get() = walletLabel.name

    val testnet: Boolean
        get() = walletType == WalletType.Testnet
}