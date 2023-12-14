package com.tonkeeper.fragment.passcode.create

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import uikit.mvi.UiFeature

class PasscodeCreateScreenFeature: UiFeature<PasscodeCreateScreenState, PasscodeCreateScreenEffect>(PasscodeCreateScreenState()) {

    fun addNumber(number: Int) {
        val state = uiState.value
        val newState = state.copy(
            code = state.code + number,
        )

        updateUiState { newState }

        if (!newState.isPasscodeComplete) return

        if (!newState.isPasscodeValid) {
            wrongPasscode(newState)
        } else {
            sendEffect(PasscodeCreateScreenEffect.Valid(newState.enteredPasscode))
        }
    }

    private fun wrongPasscode(state: PasscodeCreateScreenState) {
        sendEffect(PasscodeCreateScreenEffect.RepeatError)

        viewModelScope.launch {
            delay(1000)

            val newState = state.copy(
                code = "",
            )

            updateUiState { newState }
        }
    }

    fun removeNumber() {
        val state = uiState.value

        if (state.code.isEmpty()) {
            return
        }

        val newState = state.copy(
            code = state.code.dropLast(1),
        )
        updateUiState { newState }
    }
}