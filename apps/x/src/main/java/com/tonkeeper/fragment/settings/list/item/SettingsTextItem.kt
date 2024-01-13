package com.tonkeeper.fragment.settings.list.item

import uikit.list.ListCell

data class SettingsTextItem(
    override val id: Int,
    val titleRes: Int,
    val data: String,
    override val position: ListCell.Position
): SettingsIdItem(TEXT_TYPE, id), ListCell