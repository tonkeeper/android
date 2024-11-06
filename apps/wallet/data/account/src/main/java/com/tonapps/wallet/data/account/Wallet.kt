package com.tonapps.wallet.data.account

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

sealed class Wallet {

    companion object {

        fun typeOf(id: Int): Type {
            return Type.entries.find { it.id == id } ?: Type.Default
        }
    }

    enum class Type(val id: Int) {
        Default(0), Watch(1), Testnet(2), Signer(3), Lockup(4), Ledger(5), SignerQR(6), Keystone(7)
    }

    @Parcelize
    data class Label(
        val accountName: String,
        val emoji: CharSequence,
        val color: Int
    ): Parcelable {

        val isEmpty: Boolean
            get() = accountName.isBlank() && emoji.isBlank()

        val name: String
            get() = accountName

        val title: CharSequence?
            get() = if (isEmpty) {
                null
            } else if (emoji.startsWith("custom_")) {
                name
            } else {
                String.format("%s %s", emoji, name)
            }
    }


    data class NewLabel(
        val names: List<String>,
        val emoji: CharSequence,
        val color: Int
    ) {

        fun create(index: Int): Label {
            val name = names.getOrNull(index) ?: "Wallet"
            return Label(name, emoji, color)
        }
    }

}