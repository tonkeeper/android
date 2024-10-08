package com.tonapps.wallet.data.passcode

import android.content.Context
import androidx.biometric.BiometricPrompt
import com.tonapps.wallet.data.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.withContext

class LockScreen(
    private val passcodeManager: PasscodeManager,
    private val settingsRepository: SettingsRepository
) {

    sealed class State {
        data object None: State()
        data object Input: State()
        data object Biometric: State()
        data object Error: State()
    }

    private val _stateFlow = MutableStateFlow<State?>(null)
    val stateFlow = _stateFlow.asStateFlow().filterNotNull()

    fun init() {
        if (!settingsRepository.lockScreen) {
            hide()
        } else if (settingsRepository.biometric) {
            _stateFlow.value = State.Biometric
        } else {
            _stateFlow.value = State.Input
        }
    }

    private fun hide() {
        _stateFlow.value = State.None
    }

    private fun error() {
        _stateFlow.value = State.Error
    }

    suspend fun check(context: Context, code: String) = withContext(Dispatchers.IO) {
        val valid = passcodeManager.isValid(context, code)
        if (valid) {
            hide()
        } else {
            error()
        }
    }

    fun biometric(result: BiometricPrompt.AuthenticationResult) {
        hide()
    }

    fun reset() {

    }

}