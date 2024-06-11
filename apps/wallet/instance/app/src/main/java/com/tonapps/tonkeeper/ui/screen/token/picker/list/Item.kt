package com.tonapps.tonkeeper.ui.screen.token.picker.list

import android.net.Uri
import com.tonapps.uikit.list.BaseListItem
import com.tonapps.uikit.list.ListCell
import com.tonapps.wallet.data.token.entities.AccountTokenEntity

sealed class Item(type: Int): BaseListItem(type) {

    companion object {
        const val TYPE_TOKEN = 0
        const val TYPE_SKELETON = 1
    }

    data class Skeleton(
        val position: ListCell.Position,
    ): Item(TYPE_SKELETON)

    data class Token(
        val position: ListCell.Position,
        val raw: AccountTokenEntity,
        val selected: Boolean,
        val balance: CharSequence
    ): Item(TYPE_TOKEN) {

        val iconUri: Uri
            get() = raw.imageUri

        val symbol: String
            get() = raw.symbol
    }
}