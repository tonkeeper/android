package com.tonapps.tonkeeper.ui.screen.browser.main.list.explore.list

import android.net.Uri
import com.tonapps.uikit.list.BaseListItem
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.browser.entities.BrowserAppEntity

sealed class ExploreItem(type: Int): BaseListItem(type) {

    companion object {
        const val TYPE_TITLE = 0
        const val TYPE_APP = 2
        const val TYPE_BANNERS = 3
        const val TYPE_SPACE = 4
    }

    data class Title(
        val title: String,
        val id: String
    ): ExploreItem(TYPE_TITLE)

    data class App(
        val app: BrowserAppEntity,
        val wallet: WalletEntity,
        val singleLine: Boolean
    ): ExploreItem(TYPE_APP) {

        val icon: Uri
            get() = app.icon

        val name: String
            get() = app.name

        val url: Uri
            get() = app.url

        val host: String
            get() = url.host!!

        val textColor: Int
            get() = app.textColor
    }

    data class Banners(
        val apps: List<BrowserAppEntity>,
        val interval: Int,
        val wallet: WalletEntity,
    ): ExploreItem(TYPE_BANNERS)

}