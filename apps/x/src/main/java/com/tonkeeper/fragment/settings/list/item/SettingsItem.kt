package com.tonkeeper.fragment.settings.list.item

import uikit.list.BaseListItem

abstract class SettingsItem(
    type: Int
): BaseListItem(type) {

    companion object {
        const val LOGO_TYPE = 1
        const val TITLE_TYPE = 2
        const val ICON_TYPE = 3
        const val TEXT_TYPE = 4
    }
}