package com.tonapps.tonkeeper.ui.screen.browser.more.list

import android.net.Uri
import com.tonapps.uikit.list.BaseListItem
import com.tonapps.uikit.list.ListCell
import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.wallet.data.browser.entities.BrowserAppEntity

sealed class Item(type: Int): BaseListItem(type) {

    companion object {
        const val TYPE_APP = 0
        const val TYPE_CHAIN_FILTER = 1
        const val TYPE_CHAIN_EMPTY = 2
    }

    data object ChainFilter : Item(TYPE_CHAIN_FILTER)

    data object ChainEmpty : Item(TYPE_CHAIN_EMPTY)

    data class App(
        val wallet: WalletEntity,
        val app: BrowserAppEntity,
        val position: ListCell.Position,
        val country: String,
        val multichain: Boolean
    ): Item(TYPE_APP) {

        val icon: Uri
            get() = app.icon

        val name: String
            get() = app.name

        val url: Uri
            get() = app.url

        val description: String
            get() = app.description
    }
}
