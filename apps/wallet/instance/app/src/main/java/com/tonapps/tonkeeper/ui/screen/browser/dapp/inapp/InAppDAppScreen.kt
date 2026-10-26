package com.tonapps.tonkeeper.ui.screen.browser.dapp.inapp

import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.core.net.toUri
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.lifecycleScope
import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.log.L
import com.tonapps.tonkeeper.extensions.isSameOriginOrSubdomain
import com.tonapps.tonkeeper.ui.component.TonConnectWebView
import com.tonapps.tonkeeper.ui.screen.browser.dapp.DAppScreen
import com.tonapps.tonkeeper.ui.screen.browser.dapp.inapp.InAppBridgeContract.Bridges
import com.tonapps.tonkeeper.ui.screen.browser.dapp.inapp.InAppBridgeContract.Effect
import com.tonapps.tonkeeper.ui.screen.browser.dapp.inapp.bridge.InAppBridgeConnection
import com.tonapps.tonkeeper.ui.screen.browser.dapp.inapp.transport.InAppTransport
import com.tonapps.tonkeeper.ui.screen.browser.dapp.inapp.transport.WebMessageTransport
import kotlinx.coroutines.launch
import uikit.extensions.collectFlow
import uikit.widget.webview.WebViewFixed

/**
 * A dApp built into the app: its own pages show without the browser header, and its bridges are
 * attached before the first page loads. A subclass says which view model holds the bridges and
 * which origins may talk to them.
 */
internal abstract class InAppDAppScreen(wallet: WalletEntity) : DAppScreen(wallet) {

    protected abstract val dappBridges: Bridges

    /** Where the bridges' scripts and listeners apply. Empty turns the bridges off. */
    protected abstract val originRules: Set<String>

    private var statusBarHeight = 0

    private var headerHidden = true

    private val connections = mutableListOf<InAppBridgeConnection<*, *>>()

    private val webViewCallback = object : WebViewFixed.Callback() {
        override fun onPageStarted(url: String, favicon: Bitmap?) {
            super.onPageStarted(url, favicon)
            applyHeaderVisibility(url)
        }

        override fun onPageFinished(url: String) {
            super.onPageFinished(url)
            applyHeaderVisibility(webView.url ?: url)
        }
    }

    /** The dApp's own pages, shown without the browser header. */
    protected open fun isOwnPage(uri: Uri): Boolean = uri.isSameOriginOrSubdomain(startUri)

    protected open fun createTransport(webView: WebView, originRules: Set<String>): InAppTransport {
        return WebMessageTransport(webView, originRules)
    }

    protected open fun onEffect(effect: Effect) {
        when (effect) {
            is Effect.Close -> finish()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        headerHidden = true
        headerView.isVisible = false
        webView.addCallback(webViewCallback)
        viewLifecycleOwner.collectFlow(dappBridges.effects) { onEffect(it) }
    }

    override fun loadInitialPage(webView: TonConnectWebView, restoredState: Bundle?) {
        viewLifecycleOwner.lifecycleScope.launch {
            attachBridges(webView)
            super.loadInitialPage(webView, restoredState)
        }
    }

    override fun onDestroyView() {
        webView.removeCallback(webViewCallback)
        // One failing bridge must not leave the others attached.
        connections.forEach { connection ->
            runCatching { connection.detach() }.onFailure { L.e(it) }
        }
        connections.clear()
        super.onDestroyView()
    }

    override fun applyStatusBarInset(top: Int) {
        statusBarHeight = top
        applyTopInsets()
    }

    private suspend fun attachBridges(webView: TonConnectWebView) {
        val rules = originRules
        if (rules.isEmpty()) {
            L.w("Bridges are off: no origin rules for $fragmentName")
            return
        }
        for (bridge in dappBridges.bridges) {
            val connection = bridge.connection(createTransport(webView, rules))
            connections += connection
            if (!connection.attach(viewLifecycleOwner)) {
                L.w("${bridge.name} could not be attached to this WebView")
            }
        }
    }

    private fun applyHeaderVisibility(url: String) {
        val hidden = isOwnPage(url.toUri())
        if (headerHidden == hidden) {
            return
        }
        headerHidden = hidden
        headerView.isVisible = !hidden
        applyTopInsets()
    }

    private fun applyTopInsets() {
        headerView.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            topMargin = statusBarHeight
        }
        refreshView.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            if (headerHidden) {
                topMargin = statusBarHeight
            } else {
                topMargin = 0
            }
        }
    }
}