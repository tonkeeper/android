package com.tonapps.tonkeeper.fragment.settings.main

import com.tonapps.tonkeeper.fragment.settings.list.item.SettingsItem
import uikit.mvi.UiState

data class SettingsScreenState(
    val items: List<SettingsItem> = emptyList()
): UiState()