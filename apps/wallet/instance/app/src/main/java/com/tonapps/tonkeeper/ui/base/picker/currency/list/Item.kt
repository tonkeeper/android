package com.tonapps.tonkeeper.ui.base.picker.currency.list

import android.net.Uri
import com.tonapps.extensions.toUriOrNull
import com.tonapps.tonkeeper.os.AndroidCurrency
import com.tonapps.uikit.list.BaseListItem
import com.tonapps.uikit.list.ListCell
import com.tonapps.wallet.data.core.currency.WalletCurrency

data class Item(
    val position: ListCell.Position,
    val currency: WalletCurrency,
): BaseListItem() {

    val code: String
        get() = currency.code

    val name: String
        get() = currency.title

    val drawableRes: Int?
        get() = currency.drawableRes

    val iconUri: Uri?
        get() = currency.iconUrl?.toUriOrNull()
}