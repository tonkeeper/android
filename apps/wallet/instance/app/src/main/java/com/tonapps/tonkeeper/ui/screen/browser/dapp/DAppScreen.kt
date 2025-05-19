package com.tonapps.tonkeeper.ui.screen.browser.dapp

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.app.ShareCompat
import androidx.core.content.IntentCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.lifecycleScope
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.facebook.drawee.backends.pipeline.Fresco
import com.tonapps.tonkeeper.core.AnalyticsHelper
import com.tonapps.tonkeeper.deeplink.DeepLink
import com.tonapps.tonkeeper.deeplink.DeepLinkBuilder
import com.tonapps.tonkeeper.deeplink.DeepLinkRoute
import com.tonapps.tonkeeper.extensions.copyToClipboard
import com.tonapps.tonkeeper.extensions.loadSquare
import com.tonapps.tonkeeper.extensions.setWallet
import com.tonapps.tonkeeper.extensions.toast
import com.tonapps.tonkeeper.extensions.withUtmSource
import com.tonapps.tonkeeper.helper.BrowserHelper
import com.tonapps.tonkeeper.koin.walletViewModel
import com.tonapps.tonkeeper.popup.ActionSheet
import com.tonapps.tonkeeper.ui.base.InjectedTonConnectScreen
import com.tonapps.tonkeeper.ui.component.TonConnectWebView
import com.tonapps.tonkeeper.ui.screen.browser.share.DAppShareScreen
import com.tonapps.tonkeeper.ui.screen.root.RootActivity
import com.tonapps.tonkeeperx.R
import com.tonapps.uikit.color.tabBarActiveIconColor
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.dapps.DAppsRepository
import com.tonapps.wallet.data.dapps.entities.AppConnectEntity
import com.tonapps.wallet.data.dapps.entities.AppEntity
import com.tonapps.wallet.localization.Localization
import kotlinx.coroutines.launch
import org.koin.core.parameter.parametersOf
import uikit.drawable.HeaderDrawable
import uikit.extensions.collectFlow
import uikit.widget.webview.WebViewFixed

class DAppScreen(wallet: WalletEntity): InjectedTonConnectScreen(R.layout.fragment_dapp, wallet) {

    override val fragmentName: String = "DAppScreen"

    private lateinit var headerDrawable: HeaderDrawable
    private lateinit var headerView: View
    private lateinit var backView: View
    private lateinit var titleView: AppCompatTextView
    private lateinit var hostView: AppCompatTextView
    private lateinit var menuView: View
    private lateinit var closeView: View
    private lateinit var refreshView: SwipeRefreshLayout
    override lateinit var webView: TonConnectWebView

    private val args: DAppArgs by lazy { DAppArgs(requireArguments()) }

    private val isRequestPinShortcutSupported: Boolean by lazy {
        ShortcutManagerCompat.isRequestPinShortcutSupported(requireContext())
    }

    override val startUri: Uri
        get() = args.url

    override val viewModel: DAppViewModel by walletViewModel {
        parametersOf(args.url)
    }

    private val currentUrl: Uri
        get() = webView.url?.toUri() ?: args.url

    private val currentTitle: String
        get() = webView.title ?: args.title

