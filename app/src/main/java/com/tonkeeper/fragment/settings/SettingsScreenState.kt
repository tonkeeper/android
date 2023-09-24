package com.tonkeeper.fragment.settings

import com.tonkeeper.fragment.settings.list.item.SettingsItem
import com.tonkeeper.uikit.mvi.UiState

data class SettingsScreenState(
    val items: List<SettingsItem> = emptyList()
): UiState()