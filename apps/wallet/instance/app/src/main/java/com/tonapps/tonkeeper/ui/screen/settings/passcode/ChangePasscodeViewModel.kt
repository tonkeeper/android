package com.tonapps.tonkeeper.ui.screen.settings.passcode

import android.app.Application
import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tonapps.extensions.MutableEffectFlow
import com.tonapps.tonkeeper.ui.base.BaseWalletVM
import com.tonapps.wallet.data.passcode.PasscodeManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

class ChangePasscodeViewModel(
    app: Application,
    private val passcodeManager: PasscodeManager,
    savedStateHandle: SavedStateHandle
): BaseWalletVM(app) {

    enum class Step {
        Current, New, Confirm, Saved
    }

    private val savedState = ChangePasscodeModelState(savedStateHandle)

    private val _stepFlow = MutableEffectFlow<Step>()
    val stepFlow = _stepFlow.asSharedFlow()

    private val _errorFlow = MutableEffectFlow<Unit>()
    val errorFlow = _errorFlow.asSharedFlow()

    init {
        setStep(Step.Current)
    }

    fun checkCurrent(context: Context, pin: String) {
        savedState.oldPasscode = ""
        viewModelScope.launch {
            val isValid = passcodeManager.isValid(context, pin)
            if (isValid) {
                savedState.oldPasscode = pin
                setStep(Step.New)
            } else {
                setError()
            }
        }
    }

    fun setNew(pin: String) {
        savedState.passcode = pin
        setStep(Step.Confirm)
    }

    fun save(context: Context, pin: String) {
        savedState.reEnterPasscode = pin
        checkAndSave(context)
    }

    private fun checkAndSave(context: Context) {
        viewModelScope.launch {
            val oldPasscode = savedState.oldPasscode ?: return@launch
            val passcode = savedState.passcode ?: return@launch
            val reEnterPasscode = savedState.reEnterPasscode ?: return@launch
            if (passcode != reEnterPasscode) {
                setError()
                delay(400)
                setStep(Step.New)
                return@launch
            }

            val saved = passcodeManager.change(context, oldPasscode, passcode)
            if (!saved) {
                setError()
                delay(400)
                setStep(Step.Current)
                return@launch
            }

            setStep(Step.Saved)
        }
    }

    private fun setError() {
        _errorFlow.tryEmit(Unit)
    }

    private fun setStep(step: Step) {
        _stepFlow.tryEmit(step)
    }

}