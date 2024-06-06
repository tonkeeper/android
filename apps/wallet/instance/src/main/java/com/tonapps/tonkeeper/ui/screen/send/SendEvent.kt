package com.tonapps.tonkeeper.ui.screen.send

import org.ton.api.pub.PublicKeyEd25519
import org.ton.cell.Cell

sealed class SendEvent {
    data class Signer(val body: Cell, val publicKey: PublicKeyEd25519): SendEvent()
    data object Failed: SendEvent()
    data object Success: SendEvent()
    data object Loading: SendEvent()
}