package com.tonapps.tonkeeper.ui.screen.browser.dapp.inapp

import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.lifecycleScope
import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.core.helper.ShareManager
import com.tonapps.log.L
import com.tonapps.tonkeeper.Environment
import com.tonapps.tonkeeper.extensions.isSameOriginOrSubdomain
import com.tonapps.tonkeeper.extensions.toast
import com.tonapps.tonkeeper.extensions.withUtmSource
import com.tonapps.tonkeeper.ui.component.TonConnectWebView
import com.tonapps.tonkeeper.ui.screen.browser.dapp.DAppScreen
import com.tonapps.tonkeeperx.R
import com.tonapps.wallet.localization.Localization
import com.tonapps.webview.bridge.InAppBridgeConnection
import com.tonapps.webview.bridge.InAppBridgeContract.Bridges
import com.tonapps.webview.bridge.InAppBridgeContract.Effect
import com.tonapps.webview.bridge.InAppBridgeContract.HapticStyle
import com.tonapps.webview.bridge.transport.InAppTransport
import com.tonapps.webview.bridge.transport.WebMessageTransport
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import ui.components.moon.screen.MoonEmptyScreen
import ui.components.moon.screen.MoonEmptyScreenType
import ui.theme.MoonTheme
import ui.theme.UIKit
import uikit.extensions.collectFlow
import uikit.extensions.getDimensionPixelSize
import uikit.extensions.hapticConfirm
import uikit.extensions.hapticReject
import uikit.extensions.hapticSoftTap
import uikit.extensions.hapticTick
import uikit.extensions.setMoonOnClickListener
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

    /** Own pages span the screen under the system bars and keep clear of them through the safe area. */
    protected open val drawsOwnPagesUnderSystemBars: Boolean = false

    /** The close button stays over own pages; otherwise it shows only while a load has failed. */
    protected open val showsCloseOverOwnPages: Boolean = false

    private val environment: Environment by inject()

    private lateinit var standaloneCloseView: View

    private lateinit var loadFailureView: ComposeView

    private var httpErrorPageUrl: String? = null

    // From a failed load until a page loads, the retry included: the user keeps a way out.
    private var loadFailed = false

    private var closeHiddenByPage = false

    private var shareSheetRequested = false

    // Until a page has loaded, the WebView may hold the URL of a stopped load with nothing to reload.
    private var pageLoaded = false

    private var loadTimeoutJob: Job? = null

    private var statusBarHeight = 0

    private var spinnerStartOffset = 0

    private var spinnerRestingTop = 0

    private var spinnerShift = 0

    protected var headerHidden = true
        private set

    private val pageUnderSystemBars: Boolean
        get() = headerHidden && drawsOwnPagesUnderSystemBars

    // Own pages follow the app theme through the bridge; darkening would invert them a second time.
    override val darkensPage: Boolean
        get() = !headerHidden && super.darkensPage

    private val connections = mutableListOf<InAppBridgeConnection<*, *>>()

    private val webViewCallback = object : WebViewFixed.Callback() {
        override fun onPageStarted(url: String, favicon: Bitmap?) {
            super.onPageStarted(url, favicon)
            loadFailureView.isVisible = url == httpErrorPageUrl
            closeHiddenByPage = false
            applyHeaderVisibility(url)
            applyStandaloneCloseVisibility()
        }

        override fun onPageFinished(url: String) {
            super.onPageFinished(url)
            loadTimeoutJob?.cancel()
            httpErrorPageUrl = null
            if (!loadFailureView.isVisible) {
                loadFailed = false
                pageLoaded = true
            }
            applyHeaderVisibility(webView.url ?: url)
            applyStandaloneCloseVisibility()
        }

        // WebView replaces the page with its own error page, so there is nothing left to keep.
        override fun onReceivedError(request: WebResourceRequest?, error: WebResourceError?) {
            super.onReceivedError(request, error)
            if (request?.isForMainFrame == true) {
                showLoadFailure()
            }
        }

        // Another site keeps its own error page: single-page apps on static hosting answer a deep
        // link with 404 and still render.
        override fun onReceivedHttpError(request: WebResourceRequest?, errorResponse: WebResourceResponse?) {
            super.onReceivedHttpError(request, errorResponse)
            val status = errorResponse?.statusCode ?: return
            if (request?.isForMainFrame == true && status >= HTTP_ERROR_STATUS && isOwnPage(request.url)) {
                httpErrorPageUrl = request.url.toString()
                showLoadFailure()
            }
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
            is Effect.Toast -> navigation?.toast(effect.title)
            is Effect.Haptic -> if (isResumed) {
                playHaptic(effect.style)
            }
            is Effect.CloseButtonVisibility -> {
                closeHiddenByPage = !effect.visible
                applyStandaloneCloseVisibility()
            }
            is Effect.Share -> if (isResumed && !shareSheetRequested) {
                shareSheetRequested = true
                ShareManager(requireActivity()).share(effect.text)
            }
        }
    }

    private fun playHaptic(style: HapticStyle) {
        when (style) {
            HapticStyle.Selection -> webView.hapticTick()
            HapticStyle.Light -> webView.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            HapticStyle.Medium -> webView.hapticSoftTap()
            HapticStyle.Heavy -> webView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            HapticStyle.Success -> webView.hapticConfirm()
            HapticStyle.Warning, HapticStyle.Error -> webView.hapticReject()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        standaloneCloseView = view.findViewById(R.id.standalone_close)
        loadFailureView = view.findViewById(R.id.load_failure)
        httpErrorPageUrl = null
        loadFailed = false
        closeHiddenByPage = false
        pageLoaded = false
        super.onViewCreated(view, savedInstanceState)
        headerHidden = true
        headerView.isVisible = false
        standaloneCloseView.setMoonOnClickListener { onCloseClick() }
        applyStandaloneCloseVisibility()
        loadFailureView.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        loadFailureView.setContent {
            MoonTheme(colorScheme = environment.theme) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(UIKit.colorScheme.background.page)
                        // Keeps touches from the pull-to-refresh and the page underneath.
                        .pointerInput(Unit) {},
                ) {
                    MoonEmptyScreen(
                        text = stringResource(Localization.something_went_wrong),
                        description = stringResource(Localization.dapp_load_failed_subtitle),
                        type = MoonEmptyScreenType.Error,
                        buttonText = stringResource(Localization.retry),
                        onButtonClick = ::retryLoad,
                    )
                }
            }
        }
        spinnerStartOffset = refreshView.progressViewStartOffset
        spinnerRestingTop = refreshView.progressViewStartOffset + refreshView.progressViewEndOffset
        spinnerShift = 0
        webView.addCallback(webViewCallback)
        viewLifecycleOwner.collectFlow(dappBridges.effects) { onEffect(it) }
    }

    override fun loadInitialPage(webView: TonConnectWebView, restoredState: Bundle?) {
        startLoadTimeout()
        viewLifecycleOwner.lifecycleScope.launch {
            attachBridges(webView)
            super.loadInitialPage(webView, restoredState)
        }
    }

    override fun onResume() {
        super.onResume()
        shareSheetRequested = false
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
        standaloneCloseView.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            topMargin = top + requireContext().getDimensionPixelSize(uikit.R.dimen.offsetMedium)
        }
    }

    override fun applyBottomInsets(insets: WindowInsetsCompat) {
        if (!pageUnderSystemBars) {
            super.applyBottomInsets(insets)
            return
        }
        refreshView.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            bottomMargin = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
        }
    }

    // The keyboard stays ours: the margin above it already lifts the page.
    override fun pageInsets(insets: WindowInsetsCompat): WindowInsetsCompat {
        if (!pageUnderSystemBars) {
            return super.pageInsets(insets)
        }
        return insets.inset(0, 0, 0, insets.getInsets(WindowInsetsCompat.Type.ime()).bottom)
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
            if (!connection.attach(viewLifecycleOwner.lifecycleScope)) {
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
        applyAlgorithmicDarkening()
        ViewCompat.requestApplyInsets(webView)
        applyStandaloneCloseVisibility()
    }

    private fun applyStandaloneCloseVisibility() {
        standaloneCloseView.isVisible = headerHidden && (loadFailed || (showsCloseOverOwnPages && !closeHiddenByPage))
    }

    private fun showLoadFailure() {
        loadTimeoutJob?.cancel()
        loadFailed = true
        loadFailureView.isVisible = true
        applyStandaloneCloseVisibility()
    }

    // A host that accepts the connection and stays silent would keep the page blank for minutes.
    private fun startLoadTimeout() {
        loadTimeoutJob?.cancel()
        loadTimeoutJob = viewLifecycleOwner.lifecycleScope.launch {
            delay(INITIAL_LOAD_TIMEOUT_MS)
            refreshView.isRefreshing = false
            reportLoadTimeout()
            showLoadFailure()
            webView.stopLoading()
        }
    }

    private fun retryLoad() {
        httpErrorPageUrl = null
        loadFailureView.isVisible = false
        refreshView.isRefreshing = true
        startLoadTimeout()
        if (!pageLoaded) {
            webView.loadUrl(startUri.withUtmSource().toString())
        } else {
            webView.reload()
        }
    }

    private fun applyTopInsets() {
        headerView.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            topMargin = statusBarHeight
        }
        refreshView.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            if (headerHidden && !drawsOwnPagesUnderSystemBars) {
                topMargin = statusBarHeight
            } else {
                topMargin = 0
            }
        }
        applySpinnerShift()
    }

    // Under the status bar the pull-to-refresh spinner would start hidden behind it.
    private fun applySpinnerShift() {
        val shift = if (pageUnderSystemBars) {
            statusBarHeight
        } else {
            0
        }
        if (shift == spinnerShift) {
            return
        }
        val refreshing = refreshView.isRefreshing
        refreshView.setProgressViewOffset(false, spinnerStartOffset + shift, spinnerRestingTop + shift)
        spinnerShift = shift
        refreshView.isRefreshing = refreshing
    }

    private companion object {
        const val INITIAL_LOAD_TIMEOUT_MS = 60_000L
    }
}
