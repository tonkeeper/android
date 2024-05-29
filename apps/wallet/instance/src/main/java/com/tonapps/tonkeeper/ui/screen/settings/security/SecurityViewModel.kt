package com.tonapps.tonkeeper.ui.screen.settings.security

import androidx.lifecycle.ViewModel
import com.tonapps.wallet.data.settings.SettingsRepository

class SecurityViewModel(
    private val settingsRepository: SettingsRepository
): ViewModel() {

    var lockScreen: Boolean
        get() = settingsRepository.lockScreen
        set(value) {
            settingsRepository.lockScreen = value
        }

    var biometric: Boolean
        get() = settingsRepository.biometric
        set(value) {
            settingsRepository.biometric = value
        }
}