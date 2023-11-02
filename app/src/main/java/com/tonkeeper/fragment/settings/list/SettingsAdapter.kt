package com.tonkeeper.fragment.settings.list

import android.view.ViewGroup
import com.tonkeeper.fragment.settings.list.holder.SettingsIconHolder
import com.tonkeeper.fragment.settings.list.holder.SettingsLogoHolder
import com.tonkeeper.fragment.settings.list.holder.SettingsTextHolder
import com.tonkeeper.fragment.settings.list.holder.SettingsTitleHolder
import com.tonkeeper.fragment.settings.list.item.SettingsItem
import uikit.list.BaseListAdapter
import uikit.list.BaseListHolder
import uikit.list.BaseListItem

class SettingsAdapter(
    items: List<SettingsItem>,
    private val onClick: (SettingsItem) -> Unit
): BaseListAdapter<SettingsItem>(items) {

    override fun createHolder(parent: ViewGroup, viewType: Int): BaseListHolder<out BaseListItem> {
        return when (viewType) {
            SettingsItem.LOGO_TYPE -> SettingsLogoHolder(parent, onClick)
            SettingsItem.ICON_TYPE -> SettingsIconHolder(parent, onClick)
            SettingsItem.TEXT_TYPE -> SettingsTextHolder(parent, onClick)
            SettingsItem.TITLE_TYPE -> SettingsTitleHolder(parent)
            else -> throw IllegalArgumentException("Unknown viewType: $viewType")
        }
    }
}