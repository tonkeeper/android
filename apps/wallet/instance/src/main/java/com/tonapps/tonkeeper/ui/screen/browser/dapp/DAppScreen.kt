package com.tonapps.tonkeeper.ui.screen.browser.dapp

import android.graphics.Bitmap
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
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.tonapps.tonkeeper.fragment.tonconnect.auth.TCAuthFragment
import com.tonapps.tonkeeper.sign.SignRequestEntity
import com.tonapps.tonkeeper.ui.screen.root.RootViewModel
import com.tonapps.tonkeeperx.R
import com.tonapps.uikit.color.tabBarActiveIconColor
import com.tonapps.wallet.data.tonconnect.entities.DAppPayloadEntity
import com.tonapps.wallet.data.tonconnect.entities.DAppRequestEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.koin.androidx.viewmodel.ext.android.activityViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel
import uikit.base.BaseFragment
import uikit.drawable.HeaderDrawable
import uikit.navigation.Navigation.Companion.navigation
import uikit.widget.webview.bridge.BridgeWebView
import java.util.UUID
import kotlin.coroutines.resume

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
    private val dAppViewModel: DAppViewModel by viewModel()
    private val args: DAppArgs by lazy { DAppArgs(requireArguments()) }
    private val webViewCallback = object : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val url = request.url
            if (url.scheme != "https") {
                navigation?.openURL(url.toString(), true)
                return true
            }
            return rootViewModel.processDeepLink(url, false)
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
        webView.setOnScrollChangeListener { v, scrollX, scrollY, oldScrollX, oldScrollY ->
            headerDrawable.setDivider(scrollY > 0)
        }
        webView.jsBridge = DAppBridge(
            send = { rootViewModel.tonconnectBridgeEvent(requireContext(), args.url, it) },
            connect = { _, request -> tonConnectAuth(request) },
            restoreConnection = { dAppViewModel.restoreConnection(args.url) },
            disconnect = { dAppViewModel.disconnect(args.url) }
        )
        webView.loadUrl(args.url)

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

    private suspend fun tonConnectAuth(
        request: DAppPayloadEntity
    ): String? = suspendCancellableCoroutine { continuation ->
        val id = UUID.randomUUID().toString()
        navigation?.setFragmentResultListener(id) { bundle ->
            if (bundle.containsKey(TCAuthFragment.REPLY_ARG)) {
                continuation.resume(bundle.getString(TCAuthFragment.REPLY_ARG))
            } else {
                continuation.resume(null)
            }
        }
        openAuth(id, request)
    }

    private fun openAuth(id: String, request: DAppPayloadEntity) {
        val entity = DAppRequestEntity(
            id = id,
            r = request.toJSON().toString(),
        )
        navigation?.add(TCAuthFragment.newInstance(entity, id))
    }

    override fun onResume() {
        super.onResume()
        webView.addClientCallback(webViewCallback)
    }

    override fun onPause() {
        super.onPause()
        webView.removeClientCallback(webViewCallback)
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