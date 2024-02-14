package com.tonapps.tonkeeper.fragment.settings.list

import android.view.View
import android.view.ViewGroup
import com.tonapps.tonkeeper.fragment.settings.list.holder.SettingsAccountHolder
import com.tonapps.tonkeeper.fragment.settings.list.holder.SettingsIconHolder
import com.tonapps.tonkeeper.fragment.settings.list.holder.SettingsLogoHolder
import com.tonapps.tonkeeper.fragment.settings.list.holder.SettingsTextHolder
import com.tonapps.tonkeeper.fragment.settings.list.holder.SettingsTitleHolder
import com.tonapps.tonkeeper.fragment.settings.list.item.SettingsItem

class SettingsAdapter(
    private val onClick: (SettingsItem, View) -> Unit
): com.tonapps.uikit.list.BaseListAdapter() {

    override fun createHolder(parent: ViewGroup, viewType: Int): com.tonapps.uikit.list.BaseListHolder<out com.tonapps.uikit.list.BaseListItem> {
        return when (viewType) {
            SettingsItem.LOGO_TYPE -> SettingsLogoHolder(parent, onClick)
            SettingsItem.ICON_TYPE -> SettingsIconHolder(parent, onClick)
            SettingsItem.TEXT_TYPE -> SettingsTextHolder(parent, onClick)
            SettingsItem.ACCOUNT_TYPE -> SettingsAccountHolder(parent, onClick)
            SettingsItem.TITLE_TYPE -> SettingsTitleHolder(parent)
            else -> throw IllegalArgumentException("Unknown viewType: $viewType")
        }
    }
}