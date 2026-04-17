package com.tonapps.blockchain.model.legacy

import android.os.Parcelable
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize

sealed class Wallet {

    companion object {

        fun typeOf(id: Int): WalletType {
            return WalletType.entries.find { it.id == id } ?: WalletType.Default
        }
    }

    @Parcelize
    data class Label(
        val accountName: String = "",
        val emoji: CharSequence = "",
        val color: Int = WalletColor.all.first()
    ): Parcelable {

        @IgnoredOnParcel
        val isEmpty: Boolean by lazy {
            accountName.isBlank() && emoji.isBlank()
        }

        val name: String
            get() = accountName

        @IgnoredOnParcel
        val title: CharSequence? by lazy {
            if (isEmpty) {
                null
            } else if (emoji.startsWith("custom_")) {
                name
            } else {
                String.format("%s %s", emoji, name)
            }
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as Label
            if (accountName != other.accountName) return false
            if (emoji != other.emoji) return false
            if (color != other.color) return false
            return true
        }

        override fun hashCode(): Int {
            var result = color
            result = 31 * result + accountName.hashCode()
            result = 31 * result + emoji.hashCode()
            result = 31 * result + isEmpty.hashCode()
            result = 31 * result + name.hashCode()
            result = 31 * result + (title?.hashCode() ?: 0)
            return result
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