package com.tonapps.tonkeeper.ui.screen.settings.apps.list

import com.tonapps.uikit.list.BaseListItem
import com.tonapps.uikit.list.ListCell
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.dapps.entities.AppEntity

sealed class Item(type: Int): BaseListItem(type) {

    companion object {
        const val TYPE_APP = 0
        const val TYPE_DISCONNECT_ALL = 2
        const val TYPE_EMPTY = 3
    }

    data object DisconnectAll: Item(TYPE_DISCONNECT_ALL)

    data object Empty: Item(TYPE_EMPTY)

    data class App(
        val app: AppEntity,
        val wallet: WalletEntity,
        val position: ListCell.Position
    ): Item(TYPE_APP) {

        val iconUrl: String
            get() = app.iconUrl

        val title: String
            get() = app.name

        val host: String
            get() = app.host
    }
}