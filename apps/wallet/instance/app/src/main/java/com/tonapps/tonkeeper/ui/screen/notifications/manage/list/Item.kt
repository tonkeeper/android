package com.tonapps.tonkeeper.ui.screen.notifications.manage.list

import android.net.Uri
import com.tonapps.uikit.list.BaseListItem
import com.tonapps.uikit.list.ListCell
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.dapps.entities.AppEntity

sealed class Item(type: Int): BaseListItem(type) {

    companion object {
        const val TYPE_WALLET = 1
        const val TYPE_SPACE = 2
        const val TYPE_APPS_HEADER = 3
        const val TYPE_APP = 4
    }

    data class Wallet(
        val wallet: WalletEntity,
        val pushEnabled: Boolean,
    ): Item(TYPE_WALLET)

    data object Space: Item(TYPE_SPACE)

    data object AppsHeader: Item(TYPE_APPS_HEADER)

    data class App(
        val app: AppEntity,
        val wallet: WalletEntity,
        val pushEnabled: Boolean,
        val position: ListCell.Position
    ): Item(TYPE_APP) {

        val icon: String
            get() = app.iconUrl

        val name: String
            get() = app.name

        val uri: Uri
            get() = app.url
    }
}