package com.tonapps.tonkeeper.manager.walletkit

import com.tonapps.tonkeeper.manager.tonconnect.ITonConnectWebViewInjector
import com.tonapps.tonkeeper.ui.component.TonConnectWebView
import com.tonapps.blockchain.model.legacy.WalletEntity
import io.ton.walletkit.extensions.injectTonConnect

/**
 * WalletKit TonConnect injector using the WalletKit SDK.
 */
class WalletKitWebViewInjector(
    private val walletKitBridge: WalletKitTonConnect,
    private val wallet: WalletEntity? = null,
) : ITonConnectWebViewInjector {

    override fun inject(webView: TonConnectWebView): Boolean {
        val instance = walletKitBridge.getWalletKitInstance() ?: return false
        val walletId = wallet?.id
        webView.injectTonConnect(instance, walletId)
        return true
    }
}
