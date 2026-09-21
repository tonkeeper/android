package com.tonapps.tonkeeper.extensions

import android.annotation.SuppressLint
import com.tonapps.blockchain.model.legacy.WalletEntity
import uikit.widget.webview.WebViewFixed

fun WalletEntity.webViewProfileName(): String = id.replace("-", "")

@SuppressLint("RequiresFeature")
fun WebViewFixed.setWallet(wallet: WalletEntity) {
    setProfileName(wallet.webViewProfileName())
}
