package com.tonapps.tonkeeper.ui.screen.country.list

import com.tonapps.uikit.list.BaseListItem
import com.tonapps.uikit.list.ListCell

sealed class Item(type: Int): BaseListItem(type) {

    companion object {
        const val TYPE_COUNTRY = 0
        const val TYPE_SPACE = 1
    }

    data class Country(
        val position: ListCell.Position,
        val code: String,
        val name: String,
        val selected: Boolean,
        val icon: Int
    ): Item(TYPE_COUNTRY)

    data object Space: Item(TYPE_SPACE)

}