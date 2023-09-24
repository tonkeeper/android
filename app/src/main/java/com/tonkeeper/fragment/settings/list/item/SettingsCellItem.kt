package com.tonkeeper.fragment.settings.list.item

import com.tonkeeper.uikit.list.BaseListItem

data class SettingsCellItem(
    val id: Int,
    val titleRes: Int,
    val iconRes: Int = 0,
    val right: String? = null,
    override val position: Cell.Position,
): SettingsItem(CELL), BaseListItem.Cell {

    companion object {
        const val LOGOUT_ID = 1
        const val CURRENCY_ID = 2
        const val SECURITY_ID = 3
    }

    val hasIcon: Boolean = iconRes != 0
}
