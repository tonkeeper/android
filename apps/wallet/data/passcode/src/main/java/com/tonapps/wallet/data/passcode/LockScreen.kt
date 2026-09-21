package com.tonapps.wallet.data.passcode

import android.content.Context
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

        // Not a data object: StateFlow dedupes equal values, and a second failed attempt must
        // re-emit to reach the UI again.
        class Error : State()
    }

    private val _stateFlow = MutableStateFlow<State?>(null)
    val stateFlow = _stateFlow.asStateFlow().filterNotNull()

    private val _hiddenFlow = MutableStateFlow(false)
    val hiddenFlow = _hiddenFlow.asStateFlow()

    suspend fun init() {
        if (!passcodeManager.hasPinCode()) {
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

    fun hidden() {
        _hiddenFlow.value = true
    }

    private fun error() {
        _stateFlow.value = State.Error()
    }

    suspend fun check(context: Context, code: String) = withContext(Dispatchers.IO) {
        if (passcodeManager.isValid(context, code)) {
            hide()
        } else {
            error()
        }
    }

    fun biometric() {
        hide()
    }

    fun reset() {
    }

}