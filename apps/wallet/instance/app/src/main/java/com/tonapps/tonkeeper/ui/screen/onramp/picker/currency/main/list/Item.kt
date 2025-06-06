package com.tonapps.tonkeeper.ui.screen.onramp.picker.currency.main.list

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import com.tonapps.extensions.toUriOrNull
import com.tonapps.uikit.list.BaseListItem
import com.tonapps.uikit.list.ListCell
import com.tonapps.wallet.api.entity.TokenEntity
import com.tonapps.wallet.data.core.currency.WalletCurrency

sealed class Item(type: Int): BaseListItem(type) {

    companion object {
        const val TYPE_TITLE = 1
        const val TYPE_CURRENCY = 2
        const val TYPE_MORE = 3
    }

    data class Title(
        val title: CharSequence,
        val icons: List<Uri> = emptyList(),
        val withoutMargin: Boolean = false
    ): Item(TYPE_TITLE)

    data class Currency(
        val position: ListCell.Position,
        val id: String,
        val icon: Uri? = null,
        val iconRes: Int? = null,
        val code: CharSequence,
        val title: CharSequence,
        val network: String,
        val currency: WalletCurrency,
        val selected: Boolean,
    ): Item(TYPE_CURRENCY) {

        val isFiat: Boolean
            get() = network.equals("FIAT", true)

        constructor(
            currency: WalletCurrency,
            position: ListCell.Position,
            selected: Boolean = false
        ): this(
            position = position,
            id = currency.code,
            icon = currency.iconUrl?.toUriOrNull(),
            iconRes = currency.drawableRes,
            code = currency.code,
            title = currency.title,
            network = currency.chain.name,
            currency = currency,
            selected = selected
        )

        constructor(
            token: TokenEntity,
            position: ListCell.Position,
            code: CharSequence = token.symbol,
            title: CharSequence = token.name,
            selected: Boolean = false
        ): this(
            position = position,
            id = token.address,
            icon = token.imageUri,
            code = code,
            title = title,
            network = "TON",
            currency = WalletCurrency(
                code = token.symbol,
                title = token.name,
                chain = WalletCurrency.Chain.TON(token.address, token.decimals),
                iconUrl = token.imageUri.toString()
            ),
            selected = selected,
        )

        fun contains(query: String): Boolean {
            return code.contains(query, true) ||
                    title.contains(query, true) ||
                    network.contains(query, true)
        }
    }

    data class More(
        val position: ListCell.Position = ListCell.Position.LAST,
        val id: String,
        val title: CharSequence,
        val values: List<WalletCurrency> = emptyList()
    ): Item(TYPE_MORE) {

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as More

            if (position != other.position) return false
            if (id != other.id) return false
            if (title != other.title) return false

            return true
        }

        override fun hashCode(): Int {
            var result = position.hashCode()
            result = 31 * result + id.hashCode()
            result = 31 * result + title.hashCode()
            return result
        }
    }
}