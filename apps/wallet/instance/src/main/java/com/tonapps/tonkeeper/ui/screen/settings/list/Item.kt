package com.tonapps.tonkeeper.ui.screen.settings.list

import com.tonapps.uikit.list.BaseListItem
import com.tonapps.uikit.list.ListCell
import com.tonapps.wallet.data.account.WalletType
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.account.entities.WalletLabel
import com.tonapps.wallet.localization.Localization

sealed class Item(type: Int): BaseListItem(type) {

    companion object {
        const val TYPE_ACCOUNT = 0
        const val TYPE_SPACE = 1
        const val TYPE_TEXT = 2
    }

    data class Account(
        val title: String,
        val emoji: String,
        val color: Int,
        val walletType: WalletType
    ): Item(TYPE_ACCOUNT) {

        constructor(wallet: WalletEntity) : this(
            title = wallet.label.name,
            emoji = wallet.label.emoji.toString(),
            color = wallet.label.color,
            walletType = wallet.type
        )
    }

    data object Space: Item(TYPE_SPACE)

    open class Text(
        val titleRes: Int,
        val value: String,
        val position: ListCell.Position
    ): Item(TYPE_TEXT)

    class Currency(
        code: String,
        position: ListCell.Position
    ) : Text(
        titleRes = Localization.currency,
        value = code,
        position = position
    )

    class Language(
        data: String,
        position: ListCell.Position
    ) : Text(
        titleRes = Localization.language,
        value = data,
        position = position
    )
}