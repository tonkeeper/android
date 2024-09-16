package com.tonapps.tonkeeper.ui.screen.browser.dapp

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.tonapps.tonkeeper.core.AnalyticsHelper
import com.tonapps.tonkeeper.extensions.copyToClipboard
import com.tonapps.tonkeeper.extensions.normalizeTONSites
import com.tonapps.tonkeeper.fragment.tonconnect.auth.TCAuthFragment
import com.tonapps.tonkeeper.koin.walletViewModel
import com.tonapps.tonkeeper.popup.ActionSheet
import com.tonapps.tonkeeper.ui.base.BaseWalletScreen
import com.tonapps.tonkeeper.ui.base.ScreenContext
import com.tonapps.tonkeeper.ui.screen.root.RootViewModel
import com.tonapps.tonkeeperx.R
import com.tonapps.uikit.color.tabBarActiveIconColor
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.tonconnect.entities.DConnectEntity
import com.tonapps.wallet.data.tonconnect.entities.DAppPayloadEntity
import com.tonapps.wallet.data.tonconnect.entities.DAppRequestEntity
import com.tonapps.wallet.data.tonconnect.entities.reply.DAppDeviceEntity
import com.tonapps.wallet.localization.Localization
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import org.koin.androidx.viewmodel.ext.android.activityViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.parameter.parametersOf
import uikit.drawable.HeaderDrawable
import uikit.extensions.collectFlow
import uikit.extensions.setPaddingBottom
import uikit.navigation.Navigation.Companion.navigation
import uikit.widget.webview.WebViewFixed
import uikit.widget.webview.bridge.BridgeWebView
import java.util.UUID
import kotlin.coroutines.resume

class DAppScreen(wallet: WalletEntity): BaseWalletScreen<ScreenContext.Wallet>(R.layout.fragment_dapp, ScreenContext.Wallet(wallet)) {

    private lateinit var headerDrawable: HeaderDrawable
    private lateinit var headerView: View
    private lateinit var backView: View
    private lateinit var titleView: AppCompatTextView
    private lateinit var hostView: AppCompatTextView
    private lateinit var menuView: View
    private lateinit var closeView: View
    private lateinit var refreshView: SwipeRefreshLayout
    private lateinit var webView: BridgeWebView

    private val args: DAppArgs by lazy { DAppArgs(requireArguments()) }
    private val rootViewModel: RootViewModel by activityViewModel()

    override val viewModel: DAppViewModel by walletViewModel {
        parametersOf(args.url)
    }

    private val currentUrl: String
        get() = webView.url ?: args.url

