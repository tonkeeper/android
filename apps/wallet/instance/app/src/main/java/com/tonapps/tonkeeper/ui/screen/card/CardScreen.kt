package com.tonapps.tonkeeper.ui.screen.card

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.tonapps.extensions.appVersionName
import com.tonapps.extensions.bestMessage
import com.tonapps.extensions.toUriOrNull
import com.tonapps.tonkeeper.koin.walletViewModel
import com.tonapps.tonkeeper.manager.tonconnect.ConnectRequest
import com.tonapps.tonkeeper.manager.tonconnect.TonConnect
import com.tonapps.tonkeeper.manager.tonconnect.TonConnectManager
import com.tonapps.tonkeeper.manager.tonconnect.bridge.BridgeException
import com.tonapps.tonkeeper.manager.tonconnect.bridge.JsonBuilder
import com.tonapps.tonkeeper.manager.tonconnect.bridge.model.BridgeError
import com.tonapps.tonkeeper.manager.tonconnect.bridge.model.BridgeEvent
import com.tonapps.tonkeeper.manager.tonconnect.bridge.model.BridgeMethod
import com.tonapps.tonkeeper.ui.base.InjectedTonConnectScreen
import com.tonapps.tonkeeper.ui.base.WalletContextScreen
import com.tonapps.tonkeeper.ui.component.TonConnectWebView
import com.tonapps.tonkeeper.ui.screen.send.transaction.SendTransactionScreen
import com.tonapps.tonkeeperx.R
import com.tonapps.wallet.api.API
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.core.entity.SignRequestEntity
import org.json.JSONArray
import org.json.JSONObject
import org.koin.android.ext.android.inject
import uikit.base.BaseFragment
import uikit.extensions.activity
import uikit.widget.webview.WebViewFixed
import java.util.concurrent.CancellationException

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
        webView.jsBridge = CardBridge(
            deviceInfo = deviceInfo.toString(),
            send = ::tonconnectSend,
            connect = ::tonconnect,
            restoreConnection = { viewModel.restoreConnection(webView.url?.toUriOrNull()) },
            disconnect = { viewModel.disconnect() },
            tonapiFetch = ::tonapiFetch,
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