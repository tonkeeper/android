package com.tonkeeper.fragment.settings.list.item

import com.tonkeeper.uikit.list.BaseListItem

abstract class SettingsItem(type: Int): BaseListItem(type) {

    companion object {
        const val LOGO = 1
        const val CELL = 2
    }
}