package com.tonapps.tonkeeper.ui.screen.settings.theme

import androidx.lifecycle.ViewModel
import com.tonapps.wallet.data.settings.SettingsRepository
import kotlinx.coroutines.flow.map

class ThemeViewModel(
    private val settingsRepository: SettingsRepository
): ViewModel() {

    val currentThemeFlow = settingsRepository.themeFlow.map {
        if (it == "dark") {
            uikit.R.style.Theme_App_Dark
        } else {
            uikit.R.style.Theme_App_Blue
        }
    }

    fun setTheme(theme: Int) {
        settingsRepository.theme = if (theme == uikit.R.style.Theme_App_Dark) "dark" else "blue"
    }
}