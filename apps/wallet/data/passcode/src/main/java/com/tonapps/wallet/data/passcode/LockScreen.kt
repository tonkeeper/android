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

        // Passed, but first the wallets get the chains they miss (the new networks screen). The lock
        // screen goes once the user leaves that screen.
        data object NewChains: State()

        // The passcode was right and the old-store wallets are being moved to the multichain one:
        // the lock screen shows a loader in place of the passcode.
        data object Migrating: State()
    }

    private val _stateFlow = MutableStateFlow<State?>(null)
    val stateFlow = _stateFlow.asStateFlow().filterNotNull()

    private val _hiddenFlow = MutableStateFlow(false)
    val hiddenFlow = _hiddenFlow.asStateFlow()

    // The lock screen is up and nothing has opened the wallet yet.
    val waitsForPasscode: Boolean
        get() {
            val state = _stateFlow.value
            return state == State.Input || state == State.Biometric || state is State.Error
        }

    suspend fun init() {
        if (!passcodeManager.hasPinCode()) {
            passed(passcodeManager.startNewChainsWithoutPin())
        } else if (passcodeManager.restoreSessionWithoutPasscode()) {
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

    suspend fun retryWithoutPasscode(): Boolean {
        if (!waitsForPasscode) {
            return false
        }

        if (!passcodeManager.restoreSessionWithoutPasscode()) {
            return false
        }

        hide()
        return true
    }

    fun hidden() {
        _hiddenFlow.value = true
    }

    private fun error() {
        _stateFlow.value = State.Error()
    }

    fun migrating() {
        _stateFlow.value = State.Migrating
    }

    suspend fun check(
        context: Context,
        code: String,
        migrate: (suspend () -> Unit)? = null,
        onValid: () -> Unit = {},
    ) = withContext(Dispatchers.IO) {
        if (passcodeManager.isValid(context, code)) {
            if (migrate != null) {
                migrating()
                migrate()
            }
            onValid()
            passed()
        } else {
            error()
        }
    }

    fun biometric() {
        passed()
    }

    private fun passed(hasNewChains: Boolean = passcodeManager.hasNewChains) {
        if (hasNewChains) {
            _stateFlow.value = State.NewChains
        } else {
            hide()
        }
    }

    fun newChainsDone() {
        hide()
    }

    fun reset() {
    }

}