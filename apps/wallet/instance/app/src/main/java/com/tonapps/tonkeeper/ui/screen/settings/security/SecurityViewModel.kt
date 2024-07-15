package com.tonapps.tonkeeper.ui.screen.settings.security

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tonapps.wallet.data.passcode.PasscodeManager
import com.tonapps.wallet.data.rn.RNLegacy
import com.tonapps.wallet.data.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch

class SecurityViewModel(
    private val settingsRepository: SettingsRepository,
    private val rnLegacy: RNLegacy,
    private val passcodeManager: PasscodeManager,
): ViewModel() {

    var lockScreen: Boolean
        get() = settingsRepository.lockScreen
        set(value) {
            settingsRepository.lockScreen = value
        }

    val biometric: Boolean
        get() = settingsRepository.biometric

    fun enableBiometric(context: Context, value: Boolean) = flow {
        if (value) {
            val code = passcodeManager.requestValidPasscode(context)
            rnLegacy.setupBiometry(code)
        } else {
            rnLegacy.removeBiometry()
        }
        settingsRepository.biometric = value
        emit(Unit)
    }.flowOn(Dispatchers.IO)
}