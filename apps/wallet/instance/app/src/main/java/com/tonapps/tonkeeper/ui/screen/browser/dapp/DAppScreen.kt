package com.tonapps.tonkeeper.ui.screen.browser.dapp

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.tonapps.extensions.appVersionName
import com.tonapps.tonkeeper.core.AnalyticsHelper
import com.tonapps.tonkeeper.deeplink.DeepLink
import com.tonapps.tonkeeper.deeplink.DeepLinkRoute
import com.tonapps.tonkeeper.extensions.copyToClipboard
import com.tonapps.tonkeeper.extensions.normalizeTONSites
import com.tonapps.tonkeeper.helper.BrowserHelper
import com.tonapps.tonkeeper.koin.walletViewModel
import com.tonapps.tonkeeper.manager.tonconnect.ConnectRequest
import com.tonapps.tonkeeper.manager.tonconnect.TonConnect
import com.tonapps.tonkeeper.manager.tonconnect.TonConnectManager
import com.tonapps.tonkeeper.manager.tonconnect.bridge.JsonBuilder
import com.tonapps.tonkeeper.manager.tonconnect.bridge.model.BridgeError
import com.tonapps.tonkeeper.manager.tonconnect.bridge.model.BridgeEvent
import com.tonapps.tonkeeper.manager.tonconnect.bridge.model.BridgeMethod
import com.tonapps.tonkeeper.popup.ActionSheet
import com.tonapps.tonkeeper.ui.base.WalletContextScreen
import com.tonapps.tonkeeper.ui.screen.root.RootViewModel
import com.tonapps.tonkeeper.ui.screen.send.transaction.SendTransactionScreen
import com.tonapps.tonkeeper.ui.screen.tonconnect.TonConnectScreen
import com.tonapps.tonkeeperx.R
import com.tonapps.uikit.color.tabBarActiveIconColor
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.api.API
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.core.entity.SignRequestEntity
import com.tonapps.wallet.data.dapps.entities.AppConnectEntity
import com.tonapps.wallet.localization.Localization
import org.json.JSONArray
import org.json.JSONObject
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.activityViewModel
import org.koin.core.parameter.parametersOf
import uikit.drawable.HeaderDrawable
import uikit.extensions.activity
import uikit.extensions.collectFlow
import uikit.widget.webview.WebViewFixed
import uikit.widget.webview.bridge.BridgeWebView
import java.util.concurrent.CancellationException

class DAppScreen(wallet: WalletEntity): WalletContextScreen(R.layout.fragment_dapp, wallet) {

    private val api: API by inject()
    private val tonConnectManager: TonConnectManager by inject()

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

    private val currentUrl: Uri
        get() = webView.url?.toUri() ?: args.url

