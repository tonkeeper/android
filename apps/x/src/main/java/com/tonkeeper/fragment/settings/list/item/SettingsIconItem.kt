package com.tonkeeper.fragment.settings.list.item

import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import uikit.list.ListCell
import uikit.R

data class SettingsIconItem(
    override val id: Int,
    val titleRes: Int,
    @DrawableRes val iconRes: Int = R.drawable.ic_chevron_right_16,
    @ColorRes val colorRes: Int = 0,
    override val position: ListCell.Position,
    val dot: Boolean = false
): SettingsIdItem(ICON_TYPE, id), ListCell