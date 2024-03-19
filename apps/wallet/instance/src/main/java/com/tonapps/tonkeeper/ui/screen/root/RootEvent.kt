package com.tonapps.tonkeeper.ui.screen.root

import android.net.Uri
import com.tonapps.tonkeeper.core.tonconnect.models.TCRequest
import com.tonapps.tonkeeperx.R
import com.tonapps.wallet.data.account.WalletSource
import org.ton.api.pub.PublicKeyEd25519

sealed class RootEvent {
    data class Toast(val resId: Int): RootEvent()
    data class OpenTab(val id: Int): RootEvent() {

        constructor(link: String): this(
            id = when (link) {
                "tonkeeper://wallet" -> R.id.wallet
                "tonkeeper://activity" -> R.id.activity
                "tonkeeper://collectibles" -> R.id.collectibles
                else -> 0
            }
        )
    }

    data class Singer(
        val publicKey: PublicKeyEd25519,
        val name: String?,
        val walletSource: WalletSource
    ): RootEvent()

    data class TonConnect(
        val request: TCRequest
    ): RootEvent()

    data class Browser(
        val uri: Uri
    ): RootEvent()
}