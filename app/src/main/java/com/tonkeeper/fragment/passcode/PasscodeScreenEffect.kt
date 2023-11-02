package com.tonkeeper.fragment.passcode

import uikit.mvi.UiEffect

sealed class PasscodeScreenEffect: UiEffect() {
    data object ReEnter: PasscodeScreenEffect()

    data object Close: PasscodeScreenEffect()

    data object Default: PasscodeScreenEffect()
    data object Success: PasscodeScreenEffect()
    data object Failure: PasscodeScreenEffect()
}