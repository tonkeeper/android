package com.tonapps.tonkeeper.ui.screen.browser.dapp.keeper

import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.tonkeeper.koin.walletViewModel
import com.tonapps.tonkeeper.ui.component.TonConnectWebView
import com.tonapps.tonkeeper.ui.screen.browser.dapp.inapp.InAppDAppScreen
import com.tonapps.wallet.api.API
import org.koin.android.ext.android.inject

internal class KeeperDAppScreen(wallet: WalletEntity) : InAppDAppScreen(wallet) {

    override val fragmentName: String = "KeeperDAppScreen"

    override val drawsOwnPagesUnderSystemBars: Boolean = true

    override val isTonConnectInjectionEnabled: Boolean = false

    override val dappBridges: KeeperDAppViewModel by walletViewModel()

    override val originRules: Set<String> = keeperDAppOriginRules

    override val showsCloseOverOwnPages: Boolean = true

    private val api: API by inject()

    override fun isOwnPage(uri: Uri): Boolean = uri.isKeeperDApp()

    override fun onWebViewCreated(webView: TonConnectWebView) {
        super.onWebViewCreated(webView)
        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            WebViewCompat.removeWebMessageListener(webView, KEEPER_BRIDGE_CHANNEL)
        }
        applyUserAgent(webView)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        refreshView.setOnChildScrollUpCallback { _, _ -> headerHidden || webView.canScrollVertically(-1) }
    }

    private fun applyUserAgent(webView: TonConnectWebView) {
        val agent = api.userAgent
        val current = webView.settings.userAgentString.orEmpty()
        if (!current.contains(agent)) {
            webView.settings.userAgentString = "$current $agent".trim()
        }
    }
}
