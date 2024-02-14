package com.tonapps.tonkeeper.fragment.settings.list.item

import com.tonapps.uikit.list.ListCell
import com.tonapps.wallet.data.account.entities.WalletLabel

data class SettingsAccountItem(
    override val id: Int,
    val label: WalletLabel,
    override val position: ListCell.Position,
): SettingsIdItem(ACCOUNT_TYPE, id), ListCell {

    val color: Int
        get() = label.color

    val emoji: CharSequence
        get() = label.emoji

    val name: String
        get() = label.name
}