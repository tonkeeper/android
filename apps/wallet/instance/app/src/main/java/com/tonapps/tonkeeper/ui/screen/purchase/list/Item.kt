package com.tonapps.tonkeeper.ui.screen.purchase.list

import android.net.Uri
import com.tonapps.uikit.list.BaseListItem
import com.tonapps.uikit.list.ListCell
import com.tonapps.wallet.data.purchase.entity.PurchaseCategoryEntity
import com.tonapps.wallet.data.purchase.entity.PurchaseMethodEntity

sealed class Item(type: Int): BaseListItem(type) {

    companion object {
        const val TYPE_TITLE = 1
        const val TYPE_METHOD = 2
        const val TYPE_SPACE = 3
    }

    data class Title(
        val title: String
    ): Item(TYPE_TITLE)

    data class Method(
        val entity: PurchaseMethodEntity,
        val position: ListCell.Position,
        val categoryType: String,
    ): Item(TYPE_METHOD) {

        val iconUri: Uri
            get() = Uri.parse(entity.iconUrl)

        val title: String
            get() = entity.title

        val description: String
            get() = entity.description
    }

    data object Space: Item(TYPE_SPACE)
}