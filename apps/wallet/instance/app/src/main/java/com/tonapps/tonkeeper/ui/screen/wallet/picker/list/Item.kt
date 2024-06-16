package com.tonapps.tonkeeper.ui.screen.wallet.picker.list

import com.tonapps.uikit.list.BaseListItem
import com.tonapps.uikit.list.ListCell

sealed class Item(type: Int): BaseListItem(type) {
    companion object {
        const val TYPE_WALLET = 0
        const val TYPE_ADD_WALLET = 1
        const val TYPE_SKELETON = 2
    }

    data class Skeleton(
        val position: ListCell.Position
    ): Item(TYPE_SKELETON)

    data class Wallet(
        val accountId: String,
        val walletId: String,
        val walletLabel: com.tonapps.wallet.data.account.Wallet.Label,
        val walletType: com.tonapps.wallet.data.account.Wallet.Type,
        val selected: Boolean,
        val position: ListCell.Position,
        val balance: CharSequence,
        val hiddenBalance: Boolean,
        val editMode: Boolean = false
    ): Item(TYPE_WALLET) {

        val color: Int
            get() = walletLabel.color

        val emoji: CharSequence
            get() = walletLabel.emoji

        val name: String
            get() = walletLabel.name

        val testnet: Boolean
            get() = walletType == com.tonapps.wallet.data.account.Wallet.Type.Testnet
    }

    data object AddWallet: Item(TYPE_ADD_WALLET)
}