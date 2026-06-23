package com.tonapps.tonkeeper.ui.screen.tronfees.list

import com.tonapps.icu.Coins
import com.tonapps.tonkeeper.ui.screen.tronfees.TronFeesScreenType
import com.tonapps.uikit.list.BaseListItem
import com.tonapps.uikit.list.ListCell
import com.tonapps.blockchain.model.legacy.TokenEntity
import com.tonapps.blockchain.model.legacy.WalletEntity

sealed class Item(type: Int): BaseListItem(type) {

    companion object {
        const val TYPE_HEADER = 0
        const val TYPE_TOKEN = 1
        const val TYPE_BATTERY = 2
        const val TYPE_LEARN_MORE = 3
    }

    data class Token(
        val position: ListCell.Position,
        val wallet: WalletEntity,
        val token: TokenEntity,
        val amountFormat: CharSequence,
        val balanceFormat: CharSequence,
        val transfersCount: Int? = null,
    ): Item(TYPE_TOKEN)

    data class Battery(
        val position: ListCell.Position,
        val wallet: WalletEntity,
        val amountFormat: CharSequence,
        val balanceFormat: CharSequence,
    ): Item(TYPE_BATTERY)

    data class Header(
        val screenType: TronFeesScreenType,
        val onlyTrx: Boolean
    ): Item(TYPE_HEADER)

    data class LearnMore(
        val url: String
    ): Item(TYPE_LEARN_MORE)

}


