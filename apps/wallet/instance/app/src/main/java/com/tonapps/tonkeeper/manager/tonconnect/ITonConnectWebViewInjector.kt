package com.tonapps.tonkeeper.manager.tonconnect

import com.tonapps.tonkeeper.ui.component.TonConnectWebView
import com.tonapps.tonkeeper.ui.screen.browser.dapp.DAppBridge

/**
 * Strategy for injecting TonConnect into WebView.
 */
interface ITonConnectWebViewInjector {
    fun inject(webView: TonConnectWebView): Boolean
}

/**
 * TonConnect injector using DAppBridge.
 */
class TonConnectWebViewInjector(
    private val bridge: DAppBridge,
) : ITonConnectWebViewInjector {

    override fun inject(webView: TonConnectWebView): Boolean {
        webView.setJsBridge(bridge)
        return true
    }
}
