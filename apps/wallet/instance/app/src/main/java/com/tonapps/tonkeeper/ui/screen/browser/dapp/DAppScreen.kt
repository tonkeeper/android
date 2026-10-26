package com.tonapps.tonkeeper.ui.screen.browser.dapp

import android.Manifest
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.app.ShareCompat
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
import com.tonapps.bus.core.contract.UtmParameters
import com.tonapps.tonkeeper.extensions.copyToClipboard
import com.tonapps.tonkeeper.extensions.setWallet
import com.tonapps.tonkeeper.extensions.toast
import com.tonapps.tonkeeper.extensions.webViewProfileName
import com.tonapps.tonkeeper.extensions.withUtmSource
import com.tonapps.bus.generated.Events.DappBrowser.DappSharingCopyFrom
import com.tonapps.tonkeeper.helper.BrowserHelper
import com.tonapps.tonkeeper.koin.environment
import com.tonapps.tonkeeper.koin.walletViewModel
import com.tonapps.tonkeeper.manager.tonconnect.bridge.JsonBuilder
import com.tonapps.tonkeeper.popup.ActionSheet
import com.tonapps.tonkeeper.ui.base.InjectedTonConnectScreen
import com.tonapps.tonkeeper.ui.component.TonConnectWebView
import com.tonapps.tonkeeper.ui.screen.browser.analytics.DappBrowserAnalytics
import com.tonapps.tonkeeper.ui.screen.browser.analytics.DappOpenAnalytics
import com.tonapps.tonkeeper.ui.screen.browser.dapp.bridges.DAppBridge
import com.tonapps.tonkeeper.ui.screen.browser.share.DAppShareScreen
import com.tonapps.tonkeeper.ui.screen.root.ShortcutDeeplinkActivity
import com.tonapps.tonkeeperx.R
import com.tonapps.tonkeeper.ui.screen.browser.dapp.earn.EarnDAppScreen
import com.tonapps.uikit.color.tabBarActiveIconColor
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.core.deeplink.DeepLink
import com.tonapps.core.deeplink.DeepLinkBuilder
import com.tonapps.core.deeplink.DeepLinkRoute
import com.tonapps.wallet.data.dapps.DAppsRepository
import com.tonapps.wallet.data.dapps.entities.AppConnectEntity
import com.tonapps.wallet.data.dapps.entities.AppEntity
import com.tonapps.wallet.localization.Localization
import java.io.ByteArrayInputStream
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.core.parameter.parametersOf
import uikit.drawable.HeaderDrawable
import uikit.extensions.collectFlow
import uikit.widget.AsyncImageView
import uikit.widget.webview.WebViewFixed
import uikit.extensions.setMoonOnClickListener

open class DAppScreen(wallet: WalletEntity) : InjectedTonConnectScreen(R.layout.fragment_dapp, wallet) {

    override val fragmentName: String = "DAppScreen"

    private val dAppsRepository: DAppsRepository by inject()

    private lateinit var headerDrawable: HeaderDrawable
    protected lateinit var headerView: View
    private lateinit var backView: View
    private lateinit var titleView: AppCompatTextView
    private lateinit var hostView: AppCompatTextView
    private lateinit var menuView: View
    private lateinit var closeView: View
    protected lateinit var refreshView: SwipeRefreshLayout
    override lateinit var webView: TonConnectWebView

    private val args: DAppArgs by lazy { DAppArgs(requireArguments()) }

    // A restored screen is not a new dapp open: reporting only the loaded half of the pair would
    // break the dapp_app_loaded / dapp_app_click success rate.
    private var trackDappOpen = false

    private var cleanupAttempted = false

    private var hadConnection = false

    protected val dappAnalytics: DappOpenAnalytics by lazy {
        args.analytics ?: DappBrowserAnalytics.directContext(
            source = args.source,
            url = args.url,
            country = requireContext().environment?.deviceCountry,
            utm = args.utm,
        )
    }

    private val isRequestPinShortcutSupported: Boolean by lazy {
        ShortcutManagerCompat.isRequestPinShortcutSupported(requireContext())
    }

    override val startUri: Uri
        get() = args.url

    override val isDAppBrowser: Boolean
        get() = true

    protected open val isTonConnectInjectionEnabled: Boolean = true

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

