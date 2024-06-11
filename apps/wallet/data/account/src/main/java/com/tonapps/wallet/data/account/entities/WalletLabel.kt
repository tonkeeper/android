package com.tonapps.wallet.data.account.entities

import android.os.Parcelable
import com.tonapps.wallet.data.account.backport.data.RNWallet
import com.tonapps.wallet.data.account.backport.data.RNWallet.Companion.int
import kotlinx.parcelize.Parcelize

@Parcelize
data class WalletLabel(
    val accountName: String,
    val emoji: CharSequence,
    val color: Int
): Parcelable {

    val isEmpty: Boolean
        get() = accountName.isBlank() && emoji.isBlank()

    val name: String
        get() = accountName.ifBlank { "Wallet" }

    val title: CharSequence?
        get() = if (isEmpty) {
            null
        } else if (emoji.startsWith("custom_")) {
            name
        } else {
            String.format("%s %s", emoji, name)
        }

    constructor(rn: RNWallet): this(
        accountName = if (rn.name.startsWith("ic_")) "\uD83D\uDC8E" else rn.name,
        emoji = rn.emoji,
        color = rn.color.int
    )
}