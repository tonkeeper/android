package com.tonkeeper.fragment.send.confirm

import android.net.Uri
import uikit.mvi.UiEffect

sealed class ConfirmScreenEffect: UiEffect() {
    data class CloseScreen(
        val navigateToHistory: Boolean
    ): ConfirmScreenEffect()

    data class OpenSignerApp(
        val boc: String
    ): ConfirmScreenEffect()
}