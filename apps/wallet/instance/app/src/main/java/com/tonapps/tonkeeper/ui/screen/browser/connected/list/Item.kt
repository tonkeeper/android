package com.tonapps.tonkeeper.ui.screen.browser.connected.list

import android.net.Uri
import com.tonapps.uikit.list.BaseListItem
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.dapps.entities.AppEntity

data class Item(
    val wallet: WalletEntity,
    val app: AppEntity,
): BaseListItem(0) {

    val icon: Uri
        get() = Uri.parse(app.iconUrl)

    val name: String
        get() = app.name

    val url: Uri
        get() = app.url

    val host: String
        get() = app.url.host ?: ""
}