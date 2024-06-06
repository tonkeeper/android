package com.tonapps.tonkeeper.ui.screen.wallet.manage.list

import android.net.Uri
import androidx.annotation.StringRes
import com.tonapps.icu.Coins
import com.tonapps.icu.CurrencyFormatter
import com.tonapps.tonkeeper.core.entities.TokenExtendedEntity
import com.tonapps.uikit.list.BaseListItem
import com.tonapps.uikit.list.ListCell

sealed class Item(type: Int): BaseListItem(type) {

    companion object {
        const val TYPE_TITLE = 1
        const val TYPE_TOKEN = 2
        const val TYPE_SPACE = 3
    }

    data class Title(
        @StringRes val titleRes: Int,
        @StringRes val detailsRes: Int = 0
    ): Item(TYPE_TITLE)

    data class Token(
        val position: ListCell.Position,
        val iconUri: Uri,
        val address: String,
        val symbol: String,
        val balance: Coins,
        val balanceFormat: CharSequence,
        val pinned: Boolean,
        val hidden: Boolean
    ): Item(TYPE_TOKEN) {

        constructor(
            position: ListCell.Position,
            token: TokenExtendedEntity,
        ) : this(
            position = position,
            iconUri = token.imageUri,
            address = token.address,
            symbol = token.symbol,
            balance = token.balance.value,
            balanceFormat = CurrencyFormatter.format(token.symbol, token.balance.value),
            pinned = token.pinned,
            hidden = token.hidden
        )
    }

    data object Space: Item(TYPE_SPACE)
}