        override fun shouldInterceptRequest(request: WebResourceRequest): WebResourceResponse? {
            val uri = request.url

            if (uri.scheme == "tg") {
                webView.post {
                    if (consumeDeepLinkThrottle()) {
                        BrowserHelper.openTG(requireContext(), uri)
                    }
                }
                return WebResourceResponse(
                    "text/plain",
                    "utf-8",
                    ByteArrayInputStream(ByteArray(0))
                )
            }
            return null
        }

        override fun onPageStarted(url: String, favicon: Bitmap?) {
            super.onPageStarted(url, favicon)
            refreshView.isRefreshing = true
            hostView.text = getString(Localization.loading)
        }

        override fun onReceivedTitle(title: String) {
            super.onReceivedTitle(title)
            titleView.text = title
        }

        override fun onPageFinished(url: String) {
            super.onPageFinished(url)
            refreshView.isRefreshing = false
            applyHost(webView.url ?: url)
            if (trackDappOpen) {
                dappAnalytics.loaded()
            }
            purgeOriginStorage(webView.url ?: url)
        }

        override fun onReceivedHttpError(request: WebResourceRequest?, errorResponse: WebResourceResponse?) {
            super.onReceivedHttpError(request, errorResponse)
            val status = errorResponse?.statusCode ?: return
            if (trackDappOpen && request?.isForMainFrame == true && status >= HTTP_ERROR_STATUS) {
                dappAnalytics.loadFailed(LOAD_ERROR_HTTP, status)
            }
        }

        override fun onReceivedError(request: WebResourceRequest?, error: WebResourceError?) {
            super.onReceivedError(request, error)
            if (trackDappOpen && request?.isForMainFrame == true && error != null) {
                dappAnalytics.loadFailed("$LOAD_ERROR_WEBVIEW:${error.errorCode}", null)
            }
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

        override fun openFilePicker(fileChooserParams: WebChromeClient.FileChooserParams) {
            super.openFilePicker(fileChooserParams)
            startActivityForResult(fileChooserParams.createIntent(), REQUEST_CODE_FILE)
        }

        override fun onPermissionRequest(request: PermissionRequest) {
            super.onPermissionRequest(request)
            val resources = request.resources
            val androidPermissions = mutableListOf<String>()
            resources.forEach { resource ->
                if (resource == PermissionRequest.RESOURCE_VIDEO_CAPTURE) {
                    androidPermissions.add(Manifest.permission.CAMERA)
                } else if (resource == PermissionRequest.RESOURCE_AUDIO_CAPTURE) {
                    androidPermissions.add(Manifest.permission.RECORD_AUDIO)
                }
            }
            if (hasPermissions(androidPermissions.toTypedArray())) {
                request.grant(resources)
            } else if (androidPermissions.isNotEmpty()) {
                requestPermissions(androidPermissions.toTypedArray(), REQUEST_CODE_PERMISSIONS)
            }
        }
    }

    private val isForceConnect: Boolean
        get() = args.forceConnect && webView.url?.toUri()?.host == args.url.host

