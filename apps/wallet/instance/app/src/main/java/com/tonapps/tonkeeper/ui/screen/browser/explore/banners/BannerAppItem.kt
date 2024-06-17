package com.tonapps.tonkeeper.ui.screen.browser.explore.banners

import android.net.Uri
import com.tonapps.uikit.list.BaseListItem
import com.tonapps.wallet.data.browser.entities.BrowserAppEntity

data class BannerAppItem(
    val app: BrowserAppEntity
): BaseListItem(0) {

    companion object {
        fun createApps(apps: List<BrowserAppEntity>): List<BannerAppItem> {
            return apps.map {
                BannerAppItem(it)
            }
        }
    }

    val icon: Uri
        get() = app.icon

    val name: String
        get() = app.name

    val url: Uri
        get() = app.url

    val host: String
        get() = url.host!!

    val poster: Uri
        get() = app.poster!!

    val description: String
        get() = app.description

    val textColor: Int
        get() = app.textColor
}