package com.tonkeeper.fragment.passcode.create

import uikit.mvi.UiEffect

sealed class PasscodeCreateScreenEffect: UiEffect() {

    data object RepeatError: PasscodeCreateScreenEffect()
    data class Valid(val code: String): PasscodeCreateScreenEffect()
}