    private val bridge: DAppBridge by lazy {
        DAppBridge(
            deviceInfo = deviceInfo.toString(),
            send = { tonconnectSend(it, showLogout = !isForceConnect) },
            connect = { protocolVersion, request ->
                tonconnect(protocolVersion, request, forceConnect = isForceConnect)
            },
            restoreConnection = { viewModel.restoreConnection(currentUrl) },
            disconnect = { viewModel.disconnect() },
            tonapiFetch = ::tonapiFetch,
        )
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_FILE) {
            val uris = data?.data?.let { arrayOf(it) } ?: emptyArray()
            webView.setFilePickerResult(uris)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String?>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            val granted =
                grantResults.all { it == android.content.pm.PackageManager.PERMISSION_GRANTED }
            if (granted) {
                webView.reload()
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (view == null) {
            return
        }
        val state = Bundle()
        if (webView.saveState(state) != null) {
            outState.putBundle(STATE_WEB_VIEW, state)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            trackDappOpen = true
            dappAnalytics.click()
        }
    }

    protected open fun onCloseClick() {
        finish()
    }

    private fun applyHost(url: String) {
        hostView.text = url.toUri().host ?: "unknown"
    }

    private fun purgeOriginStorage(url: String) {
        if (cleanupAttempted) {
            return
        }
        val host = url.toUri().host?.lowercase() ?: return
        if (host != args.url.host?.lowercase()) {
            return
        }
        val profileName = wallet.webViewProfileName()
        if (!dAppsRepository.hasOriginCleanup(profileName, host)) {
            return
        }
        cleanupAttempted = true
        webView.evaluateJavascript(PURGE_ORIGIN_STORAGE_JS) { result ->
            if (result?.removeSurrounding("\"") == "ok") {
                dAppsRepository.consumeOriginCleanup(profileName, host)
                webView.post { webView.reload() }
            } else {
                // No reload was issued, so re-arming cannot loop: only a real navigation retries.
                cleanupAttempted = false
            }
        }
    }

    private fun openNewTab(url: String) {
        if (consumeDeepLinkThrottle()) {
            val uri = url.toUri()
            if (DeepLinkRoute.isAppLink(url)) {
                val deeplink = DeepLink(uri, false, null)
                when (deeplink.route) {
                    is DeepLinkRoute.DApp -> webView.loadUrl(url)
                    is DeepLinkRoute.Unknown -> BrowserHelper.open(requireContext(), url)
                    else -> processDeeplink(deeplink, url)
                }
            } else if (url.startsWith("https://x.com")) {
                BrowserHelper.openX(requireActivity(), uri)
            } else if (url.startsWith("https://t.me") || uri.scheme == "tg") {
                BrowserHelper.openTG(requireContext(), uri)
            } else {
                openNewDApp(uri)
            }
        }
    }

    private fun consumeDeepLinkThrottle(): Boolean {
        val now = System.currentTimeMillis()
        if ((now - lastDeepLinkTime) <= 1000) {
            return false
        }
        lastDeepLinkTime = now
        return true
    }

    private fun openNewDApp(uri: Uri) {
        navigation?.add(
            newInstance(
                wallet = wallet,
                title = uri.host ?: "unknown",
                iconUrl = "https://www.google.com/s2/favicons?sz=256&domain=${uri.host}",
                url = uri,
                source = DappBrowserAnalytics.IN_DAPP_LINK_SOURCE
            )
        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        headerDrawable = HeaderDrawable(requireContext())

        headerView = view.findViewById(R.id.header)
        headerView.background = headerDrawable

        backView = view.findViewById(R.id.back)
        backView.setMoonOnClickListener { back() }

        titleView = view.findViewById(R.id.title)
        titleView.text = args.title

        hostView = view.findViewById(R.id.host)
        hostView.text = args.url.host

        menuView = view.findViewById(R.id.menu)

        closeView = view.findViewById(R.id.close)
        closeView.setMoonOnClickListener { onCloseClick() }

        webView = view.findViewById(R.id.web_view)
        if (viewModel.isDarkTheme && WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
            WebSettingsCompat.setAlgorithmicDarkeningAllowed(webView.settings, true)
        }

        webView.setWallet(wallet)
        webView.settings.useWideViewPort = true
        webView.settings.loadWithOverviewMode = true
        webView.addCallback(webViewCallback)

        if (isTonConnectInjectionEnabled) {
            webView.setJsBridge(bridge)
        }
        onWebViewCreated(webView)
        loadInitialPage(webView, savedInstanceState?.getBundle(STATE_WEB_VIEW))

        refreshView = view.findViewById(R.id.refresh)
        refreshView.setColorSchemeColors(requireContext().tabBarActiveIconColor)
        refreshView.setOnRefreshListener {
            webView.reload()
        }

        ViewCompat.setOnApplyWindowInsetsListener(view) { _, insets ->
            applyStatusBarInset(insets.getInsets(WindowInsetsCompat.Type.statusBars()).top)
            val bottomInsets =
                insets.getInsets(WindowInsetsCompat.Type.ime() or WindowInsetsCompat.Type.navigationBars())
            refreshView.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = bottomInsets.bottom
            }

            insets
        }

        collectFlow(viewModel.connectionFlow) { connection ->
            if (connection == null) {
                setDefaultState()
                // The fragment scope outlives the view, and onDestroyView destroys the WebView.
                if (hadConnection && this.view != null) {
                    webView.emitEvent(JsonBuilder.disconnectEvent())
                }
            } else {
                setConnectionState(connection)
            }
            hadConnection = connection != null
        }
    }

    protected open fun onWebViewCreated(webView: TonConnectWebView) = Unit

    protected open fun loadInitialPage(webView: TonConnectWebView, restoredState: Bundle?) {
        if (restoredState == null || webView.restoreState(restoredState) == null) {
            webView.loadUrl(args.url.withUtmSource())
        }
    }

    protected open fun applyStatusBarInset(top: Int) {
        headerView.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            topMargin = top
        }
    }

