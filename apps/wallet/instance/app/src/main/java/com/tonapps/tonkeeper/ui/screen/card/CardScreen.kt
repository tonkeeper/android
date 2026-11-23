package com.tonapps.tonkeeper.ui.screen.card

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import com.tonapps.tonkeeper.koin.walletViewModel
import com.tonapps.tonkeeper.ui.base.InjectedTonConnectScreen
import com.tonapps.tonkeeper.ui.component.TonConnectWebView
import com.tonapps.tonkeeperx.R
import com.tonapps.blockchain.model.legacy.WalletEntity
import uikit.base.BaseFragment
import uikit.widget.webview.WebViewFixed

class CardScreen(wallet: WalletEntity): InjectedTonConnectScreen(R.layout.fragment_card, wallet), BaseFragment.SwipeBack {

    override val fragmentName: String = "CardScreen"

    override val viewModel: CardViewModel by walletViewModel()

    override lateinit var webView: TonConnectWebView

    override val startUri: Uri
        get() = viewModel.url

    private val webViewCallback = object : WebViewFixed.Callback() {
        override fun shouldOverrideUrlLoading(request: WebResourceRequest): Boolean {
            return overrideUrlLoading(request)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        webView = view.findViewById(R.id.webView)
        webView.settings.useWideViewPort = true
        webView.settings.loadWithOverviewMode = true
        webView.addCallback(webViewCallback)
        webView.setJsBridge(
            CardBridge(
                deviceInfo = deviceInfo.toString(),
                send = ::tonconnectSend,
                connect = ::tonconnect,
                restoreConnection = { viewModel.restoreConnection(uri) },
                disconnect = { viewModel.disconnect(uri) },
                tonapiFetch = ::tonapiFetch,
            )
        )
        webView.loadUrl(viewModel.url.toString())

        ViewCompat.setOnApplyWindowInsetsListener(view) { _, insets ->
            val statusInsets = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            val bottomInsets = insets.getInsets(WindowInsetsCompat.Type.ime() or WindowInsetsCompat.Type.navigationBars())
            applyWebViewOffset(statusInsets.top, bottomInsets.bottom)
            insets
        }
    }

    override fun onResume() {
        super.onResume()
        webView.addCallback(webViewCallback)
    }

    override fun onPause() {
        super.onPause()
        webView.removeCallback(webViewCallback)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        webView.removeCallback(webViewCallback)
        webView.destroy()
    }

    private fun applyWebViewOffset(top: Int, bottom: Int) {
        webView.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            topMargin = top
            bottomMargin = bottom
        }
    }

    companion object {

        fun newInstance(wallet: WalletEntity) = CardScreen(wallet)
    }
}