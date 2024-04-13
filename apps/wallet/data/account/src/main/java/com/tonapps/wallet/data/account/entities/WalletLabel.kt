package com.tonapps.wallet.data.account.entities

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class WalletLabel(
    val name: String,
    val emoji: CharSequence,
    val color: Int
): Parcelable {

    val isEmpty: Boolean
        get() = name.isBlank() && emoji.isBlank()

    val title: CharSequence?
        get() = if (isEmpty) null else String.format("%s %s", emoji, name)
}