package com.tonapps.tonkeeper.ui.screen.wallet.picker.list

import com.tonapps.blockchain.ton.contract.WalletVersion
import com.tonapps.uikit.list.BaseListItem
import com.tonapps.uikit.list.ListCell
import com.tonapps.wallet.data.account.entities.WalletEntity

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
        val wallet: WalletEntity,
        val selected: Boolean,
        val position: ListCell.Position,
        val balance: CharSequence,
        val hiddenBalance: Boolean,
        val editMode: Boolean = false
    ): Item(TYPE_WALLET) {

        val accountId: String
            get() = wallet.accountId

        val walletId: String
            get() = wallet.id

        val color: Int
            get() = wallet.label.color

        val emoji: CharSequence
            get() = wallet.label.emoji

        val name: String
            get() = wallet.label.name

        val testnet: Boolean
            get() = wallet.testnet
    }

    data object AddWallet: Item(TYPE_ADD_WALLET)
}