    private fun setDefaultState() {
        menuView.setMoonOnClickListener { openDefaultMenu(it) }
    }

    private fun setConnectionState(connection: AppConnectEntity) {
        menuView.setMoonOnClickListener { openConnectionMenu(it, connection) }
    }

    private fun openDefaultMenu(view: View) {
        val actionSheet = ActionSheet(requireContext())
        actionSheet.addItem(REFRESH_ID, Localization.refresh, UIKitIcon.ic_refresh_16)
        actionSheet.addItem(SHARE_ID, Localization.share, UIKitIcon.ic_share_16)
        actionSheet.addItem(COPY_ID, Localization.copy_link, UIKitIcon.ic_copy_16)
        if (isRequestPinShortcutSupported) {
            actionSheet.addItem(
                ADD_HOME_SCREEN_ID,
                Localization.add_to_home_screen,
                UIKitIcon.ic_apps_16
            )
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
            actionSheet.addItem(
                ADD_HOME_SCREEN_ID,
                Localization.add_to_home_screen,
                UIKitIcon.ic_apps_16
            )
        }
        actionSheet.doOnItemClick = { actionClick(it.id) }
        actionSheet.show(view)
    }

    private fun addToHomeScreen() {
        lifecycleScope.launch {
            try {
                val app = buildAppEntity()
                val title = app.name
                val bitmap = AsyncImageView.loadSquareBitmap(requireContext(), app.iconUrl.toUri(), 512)
                    ?: throw IllegalArgumentException("Failed to load icon")

                val targetIntent = ShortcutDeeplinkActivity.intent(requireContext(), startUri.toString())

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
                dappAnalytics.sharingCopy(DappSharingCopyFrom.CopyLink)
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

    private fun shareLink() {
        if (DeepLinkBuilder.dAppIsSpecialUrl(currentUrl)) {
            dappAnalytics.sharingCopy(DappSharingCopyFrom.Share)
            ShareCompat.IntentBuilder(requireContext())
                .setType("text/plain")
                .setChooserTitle(getString(Localization.share))
                .setText(currentUrl.toString())
                .startChooser()
        } else {
            val app = buildAppEntity()
            navigation?.add(DAppShareScreen.newInstance(wallet, app, currentUrl, dappAnalytics))
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

        private const val REQUEST_CODE_FILE = 1933
        private const val REQUEST_CODE_PERMISSIONS = 1934

        private const val STATE_WEB_VIEW = "web_view_state"
        private const val HTTP_ERROR_STATUS = 400
        private const val LOAD_ERROR_HTTP = "http_error"
        private const val LOAD_ERROR_WEBVIEW = "webview_error"

        private const val PURGE_ORIGIN_STORAGE_JS =
            "(function(){try{localStorage.clear();sessionStorage.clear();return 'ok'}catch(e){return 'err:'+e}})()"

        fun newInstance(
            wallet: WalletEntity,
            title: String,
            url: Uri,
            iconUrl: String,
            source: String,
            forceConnect: Boolean = false,
            analytics: DappOpenAnalytics? = null,
            utm: UtmParameters = UtmParameters.Empty,
            type: DAppType = DAppType.Browser,
        ): DAppScreen {
            return newInstance(wallet, DAppArgs(title, url, source, iconUrl, forceConnect, analytics, utm, type))
        }

        fun newInstance(
            wallet: WalletEntity,
            app: AppEntity,
            source: String,
            forceConnect: Boolean = false,
            analytics: DappOpenAnalytics? = null,
        ): DAppScreen {
            return newInstance(wallet, app.name, app.url, app.iconUrl, source, forceConnect, analytics)
        }

        fun newInstance(
            wallet: WalletEntity,
            args: DAppArgs,
        ): DAppScreen {
            val fragment = when (args.type) {
                DAppType.Browser -> DAppScreen(wallet)
                DAppType.Earn -> EarnDAppScreen(wallet)
            }
            fragment.setArgs(args)
            return fragment
        }
    }
}
