package com.tonapps.tonkeeper.ui.screen.browser.dapp

import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.marginTop
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.tonapps.tonkeeper.App
import com.tonapps.tonkeeper.ui.screen.root.RootViewModel
import com.tonapps.tonkeeperx.R
import com.tonapps.uikit.color.tabBarActiveIconColor
import org.koin.androidx.viewmodel.ext.android.activityViewModel
import uikit.base.BaseFragment
import uikit.drawable.HeaderDrawable
import uikit.extensions.applyNavBottomPadding
import uikit.extensions.getDimensionPixelSize
import uikit.widget.webview.bridge.BridgeWebView

class DAppScreen: BaseFragment(R.layout.fragment_dapp) {

    private lateinit var headerDrawable: HeaderDrawable
    private lateinit var headerView: View
    private lateinit var backView: View
    private lateinit var titleView: AppCompatTextView
    private lateinit var hostView: AppCompatTextView
    private lateinit var menuView: View
    private lateinit var closeView: View
    private lateinit var refreshView: SwipeRefreshLayout
    private lateinit var webView: BridgeWebView

    private val rootViewModel: RootViewModel by activityViewModel()

    private val args: DAppArgs by lazy { DAppArgs(requireArguments()) }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        headerDrawable = HeaderDrawable(requireContext())

        headerView = view.findViewById(R.id.header)
        headerView.background = headerDrawable

        backView = view.findViewById(R.id.back)
        backView.setOnClickListener { back() }

        titleView = view.findViewById(R.id.title)
        titleView.text = args.title

        hostView = view.findViewById(R.id.host)
        hostView.text = args.host

        menuView = view.findViewById(R.id.menu)

        closeView = view.findViewById(R.id.close)
        closeView.setOnClickListener { finish() }

        webView = view.findViewById(R.id.web_view)
        webView.clipToPadding = false
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                Log.d("DAppBridgeLog", "shouldOverrideUrlLoading: ${request.url}")
                return rootViewModel.processDeepLink(request.url, false)
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                refreshView.isRefreshing = true
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                refreshView.isRefreshing = false
            }
        }
        webView.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                Log.e("DAppBridgeLog", "onConsoleMessage: ${consoleMessage?.message()}")
                return super.onConsoleMessage(consoleMessage)
            }
        }
        webView.setOnScrollChangeListener { v, scrollX, scrollY, oldScrollX, oldScrollY ->
            headerDrawable.setDivider(scrollY > 0)
        }
        webView.loadUrl("https://ton-connect.github.io/demo-dapp-with-wallet/")
        webView.jsBridge = DAppBridge(
            sendTransaction = { request ->
                Log.d("DAppBridgeLog", "sendTransaction: $request")
                null
            },
            connect = { protocolVersion, request ->
                Log.d("DAppBridgeLog", "connect: $protocolVersion, $request")
                null
            },
            restoreConnection = {
                Log.d("DAppBridgeLog", "restoreConnection")
                null
            },
            disconnect = {
                Log.d("DAppBridgeLog", "disconnect")
            }
        )


        refreshView = view.findViewById(R.id.refresh)
        refreshView.setColorSchemeColors(requireContext().tabBarActiveIconColor)
        refreshView.setOnRefreshListener {
            webView.reload()
        }

        ViewCompat.setOnApplyWindowInsetsListener(view) { _, insets ->
            val statusInsets = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            headerView.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin = statusInsets.top
            }
            val navInsets = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            webView.updatePadding(navInsets.bottom)
            insets
        }
    }

    private fun back() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            finish()
        }
    }

    override fun onBackPressed(): Boolean {
        back()
        return false
    }

    companion object {

        fun newInstance(
            title: String? = null,
            host: String? = null,
            url: String
        ): DAppScreen {
            val fragment = DAppScreen()
            fragment.setArgs(DAppArgs(title, host, url))
            return fragment
        }
    }
}