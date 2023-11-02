package com.tonkeeper.fragment.passcode

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.tonkeeper.App
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import uikit.mvi.UiFeature

class PasscodeScreenFeature(
    private val state: Int
): UiFeature<PasscodeScreenState, PasscodeScreenEffect>(PasscodeScreenState()) {

    companion object {
        private const val PASSCODE_LENGTH = 4

        fun factory(state: Int): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return PasscodeScreenFeature(state) as T
                }
            }
        }
    }

    private fun checkCode(numbers: List<Int>) {
        val code = numbers.joinToString("")
        viewModelScope.launch {
            if (state == PasscodeScreen.STATE_CREATE) {
                App.passcode.temporalPinCode = code
                sendEffect(PasscodeScreenEffect.ReEnter)
            } else {
                val result = checkPassCode(state == PasscodeScreen.STATE_CHECK, code)
                if (result) {
                    App.passcode.setPinCode(code)
                    sendEffect(PasscodeScreenEffect.Success)
                    delay(1000)
                    sendEffect(PasscodeScreenEffect.Close)
                } else {
                    sendEffect(PasscodeScreenEffect.Failure)
                    delay(1000)
                    clearNumbers()
                }
            }
        }
    }

    private suspend fun checkPassCode(checkTemporal: Boolean, code: String): Boolean {
        return if (checkTemporal) {
            App.passcode.checkTemporalPinCode(code)
        } else {
            App.passcode.checkPinCode(code)
        }
    }

    fun clearNumbers() {
        updateUiState {
            it.copy(
                numbers = emptyList(),
                backspace = false
            )
        }
    }

    fun addNumber(number: Int) {
        val numbers = uiState.value.numbers.toMutableList()
        if (numbers.size == PASSCODE_LENGTH) {
            return
        }
        numbers.add(number)
        if (numbers.size == PASSCODE_LENGTH) {
            checkCode(numbers)
        }
        updateUiState {
            it.copy(
                numbers = numbers,
                backspace = numbers.size > 0,
                error = false
            )
        }
    }

    fun backspace() {
        val numbers = uiState.value.numbers.toMutableList()
        if (numbers.isEmpty()) {
            return
        }
        numbers.removeAt(numbers.size - 1)
        updateUiState {
            it.copy(
                numbers = numbers,
                backspace = numbers.size > 0
            )
        }
    }
}