    private val webViewCallback = object : WebViewFixed.Callback() {
        override fun shouldOverrideUrlLoading(request: WebResourceRequest): Boolean {
            return overrideUrlLoading(request)
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

        override fun onNewTab(url: String) {
            super.onNewTab(url)
            openNewTab(DeepLink.fixBadUrl(url))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (args.sendAnalytics) {
            AnalyticsHelper.trackEventClickDApp(
                url = args.url.toString(),
                name = args.title,
                installId = installId,
                source = args.source
            )
        }
    }

    private fun applyHost(url: String) {
        url.toUri().host?.let {
            hostView.text = it
        }
    }

    private fun openNewTab(url: String) {
        val now = System.currentTimeMillis()
        if ((now - lastDeepLinkTime) > 1000) {
            lastDeepLinkTime = now
            if (DeepLinkRoute.isAppLink(url)) {
                val deeplink = DeepLink(url.toUri(), false, null)
                when (deeplink.route) {
                    is DeepLinkRoute.DApp -> webView.loadUrl(url)
                    is DeepLinkRoute.Unknown -> BrowserHelper.open(requireActivity(), url)
                    else -> processDeeplink(deeplink, url)
                }
            } else {
                BrowserHelper.open(requireActivity(), url)
            }
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
        hostView.text = args.url.host

        menuView = view.findViewById(R.id.menu)

        closeView = view.findViewById(R.id.close)
        closeView.setOnClickListener { finish() }

        webView = view.findViewById(R.id.web_view)
        if (viewModel.isDarkTheme && WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
            WebSettingsCompat.setAlgorithmicDarkeningAllowed(webView.settings, true)
        }

        webView.setWallet(wallet)
        webView.settings.useWideViewPort = true
        webView.settings.loadWithOverviewMode = true
        webView.addCallback(webViewCallback)
        webView.jsBridge = DAppBridge(
            deviceInfo = deviceInfo.toString(),
            send = ::tonconnectSend,
            connect = ::tonconnect,
            restoreConnection = { viewModel.restoreConnection(currentUrl) },
            disconnect = { viewModel.disconnect() },
            tonapiFetch = ::tonapiFetch,
        )
        webView.loadUrl(args.url.withUtmSource())

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
        actionSheet.addItem(COPY_ID, Localization.copy_link, UIKitIcon.ic_copy_16)
        if (isRequestPinShortcutSupported) {
            actionSheet.addItem(ADD_HOME_SCREEN_ID, Localization.add_to_home_screen, UIKitIcon.ic_apps_16)
        }
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
        actionSheet.addItem(COPY_ID, Localization.copy_link, UIKitIcon.ic_copy_16)
        actionSheet.addItem(DISCONNECT_ID, Localization.disconnect, UIKitIcon.ic_disconnect_16)
        if (isRequestPinShortcutSupported) {
            actionSheet.addItem(ADD_HOME_SCREEN_ID, Localization.add_to_home_screen, UIKitIcon.ic_apps_16)
        }
        actionSheet.doOnItemClick = { actionClick(it.id) }
        actionSheet.show(view)
    }

    private fun addToHomeScreen() {
        lifecycleScope.launch {
            try {
                val app = buildAppEntity()
                val title = app.name
                val bitmap = Fresco.getImagePipeline().loadSquare(app.iconUrl.toUri(), 512) ?: throw IllegalArgumentException("Failed to load icon")

                val targetIntent = Intent(context, RootActivity::class.java).apply {
                    putExtra("dapp_deeplink", startUri.toString())
                    action = Intent.ACTION_MAIN
                }

                val info = ShortcutInfoCompat.Builder(requireContext(), args.url.host ?: "unknown")
                    .setShortLabel(title)
                    .setIntent(targetIntent)
                    .setIcon(IconCompat.createWithBitmap(bitmap))
                    .build()

                ShortcutManagerCompat.requestPinShortcut(requireContext(), info, null)
            } catch (e: Throwable) {
                navigation?.toast(Localization.unknown_error)
            }
        }
    }

    private fun actionClick(id: Long) {
        when (id) {
            REFRESH_ID -> webView.reload()
            MUTE_ID -> viewModel.mute()
            SHARE_ID -> shareLink()
            COPY_ID -> {
                analyticsSharingCopy("Copy link")
                requireContext().copyToClipboard(DeepLinkBuilder.dAppShare(currentUrl.toString()))
            }
            DISCONNECT_ID -> viewModel.disconnect()
            ADD_HOME_SCREEN_ID -> addToHomeScreen()
        }
    }

    private fun buildAppEntity(): AppEntity {
        if (args.url.host == webView.uri?.host) {
            val iconUrl = args.iconUrl.ifBlank {
                "https://www.google.com/s2/favicons?sz=256&domain=${currentUrl.host}"
            }
            val title = args.title.ifBlank { webView.title ?: "unknown" }
            return AppEntity(
                url = currentUrl,
                name = title,
                iconUrl = iconUrl,
                empty = false,
            )
        } else {
            return AppEntity(
                url = currentUrl,
                name = DAppsRepository.fixAppTitle(currentTitle),
                iconUrl = "https://www.google.com/s2/favicons?sz=256&domain=${currentUrl.host}",
                empty = true,
            )
        }
    }

    private fun analyticsSharingCopy(from: String) {
        val app = buildAppEntity()
        AnalyticsHelper.dappSharingCopy(
            installId = viewModel.installId,
            name = app.name,
            from = from,
            url = currentUrl.toString()
        )
    }

    private fun shareLink() {
        analyticsSharingCopy("Share")
        if (DeepLinkBuilder.dAppIsSpecialUrl(currentUrl)) {
            ShareCompat.IntentBuilder(requireContext())
                .setType("text/plain")
                .setChooserTitle(getString(Localization.share))
                .setText(currentUrl.toString())
                .startChooser()
        } else {
            val app = buildAppEntity()
            navigation?.add(DAppShareScreen.newInstance(wallet, app, currentUrl))
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

    companion object {

        private const val REFRESH_ID = 1L
        private const val MUTE_ID = 2L
        private const val SHARE_ID = 3L
        private const val COPY_ID = 4L
        private const val DISCONNECT_ID = 5L
        private const val ADD_HOME_SCREEN_ID = 6L

        fun newInstance(
            wallet: WalletEntity,
            title: String,
            url: Uri,
            iconUrl: String,
            source: String,
            sendAnalytics: Boolean = true,
        ): DAppScreen {
            return newInstance(wallet, DAppArgs(title, url, source, iconUrl, sendAnalytics))
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