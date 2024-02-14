package com.tonapps.tonkeeper.fragment.settings.list.item

abstract class SettingsItem(
    type: Int
): com.tonapps.uikit.list.BaseListItem(type) {

    companion object {
        const val LOGO_TYPE = 1
        const val TITLE_TYPE = 2
        const val ICON_TYPE = 3
        const val TEXT_TYPE = 4
        const val ACCOUNT_TYPE = 5
    }
}