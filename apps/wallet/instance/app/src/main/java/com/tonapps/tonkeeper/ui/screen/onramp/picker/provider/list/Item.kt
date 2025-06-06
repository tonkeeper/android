package com.tonapps.tonkeeper.ui.screen.onramp.picker.provider.list

import android.net.Uri
import com.tonapps.uikit.list.BaseListItem
import com.tonapps.uikit.list.ListCell
import com.tonapps.wallet.data.purchase.entity.PurchaseMethodEntity
import androidx.core.net.toUri

data class Item(
    val position: ListCell.Position,
    val provider: PurchaseMethodEntity,
    val selected: Boolean,
    val best: Boolean
): BaseListItem(0) {

    val iconUri: Uri
        get() = provider.iconUrl.toUri()

    val title: String
        get() = provider.title

    val description: String
        get() = provider.description
}

