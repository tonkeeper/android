package com.tonapps.tonkeeper.ui.screen.settings.theme

import android.app.Application
import androidx.lifecycle.ViewModel
import com.tonapps.extensions.recreate
import com.tonapps.tonkeeper.ui.base.BaseWalletVM
import com.tonapps.tonkeeper.ui.screen.settings.theme.list.Item
import com.tonapps.uikit.list.ListCell
import com.tonapps.wallet.data.core.Theme
import com.tonapps.wallet.data.settings.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import uikit.extensions.collectFlow

class ThemeViewModel(
    app: Application,
    private val settingsRepository: SettingsRepository
): BaseWalletVM(app) {

    private var currentThemeId = settingsRepository.theme.resId

    private val _uiItemsFlow = MutableStateFlow<List<Item>>(emptyList())
    val uiItemsFlow = _uiItemsFlow.asStateFlow().filter { it.isNotEmpty() }

    init {
        updateValues(currentThemeId)
    }

    fun setTheme(theme: Int) {
        currentThemeId = theme
        updateValues(theme)
        settingsRepository.theme = Theme.getByResId(theme)
        context.recreate()
    }

    private fun updateValues(themeId: Int) {
        val items = mutableListOf<Item>()
        for ((index, theme) in Theme.getSupported().withIndex()) {
            val position = ListCell.getPosition(Theme.getSupported().size, index)
            items.add(Item(
                position = position,
                theme = theme,
                selected = themeId == theme.resId
            ))
        }
        _uiItemsFlow.value = items
    }
}