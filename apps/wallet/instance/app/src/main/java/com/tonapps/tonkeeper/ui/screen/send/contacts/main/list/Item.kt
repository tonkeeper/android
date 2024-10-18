package com.tonapps.tonkeeper.ui.screen.send.contacts.main.list

import com.tonapps.blockchain.ton.extensions.toUserFriendly
import com.tonapps.extensions.short8
import com.tonapps.uikit.list.BaseListItem
import com.tonapps.uikit.list.ListCell
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.contacts.entities.ContactEntity
import io.tonapi.models.AccountAddress

sealed class Item(type: Int): BaseListItem(type) {

    companion object {
        const val TYPE_MY_WALLET = 1
        const val TYPE_SPACE = 2
        const val TYPE_LATEST_CONTACT = 3
        const val TYPE_SAVED_CONTACT = 4
        const val TYPE_LOADING = 5
    }

    data object Space: Item(TYPE_SPACE)

    data class MyWallet(
        val position: ListCell.Position,
        val wallet: WalletEntity
    ): Item(TYPE_MY_WALLET) {

        val emoji: CharSequence
            get() = wallet.label.emoji

        val name: String
            get() = wallet.label.name

        val userFriendlyAddress: String = wallet.address.toUserFriendly(testnet = wallet.testnet)
    }

    data class LatestContact(
        val position: ListCell.Position,
        val account: AccountAddress,
        val testnet: Boolean
    ): Item(TYPE_LATEST_CONTACT) {

        val userFriendlyAddress: String = account.address.toUserFriendly(testnet = testnet)

        val name: String
            get() = account.name ?: userFriendlyAddress.short8
    }

    data class SavedContact(
        val position: ListCell.Position,
        val contact: ContactEntity,
        val testnet: Boolean
    ): Item(TYPE_SAVED_CONTACT) {

        val name: String
            get() = contact.name

        val userFriendlyAddress: String = contact.address.toUserFriendly(testnet = testnet)
    }

    data object Loading: Item(TYPE_LOADING)

}