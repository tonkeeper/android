package com.tonapps.tonkeeper.ui.screen.browser.more.list

import android.net.Uri
import com.tonapps.uikit.list.BaseListItem
import com.tonapps.uikit.list.ListCell
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.browser.entities.BrowserAppEntity

data class Item(
    val wallet: WalletEntity,
    val app: BrowserAppEntity,
    val position: ListCell.Position,
    val country: String
): BaseListItem(0) {

    val icon: Uri
        get() = app.icon

    val name: String
        get() = app.name

    val url: Uri
        get() = app.url

    val description: String
        get() = app.description
}