package com.tonapps.tonkeeper.ui.screen.browser.explore.list

import android.net.Uri
import com.tonapps.uikit.list.BaseListItem
import com.tonapps.wallet.data.browser.entities.BrowserAppEntity

sealed class Item(type: Int): BaseListItem(type) {

    companion object {
        const val TYPE_TITLE = 0
        const val TYPE_APP = 2
        const val TYPE_BANNERS = 3
    }

    data class Title(val title: String): Item(TYPE_TITLE)

    data class App(
        val app: BrowserAppEntity
    ): Item(TYPE_APP) {

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
    ): Item(TYPE_BANNERS)

}