package com.tonkeeper.fragment.settings.language.list

import uikit.list.BaseListItem
import uikit.list.ListCell

data class LanguageItem(
    val name: String,
    val nameLocalized: String = "",
    val selected: Boolean = false,
    val code: String,
    override val position: ListCell.Position
): BaseListItem(), ListCell