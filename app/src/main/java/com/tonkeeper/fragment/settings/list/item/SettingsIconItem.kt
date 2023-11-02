package com.tonkeeper.fragment.settings.list.item

import uikit.list.ListCell
import uikit.R

data class SettingsIconItem(
    override val id: Int,
    val titleRes: Int,
    val iconRes: Int = R.drawable.ic_chevron_right_16,
    override val position: ListCell.Position
): SettingsIdItem(ICON_TYPE, id), ListCell