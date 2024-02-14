package com.tonapps.tonkeeper.fragment.passcode.create

import com.tonapps.tonkeeper.PasscodeManager
import com.tonapps.tonkeeper.extensions.substringSafe
import com.tonapps.tonkeeper.fragment.passcode.create.pager.InputType
import uikit.mvi.UiState

data class PasscodeCreateScreenState(
    val code: String = "",
): UiState() {

    private companion object {
        private const val MAX_CODE_LENGTH = PasscodeManager.CODE_LENGTH * 2
    }

    val enteredPasscode: String
        get() = code.substringSafe(0, PasscodeManager.CODE_LENGTH)

    val repeatedPasscode: String
        get() = code.substringSafe(PasscodeManager.CODE_LENGTH, MAX_CODE_LENGTH)

    val activeType: InputType
        get() = if (PasscodeManager.CODE_LENGTH > enteredPasscode.length) {
            InputType.ENTER
        } else {
            InputType.REPEAT
        }

    val isPasscodeComplete: Boolean
        get() = code.length == MAX_CODE_LENGTH

    val isPasscodeValid: Boolean
        get() = enteredPasscode == repeatedPasscode

    val displayBackspace: Boolean
        get() = code.isNotEmpty()
}