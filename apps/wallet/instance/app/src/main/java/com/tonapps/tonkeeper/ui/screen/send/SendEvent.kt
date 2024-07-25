package com.tonapps.tonkeeper.ui.screen.send

import com.tonapps.icu.Coins
import com.tonapps.ledger.ton.Transaction
import org.ton.api.pub.PublicKeyEd25519
import org.ton.cell.Cell

sealed class SendEvent {
    data class Signer(val body: Cell, val publicKey: PublicKeyEd25519): SendEvent()
    data class Ledger(val transaction: Transaction, val walletId: String): SendEvent()
    data object Failed: SendEvent()
    data object Success: SendEvent()
    data object Loading: SendEvent()
    data object InsufficientBalance: SendEvent()
    data object Confirm: SendEvent()
    data class Fee(
        val value: Coins,
        val format: CharSequence,
        val convertedFormat: CharSequence
    ): SendEvent()
}