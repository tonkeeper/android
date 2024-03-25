package com.tonapps.tonkeeper.ui.screen.settings.language.list

import com.tonapps.uikit.list.BaseListItem
import com.tonapps.uikit.list.ListCell

data class Item(
    val name: String,
    val nameLocalized: String = "",
    val selected: Boolean = false,
    val code: String,
    val position: ListCell.Position
): BaseListItem()