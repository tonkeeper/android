package com.tonapps.tonkeeper.ui.screen.settings.passcode

import androidx.lifecycle.SavedStateHandle

class ChangePasscodeModelState(private val savedStateHandle: SavedStateHandle) {

    var oldPasscode: String?
        get() = savedStateHandle[OLD_PASSCODE_KEY]
        set(value) = savedStateHandle.set(OLD_PASSCODE_KEY, value)

    var passcode: String?
        get() = savedStateHandle[PASSCODE_KEY]
        set(value) = savedStateHandle.set(PASSCODE_KEY, value)

    var reEnterPasscode: String?
        get() = savedStateHandle[REENTER_PASSCODE_KEY]
        set(value) = savedStateHandle.set(REENTER_PASSCODE_KEY, value)

    var create: Boolean
        get() = savedStateHandle[CREATE_KEY] ?: false
        set(value) = savedStateHandle.set(CREATE_KEY, value)

    companion object {
        private const val OLD_PASSCODE_KEY = "old_passcode"
        private const val PASSCODE_KEY = "passcode"
        private const val REENTER_PASSCODE_KEY = "re-enter_passcode"
        private const val CREATE_KEY = "create"
    }
}
