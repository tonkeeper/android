package com.tonapps.wallet.data.account.entities

data class WalletLabel(
    val name: String,
    val emoji: CharSequence,
    val color: Int
) {

    val isEmpty: Boolean
        get() = name.isBlank() && emoji.isBlank()
}