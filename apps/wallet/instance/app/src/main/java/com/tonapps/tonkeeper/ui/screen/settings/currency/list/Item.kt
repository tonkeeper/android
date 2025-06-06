package com.tonapps.tonkeeper.ui.screen.settings.currency.list

import com.tonapps.uikit.list.BaseListItem
import com.tonapps.uikit.list.ListCell

data class Item(
    val currency: String,
    val name: String,
    val selected: Boolean,
    val position: ListCell.Position
): BaseListItem()