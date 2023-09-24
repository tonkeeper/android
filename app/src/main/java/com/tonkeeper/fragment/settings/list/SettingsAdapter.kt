package com.tonkeeper.fragment.settings.list

import android.view.ViewGroup
import com.tonkeeper.fragment.settings.list.holder.SettingsCellHolder
import com.tonkeeper.fragment.settings.list.holder.SettingsLogoHolder
import com.tonkeeper.fragment.settings.list.item.SettingsItem
import com.tonkeeper.uikit.list.BaseListAdapter
import com.tonkeeper.uikit.list.BaseListHolder
import com.tonkeeper.uikit.list.BaseListItem

class SettingsAdapter(
    items: List<SettingsItem>
): BaseListAdapter<SettingsItem>(items) {

    override fun createHolder(parent: ViewGroup, viewType: Int): BaseListHolder<out BaseListItem> {
        return when (viewType) {
            SettingsItem.LOGO -> SettingsLogoHolder(parent)
            SettingsItem.CELL -> SettingsCellHolder(parent)
            else -> throw IllegalArgumentException("Unknown viewType: $viewType")
        }
    }
}