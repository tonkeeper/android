package com.tonapps.tonkeeper.ui.screen.send.contacts.main.list

import com.tonapps.blockchain.ton.extensions.toUserFriendly
import com.tonapps.extensions.short8
import com.tonapps.uikit.list.BaseListItem
import com.tonapps.uikit.list.ListCell
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.contacts.entities.ContactEntity
import io.tonapi.models.AccountAddress

sealed class Item(type: Int) : BaseListItem(type) {

    companion object {
        const val TYPE_MY_WALLET = 1
        const val TYPE_SPACE = 2
        const val TYPE_LATEST_CONTACT = 3
        const val TYPE_SAVED_CONTACT = 4
        const val TYPE_LOADING = 5
    }

    data object Space : Item(TYPE_SPACE)

    sealed class Address(
        type: Int,
        open val position: ListCell.Position,
        open val address: String
    ) : Item(type)

    data class MyWallet(
        override val position: ListCell.Position,
        val wallet: WalletEntity
    ) : Address(TYPE_MY_WALLET, position, wallet.address) {

        val emoji: CharSequence
            get() = wallet.label.emoji

        val name: String
            get() = wallet.label.name
    }

    data class LatestContact(
        override val position: ListCell.Position,
        override val address: String,
        val name: String,
        val timestamp: Long,
    ) : Address(TYPE_LATEST_CONTACT, position, address) {

        constructor(
            position: ListCell.Position,
            account: AccountAddress,
            timestamp: Long,
            testnet: Boolean
        ) : this(
            position = position,
            address = account.address.toUserFriendly(testnet = testnet),
            name = account.name ?: account.address.toUserFriendly(testnet = testnet).short8,
            timestamp = timestamp
        )

        constructor(position: ListCell.Position, address: String, timestamp: Long) : this(
            position = position,
            address = address,
            name = address.short8,
            timestamp = timestamp
        )
    }

    data class SavedContact(
        override val position: ListCell.Position,
        val contact: ContactEntity,
        val testnet: Boolean
    ) : Address(TYPE_SAVED_CONTACT, position, contact.address) {

        val name: String
            get() = contact.name
    }

    data object Loading : Item(TYPE_LOADING)

}