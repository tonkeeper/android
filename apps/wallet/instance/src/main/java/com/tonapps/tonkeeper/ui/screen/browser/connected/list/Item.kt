package com.tonapps.tonkeeper.ui.screen.browser.connected.list

import android.net.Uri
import android.util.Log
import com.tonapps.uikit.list.BaseListItem
import com.tonapps.wallet.data.tonconnect.entities.DAppEntity

data class Item(
    val app: DAppEntity
): BaseListItem(0) {

    val icon: Uri
        get() = Uri.parse(app.manifest.iconUrl)

    val name: String
        get() = app.manifest.name

    val url: Uri
        get() = Uri.parse(app.manifest.url)

    val host: String
        get() = url.host!!

    init {
        Log.d("ItemLog", "Item: $app")
    }
}