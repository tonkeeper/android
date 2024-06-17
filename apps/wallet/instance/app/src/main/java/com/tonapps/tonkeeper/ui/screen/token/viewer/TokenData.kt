package com.tonapps.tonkeeper.ui.screen.token.viewer

import android.net.Uri
import com.tonapps.icu.Coins
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.token.entities.AccountTokenEntity

data class TokenData(
    val token: AccountTokenEntity,
    val wallet: WalletEntity,
) {

    val isTon: Boolean
        get() = token.isTon

    val symbol: String
        get() = token.symbol

    val address: String
        get() = token.address

    val walletAddress: String
        get() = wallet.address

    val verified: Boolean
        get() = token.verified

    val detailsUrl = if (isTon) {
        Uri.parse("https://tonviewer.com/${wallet.address}")
    } else {
        Uri.parse("https://tonviewer.com/${wallet.address}/jetton/${token.address}")
    }

    val iconUri: Uri
        get() = token.imageUri

    val balance: Coins
        get() = token.balance.value

    val fiat: Coins
        get() = token.fiat
}