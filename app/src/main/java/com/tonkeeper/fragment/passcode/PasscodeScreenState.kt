package com.tonkeeper.fragment.passcode

import uikit.mvi.UiState

data class PasscodeScreenState(
    val numbers: List<Int> = emptyList(),
    val backspace: Boolean = false,
    val error: Boolean = false
): UiState()