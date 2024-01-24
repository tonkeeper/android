package com.tonkeeper.fragment.send.confirm

import android.net.Uri
import org.ton.api.pub.PublicKeyEd25519
import org.ton.cell.Cell
import uikit.mvi.UiEffect

sealed class ConfirmScreenEffect: UiEffect() {
    data class CloseScreen(
        val navigateToHistory: Boolean
    ): ConfirmScreenEffect()

    data class OpenSignerApp(
        val body: Cell,
        val publicKey: PublicKeyEd25519,
    ): ConfirmScreenEffect()
}