package com.tonapps.tonkeeper.fragment.settings.list.item

data class SettingsTextItem(
    override val id: Int,
    val titleRes: Int,
    val data: String,
    override val position: com.tonapps.uikit.list.ListCell.Position
): SettingsIdItem(TEXT_TYPE, id), com.tonapps.uikit.list.ListCell