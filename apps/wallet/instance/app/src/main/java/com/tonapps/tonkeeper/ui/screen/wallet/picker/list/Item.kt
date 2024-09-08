package com.tonapps.tonkeeper.ui.screen.wallet.picker.list

import com.tonapps.uikit.list.BaseListItem
import com.tonapps.uikit.list.ListCell
import com.tonapps.wallet.data.account.entities.WalletEntity
import uikit.extensions.dp

sealed class Item(
    type: Int,
    val viewHeight: Int
): BaseListItem(type) {

    companion object {
        const val TYPE_WALLET = 0
        const val TYPE_ADD_WALLET = 1
        const val TYPE_SKELETON = 2

        val List<Item>.height: Int
            get() = sumOf { it.viewHeight }

        private fun getId(item: Item) = when (item) {
            is Wallet -> item.wallet.id
            is AddWallet -> "add_wallet"
            is Skeleton -> "skeleton"
        }
    }

    val id: String
        get() = getId(this)

    data class Skeleton(
        val position: ListCell.Position
    ): Item(TYPE_SKELETON, 78.dp)

    data class Wallet(
        val wallet: WalletEntity,
        val selected: Boolean,
        val position: ListCell.Position,
        val balance: CharSequence?,
        val hiddenBalance: Boolean,
        val editMode: Boolean = false,
    ): Item(TYPE_WALLET, 78.dp) {

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

    data object AddWallet: Item(TYPE_ADD_WALLET, 68.dp)
}