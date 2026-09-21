package com.tonapps.tonkeeper.ui.screen.settings.passcode

import android.app.Application
import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.google.firebase.crashlytics.FirebaseCrashlytics
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

    private var started = false

    fun start(create: Boolean) {
        if (started) {
            return
        }
        started = true
        savedState.create = create
        setStep(firstStep())
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
            val passcode = savedState.passcode ?: return@launch
            val reEnterPasscode = savedState.reEnterPasscode ?: return@launch
            if (passcode != reEnterPasscode) {
                setError()
                delay(400)
                setStep(Step.New)
                return@launch
            }

            val saved = if (savedState.create) {
                createPasscode(passcode)
            } else {
                val oldPasscode = savedState.oldPasscode ?: return@launch
                passcodeManager.change(context, oldPasscode, passcode)
            }

            if (!saved) {
                setError()
                delay(400)
                setStep(firstStep())
                return@launch
            }

            setStep(Step.Saved)
        }
    }

    private suspend fun createPasscode(passcode: String): Boolean {
        return try {
            passcodeManager.create(passcode)
        } catch (e: Throwable) {
            FirebaseCrashlytics.getInstance().recordException(e)
            false
        }
    }

    private fun firstStep(): Step {
        return if (savedState.create) {
            Step.New
        } else {
            Step.Current
        }
    }

    private fun setError() {
        _errorFlow.tryEmit(Unit)
    }

    private fun setStep(step: Step) {
        _stepFlow.tryEmit(step)
    }
}
