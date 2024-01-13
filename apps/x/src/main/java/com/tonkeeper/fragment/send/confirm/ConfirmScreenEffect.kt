package com.tonkeeper.fragment.send.confirm

import uikit.mvi.UiEffect

sealed class ConfirmScreenEffect: UiEffect() {
    data class CloseScreen(
        val navigateToHistory: Boolean
    ): ConfirmScreenEffect()
}