    private val webViewCallback = object : WebViewFixed.Callback() {
        override fun shouldOverrideUrlLoading(request: WebResourceRequest): Boolean {
            val refererUri = request.requestHeaders?.get("Referer")?.toUri()
            val url = request.url.normalizeTONSites()
            val scheme = url.scheme ?: ""
            if (scheme == "https") {
                return false
            }
            val deeplink = DeepLink(url, false, refererUri)
            if (deeplink.route is DeepLinkRoute.TonConnect) {
                rootViewModel.processTonConnectDeepLink(deeplink, null)
            } else if (deeplink.route is DeepLinkRoute.Unknown) {
                navigation?.openURL(url.toString())
            }
            return true
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
        AnalyticsHelper.trackEventClickDApp(args.url.toString())
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
        hostView.text = args.url.host

        menuView = view.findViewById(R.id.menu)

        closeView = view.findViewById(R.id.close)
        closeView.setOnClickListener { finish() }

        webView = view.findViewById(R.id.web_view)
        webView.settings.useWideViewPort = true
        webView.settings.loadWithOverviewMode = true
        webView.addCallback(webViewCallback)
        webView.jsBridge = DAppBridge(
            deviceInfo = JsonBuilder.device(wallet.maxMessages, requireContext().appVersionName).toString(),
            send = ::send,
            connect = ::tonconnect,
            restoreConnection = viewModel::restoreConnection,
            disconnect = { viewModel.disconnect() },
            tonapiFetch = api::tonapiFetch,
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

        collectFlow(viewModel.connectionFlow) { connection ->
            if (connection == null) {
                setDefaultState()
            } else {
                setConnectionState(connection)
            }
        }
    }

    private suspend fun send(array: JSONArray): JSONObject {
        val messages = BridgeEvent.Message.parse(array)
        if (messages.size == 1) {
            val message = messages.first()
            val id = message.id
            if (message.method != BridgeMethod.SEND_TRANSACTION) {
                return JsonBuilder.responseError(id, BridgeError.METHOD_NOT_SUPPORTED)
            }
            val signRequests = message.params.map { SignRequestEntity(it) }
            if (signRequests.size != 1) {
                return JsonBuilder.responseError(id, BridgeError.BAD_REQUEST)
            }
            val signRequest = signRequests.first()
            return try {
                val boc = SendTransactionScreen.run(requireContext(), wallet, signRequest)
                JsonBuilder.responseSendTransaction(id, boc)
            } catch (e: CancellationException) {
                JsonBuilder.responseError(id, BridgeError.USER_DECLINED_TRANSACTION)
            } catch (e: BridgeError.Exception) {
                JsonBuilder.responseError(id, e.error)
            } catch (e: Throwable) {
                JsonBuilder.responseError(id, BridgeError.UNKNOWN)
            }
        } else {
            return JsonBuilder.responseError(0, BridgeError.BAD_REQUEST)
        }
    }

    private fun setDefaultState() {
        menuView.setOnClickListener { openDefaultMenu(it) }
    }

    private fun setConnectionState(connection: AppConnectEntity) {
        menuView.setOnClickListener { openConnectionMenu(it, connection) }
    }

    private fun openDefaultMenu(view: View) {
        val actionSheet = ActionSheet(requireContext())
        actionSheet.addItem(REFRESH_ID, Localization.refresh, UIKitIcon.ic_refresh_16)
        actionSheet.addItem(SHARE_ID, Localization.share, UIKitIcon.ic_share_16)
        actionSheet.addItem(COPY_ID, Localization.copy, UIKitIcon.ic_copy_16)
        actionSheet.doOnItemClick = { actionClick(it.id) }
        actionSheet.show(view)
    }

    private fun openConnectionMenu(view: View, connection: AppConnectEntity) {
        val actionSheet = ActionSheet(requireContext())
        actionSheet.addItem(REFRESH_ID, Localization.refresh, UIKitIcon.ic_refresh_16)
        if (connection.pushEnabled) {
            actionSheet.addItem(MUTE_ID, Localization.mute, UIKitIcon.ic_bell_disable_16)
        }
        actionSheet.addItem(SHARE_ID, Localization.share, UIKitIcon.ic_share_16)
        actionSheet.addItem(COPY_ID, Localization.copy, UIKitIcon.ic_copy_16)
        actionSheet.addItem(DISCONNECT_ID, Localization.disconnect, UIKitIcon.ic_disconnect_16)
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

    private suspend fun tonconnect(
        version: Int,
        request: ConnectRequest
    ): JSONObject {
        if (version != 2) {
            return JsonBuilder.connectEventError(BridgeError.BAD_REQUEST)
        }
        val activity = requireContext().activity ?: return JsonBuilder.connectEventError(BridgeError.BAD_REQUEST)
        return tonConnectManager.launchConnectFlow(
            activity = activity,
            tonConnect = TonConnect.fromJsInject(request, webView.url?.toUri()),
            wallet = wallet
        )
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

    override fun onDestroyView() {
        super.onDestroyView()
        webView.removeCallback(webViewCallback)
        webView.destroy()
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
            url: Uri
        ): DAppScreen {
            return newInstance(wallet, DAppArgs(title, url))
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