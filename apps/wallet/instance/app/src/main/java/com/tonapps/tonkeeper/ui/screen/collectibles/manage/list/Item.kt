package com.tonapps.tonkeeper.ui.screen.collectibles.manage.list

import android.net.Uri
import com.tonapps.uikit.list.BaseListItem
import com.tonapps.uikit.list.ListCell

sealed class Item(type: Int): BaseListItem(type) {

    companion object {
        const val TYPE_TITLE = 1
        const val TYPE_COLLECTION = 2
        const val TYPE_ALL = 3
        const val TYPE_SPACE = 4
        const val TYPE_SAFE_MODE = 5
    }

    data class Title(val title: String): Item(TYPE_TITLE)

    data class Collection(
        val position: ListCell.Position = ListCell.Position.MIDDLE,
        val address: String,
        val title: String,
        val imageUri: Uri,
        val count: Int = 0,
        val spam: Boolean = false,
        val visible: Boolean = true,
    ): Item(TYPE_COLLECTION)

    data object All: Item(TYPE_ALL)

    data object Space: Item(TYPE_SPACE)

    data object SafeMode: Item(TYPE_SAFE_MODE)
}