    private val webViewCallback = object : WebViewFixed.Callback() {
        override fun shouldOverrideUrlLoading(request: WebResourceRequest): Boolean {
            val refererUri = request.requestHeaders?.get("Referer")?.toUri()
            val url = request.url.normalizeTONSites()
            if (!url.toString().startsWith("https") || url.host == "t.me") {
                navigation?.openURL(url.toString())
                return true
            }
            return rootViewModel.processDeepLink(url, false, refererUri)
        }

        override fun onPageStarted(url: String, favicon: Bitmap?) {
            super.onPageStarted(url, favicon)
            refreshView.isRefreshing = true
            applyHost(url)
        }

        override fun onReceivedTitle(title: String) {
            super.onReceivedTitle(title)
            titleView.text = title
        }

        override fun onPageFinished(url: String) {
            super.onPageFinished(url)
            refreshView.isRefreshing = false
            applyHost(url)
        }

        override fun onScroll(y: Int, x: Int) {
            super.onScroll(y, x)
            headerDrawable.setDivider(y > 0)
            refreshView.isEnabled = y == 0
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AnalyticsHelper.trackEventClickDApp(args.url)
    }

    private fun applyHost(url: String) {
        url.toUri().host?.let {
            hostView.text = it
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
        if (args.title.isNullOrBlank()) {
            titleView.text = getString(Localization.loading)
        } else {
            titleView.text = args.title
        }

        hostView = view.findViewById(R.id.host)
        hostView.text = args.host

        menuView = view.findViewById(R.id.menu)
        menuView.setOnClickListener { requestMenu(it) }

        closeView = view.findViewById(R.id.close)
        closeView.setOnClickListener { finish() }

        webView = view.findViewById(R.id.web_view)
        webView.settings.useWideViewPort = true
        webView.settings.loadWithOverviewMode = true
        webView.addCallback(webViewCallback)
        webView.jsBridge = DAppBridge(
            deviceInfo = DAppDeviceEntity(sendMaxMessages = screenContext.wallet.contract.maxMessages),
            send = { rootViewModel.tonconnectBridgeEvent(requireContext(), args.url, it) },
            connect = { _, request -> tonConnectAuth(webView.url?.toUri(), request) },
            restoreConnection = { viewModel.restoreConnection() },
            disconnect = { viewModel.disconnect() }
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
            val bottomInsets = insets.getInsets(WindowInsetsCompat.Type.ime() or WindowInsetsCompat.Type.navigationBars())
            refreshView.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = bottomInsets.bottom
            }

            insets
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        webView.removeCallback(webViewCallback)
        webView.destroy()
    }

    private fun requestMenu(view: View) {
        collectFlow(viewModel.appFlow) { openMenu(view, it) }
    }

    private fun openMenu(view: View, app: DConnectEntity?) {
        val actionSheet = ActionSheet(requireContext())
        actionSheet.addItem(REFRESH_ID, Localization.refresh, UIKitIcon.ic_refresh_16)
        if (app?.enablePush == true) {
            actionSheet.addItem(MUTE_ID, Localization.mute, UIKitIcon.ic_bell_disable_16)
        }
        actionSheet.addItem(SHARE_ID, Localization.share, UIKitIcon.ic_share_16)
        actionSheet.addItem(COPY_ID, Localization.copy, UIKitIcon.ic_copy_16)
        if (app != null) {
            actionSheet.addItem(DISCONNECT_ID, Localization.disconnect, UIKitIcon.ic_disconnect_16)
        }
        actionSheet.doOnItemClick = { actionClick(it.id) }
        actionSheet.show(view)
    }

    private fun actionClick(id: Long) {
        when (id) {
            REFRESH_ID -> webView.reload()
            MUTE_ID -> viewModel.mute()
            SHARE_ID -> shareLink()
            COPY_ID -> requireContext().copyToClipboard(currentUrl)
            DISCONNECT_ID -> viewModel.disconnect()
        }
    }

    private fun shareLink() {
        val sendIntent = Intent(Intent.ACTION_SEND)
        sendIntent.putExtra(Intent.EXTRA_TEXT, currentUrl)
        sendIntent.type = "text/plain"
        val shareIntent = Intent.createChooser(sendIntent, null)
        startActivity(shareIntent)
    }

    private suspend fun tonConnectAuth(
        sourceUri: Uri?,
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
        openAuth(sourceUri, id, request)
    }

    private fun openAuth(sourceUri: Uri?, id: String, request: DAppPayloadEntity) {
        val entity = DAppRequestEntity(
            id = id,
            r = request.toJSON().toString(),
            source = sourceUri,
        )
        navigation?.add(TCAuthFragment.newInstance(entity, id, true))
    }

    override fun onResume() {
        super.onResume()
        webView.addCallback(webViewCallback)
    }

    override fun onPause() {
        super.onPause()
        webView.removeCallback(webViewCallback)
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

        private const val REFRESH_ID = 1L
        private const val MUTE_ID = 2L
        private const val SHARE_ID = 3L
        private const val COPY_ID = 4L
        private const val DISCONNECT_ID = 5L

        fun newInstance(
            wallet: WalletEntity,
            title: String? = null,
            host: String? = null,
            url: String
        ): DAppScreen {
            val mustHost = if (host.isNullOrBlank()) {
                Uri.parse(url).host
            } else {
                host
            }
            return newInstance(wallet, DAppArgs(title, mustHost, Uri.parse(url)))
        }

        fun newInstance(
            wallet: WalletEntity,
            args: DAppArgs,
        ): DAppScreen {
            val fragment = DAppScreen(wallet)
            fragment.setArgs(args)
            return fragment
        }
    }
}