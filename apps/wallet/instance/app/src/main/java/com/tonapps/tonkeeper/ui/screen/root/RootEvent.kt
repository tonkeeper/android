package com.tonapps.tonkeeper.ui.screen.root

import android.net.Uri
import com.tonapps.tonkeeper.core.history.list.item.HistoryItem
import com.tonapps.wallet.data.tonconnect.entities.DAppRequestEntity
import org.ton.api.pub.PublicKeyEd25519

sealed class RootEvent {
    data class Toast(val resId: Int): RootEvent()
    data class OpenTab(val link: String): RootEvent()
    data class Swap(
        val uri: Uri,
        val address: String,
        val from: String,
        val to: String?
    ): RootEvent()

    data object BuyOrSell: RootEvent()

    data class BuyOrSellDirect(
        val name: String
    ): RootEvent()

    data class Singer(
        val publicKey: PublicKeyEd25519,
        val name: String?,
        val qr: Boolean
    ): RootEvent()

    data class TonConnect(
        val request: DAppRequestEntity
    ): RootEvent()

    data class Browser(
        val uri: Uri
    ): RootEvent()

    data class Transfer(
        val address: String,
        val amount: String?,
        val text: String?,
        val jettonAddress: String?
    ): RootEvent()

    data class Transaction(
        val event: HistoryItem.Event
    ): RootEvent()
}