package com.tonapps.tonkeeper.ui.screen.settings.security

import androidx.lifecycle.ViewModel
import com.tonapps.tonkeeper.password.PasscodeRepository
import com.tonapps.wallet.data.settings.SettingsRepository

class SecurityViewModel(
    private val settingsRepository: SettingsRepository,
    private val passcodeRepository: PasscodeRepository
): ViewModel() {


    fun enableLockScreen(enable: Boolean) {
        settingsRepository.lockScreen = enable
    }
}