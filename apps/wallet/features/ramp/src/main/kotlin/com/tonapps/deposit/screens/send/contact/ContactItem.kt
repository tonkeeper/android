package com.tonapps.deposit.screens.send.contact

import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.uikit.list.ListCell
import com.tonapps.wallet.data.contacts.entities.ContactEntity

sealed interface ContactItem {

    sealed interface AddressItem : ContactItem {
        val position: ListCell.Position
        val address: String
    }

    data class MyWallet(
        override val position: ListCell.Position,
        val wallet: WalletEntity,
    ) : AddressItem {
        override val address: String get() = wallet.address
        val emoji: CharSequence get() = wallet.label.emoji
        val name: String get() = wallet.label.name
    }

    data class SavedContact(
        override val position: ListCell.Position,
        val contact: ContactEntity,
        val testnet: Boolean,
    ) : AddressItem {
        override val address: String get() = contact.address
        val name: String get() = contact.name
    }

    data class LatestContact(
        override val position: ListCell.Position,
        override val address: String,
        val name: String,
        val timestamp: Long,
    ) : AddressItem

    data object Space : ContactItem

    data object Loading : ContactItem
}
