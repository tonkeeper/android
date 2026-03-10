package com.tonapps.tonkeeper.extensions

import android.annotation.SuppressLint
import com.tonapps.wallet.data.account.entities.WalletEntity
import uikit.widget.webview.WebViewFixed

@SuppressLint("RequiresFeature")
fun WebViewFixed.setWallet(wallet: WalletEntity) {
    val walletId = wallet.id.replace("-", "")
    setProfileName(walletId)
}