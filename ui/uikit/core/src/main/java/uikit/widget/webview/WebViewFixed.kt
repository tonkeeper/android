package uikit.widget.webview

import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.RectF
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebChromeClient.FileChooserParams
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.webkit.Profile
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.tonapps.uikit.color.backgroundPageColor
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONException
import org.json.JSONObject
import org.json.JSONTokener
import uikit.R
import uikit.drawable.HeaderDrawable
import uikit.extensions.isDark
import uikit.extensions.setMoonOnClickListener
import java.util.LinkedList
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.coroutines.resume

private const val BLANK_PAGE_URL = "about:blank"
private const val BLANK_PAGE_HOST_CHECK_INTERVAL_MS = 500L

open class WebViewFixed @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = android.R.attr.webViewStyle,
) : WebView(context, attrs, defStyle) {

    open class Callback {
        open fun onScroll(y: Int, x: Int) {}
        open fun onElementBlurred() {}
        open fun onElementFocused(rect: RectF) {}
        open fun onPageStarted(url: String, favicon: Bitmap?) {}
        open fun shouldOverrideUrlLoading(request: WebResourceRequest): Boolean {
            return false
        }

        open fun onPageFinished(url: String) {}
        open fun onReceivedTitle(title: String) {}
        open fun onProgressChanged(newProgress: Int) {}
        open fun onLoadResource(url: String): Boolean {
            return true
        }

        open fun onWindowClose() {}
        open fun onNewTab(url: String) {}
        open fun openFilePicker(fileChooserParams: FileChooserParams) {}
        open fun onPermissionRequest(request: PermissionRequest) {}

        open fun shouldInterceptRequest(request: WebResourceRequest): WebResourceResponse? {
            return null
        }

        open fun onReceivedHttpError(request: WebResourceRequest?, errorResponse: WebResourceResponse?) { }
        open fun onReceivedError(request: WebResourceRequest?, error: WebResourceError?) { }
    }

    private var isPageLoaded = false
        set(value) {
            field = value
            if (value) {
                executeJsQueue()
            }
        }

    private var filePathCallback: ValueCallback<Array<Uri>>? = null

    private val androidWebViewBridgeJS: String by lazy {
        context.resources.openRawResource(R.raw.webview_ext).readBytes().decodeToString()
    }

    private val callbacks = CopyOnWriteArrayList<Callback>()
    private var onNewTabRunnable: Runnable? = null
    private var onScrollRunnable: Runnable? = null
    private var onElementFocusRunnable: Runnable? = null

    private val jsExecuteQueue = LinkedList<String>()
    private val newWindowDialogs = mutableListOf<NewWindowDialog>()

    init {
        super.setLayerType(LAYER_TYPE_HARDWARE, null)
        isNestedScrollingEnabled = true
        overScrollMode = OVER_SCROLL_NEVER

        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.javaScriptCanOpenWindowsAutomatically = true
        settings.setSupportMultipleWindows(true)
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        settings.allowFileAccess = false
        settings.cacheMode = WebSettings.LOAD_NO_CACHE
        settings.allowContentAccess = false
        settings.allowFileAccessFromFileURLs = false
        settings.allowUniversalAccessFromFileURLs = false
        settings.textSize = WebSettings.TextSize.NORMAL
        settings.setGeolocationEnabled(false)


        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
            WebSettingsCompat.setAlgorithmicDarkeningAllowed(settings, false)
        }
        if (WebViewFeature.isFeatureSupported(WebViewFeature.MUTE_AUDIO)) {
            WebViewCompat.setAudioMuted(this@WebViewFixed, true)
        }

        super.setRendererPriorityPolicy(RENDERER_PRIORITY_IMPORTANT, false)
        super.setBackgroundColor(Color.TRANSPARENT)
        if (0 != context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) {
            setWebContentsDebuggingEnabled(true)
        }
        webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                isPageLoaded = true
                callbacks.forEach { it.onPageStarted(url, favicon) }
            }

            override fun shouldOverrideUrlLoading(
                view: WebView, request: WebResourceRequest
            ): Boolean {
                for (callback in callbacks) {
                    if (callback.shouldOverrideUrlLoading(request)) {
                        return true
                    }
                }
                return handleDeeplink(request.url)
            }

            override fun onPageFinished(view: WebView?, url: String) {
                super.onPageFinished(view, url)
                callbacks.forEach { it.onPageFinished(url) }

                val anchor = url.toUri().fragment
                if (!anchor.isNullOrEmpty()) {
                    val safeAnchor = JSONObject.quote(anchor)
                    evaluateJavascript(
                        """
                        (function() {
                            var el = document.getElementById($safeAnchor);
                            if (el) {
                                el.scrollIntoView({behavior: 'smooth'});
                            }
                        })();
                        """.trimIndent()
                    )
                }
            }

            override fun onLoadResource(view: WebView?, url: String) {
                var isStop = false
                for (callback in callbacks) {
                    if (!callback.onLoadResource(url)) {
                        isStop = true
                    }
                }
                if (!isStop) {
                    super.onLoadResource(view, url)
                }
            }

            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                super.onReceivedError(view, request, error)
                callbacks.forEach { it.onReceivedError(request, error) }
            }

            override fun onReceivedHttpError(view: WebView?, request: WebResourceRequest?, errorResponse: WebResourceResponse?) {
                super.onReceivedHttpError(view, request, errorResponse)
                callbacks.forEach { it.onReceivedHttpError(request, errorResponse) }
            }

            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest
            ): WebResourceResponse? {
                for (callback in callbacks) {
                    callback.shouldInterceptRequest(request)?.let { return it }
                }
                return super.shouldInterceptRequest(view, request)
            }
        }

        webChromeClient = object : WebChromeClient() {
            override fun onReceivedTitle(view: WebView?, title: String) {
                super.onReceivedTitle(view, title)
                callbacks.forEach { it.onReceivedTitle(title) }
            }

            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
                callbacks.forEach { it.onProgressChanged(newProgress) }
            }

            override fun onPermissionRequest(request: PermissionRequest) {
                callbacks.forEach { it.onPermissionRequest(request) }
            }

            override fun onCreateWindow(
                view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message?
            ): Boolean {
                if (isDialog) {
                    return resultMsg?.let { openNewWindow(it) } ?: false
                }
                getTargetUrl(view, resultMsg) { newUrl ->
                    onNewTab(newUrl)
                }
                return true
            }

            override fun onCloseWindow(window: WebView) {
                super.onCloseWindow(window)
                callbacks.forEach { it.onWindowClose() }
            }

            override fun onShowFileChooser(
                webView: WebView?,
                callback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                filePathCallback?.onReceiveValue(null)
                val params = fileChooserParams ?: return false
                filePathCallback = callback
                callbacks.forEach { it.openFilePicker(params) }
                return true
            }
        }

        applyAndroidWebViewBridge()
    }

    fun setFilePickerResult(arrays: Array<Uri>) {
        filePathCallback?.onReceiveValue(arrays)
        filePathCallback = null
    }

    private fun onNewTab(url: String) {
        onNewTabRunnable?.let { removeCallbacks(it) }
        onNewTabRunnable = Runnable {
            callbacks.forEach { it.onNewTab(url) }
        }
        onNewTabRunnable?.let { postDelayed(it, 220) }
    }

    private fun getTargetUrl(
        view: WebView, resultMsg: Message?, callback: (url: String) -> Unit
    ) {
        val extra = view.hitTestResult.extra
        if (extra != null) {
            callback(extra)
        } else {
            getTargetUrlHack(view, resultMsg, callback)
        }
    }

    private fun getTargetUrlHack(
        view: WebView, resultMsg: Message?, callback: (url: String) -> Unit
    ) {
        val newWebView = WebView(view.context).apply {
            settings.apply {
                javaScriptEnabled = true
                displayZoomControls = false
                loadWithOverviewMode = true
                setSupportMultipleWindows(true)
            }
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView?, request: WebResourceRequest?
                ): Boolean {
                    request?.url?.let {
                        callback(it.toString())
                        post {
                            destroy()
                        }
                    }
                    return true
                }
            }
        }
        try {
            val transport = resultMsg?.obj as WebView.WebViewTransport
            transport.webView = newWebView
            resultMsg.sendToTarget()
        } catch (e: Throwable) {
            newWebView.destroy()
        }
    }

    private fun openNewWindow(resultMsg: Message): Boolean {
        val name = getProfile()?.name ?: return false
        val dialog = NewWindowDialog(context, name)
        this.callbacks.forEach { callback ->
            dialog.webView.addCallback(object : Callback() {
                override fun shouldOverrideUrlLoading(request: WebResourceRequest): Boolean {
                    return callback.shouldOverrideUrlLoading(request)
                }

                override fun shouldInterceptRequest(request: WebResourceRequest): WebResourceResponse? {
                    return callback.shouldInterceptRequest(request)
                }
            })
        }
        dialog.setOnDismissListener { newWindowDialogs.remove(dialog) }
        newWindowDialogs.add(dialog)
        dialog.show()

        val transport = resultMsg.obj as? WebViewTransport ?: return false
        transport.webView = dialog.webView
        resultMsg.sendToTarget()
        dialog.postBlankPageHostCheck()
        return true
    }

    private class NewWindowDialog(context: Context, profileName: String) :
        Dialog(context, R.style.Widget_Dialog) {

        val webView = WebViewFixed(context)

        private val headerDrawable = HeaderDrawable(context)
        private val titleView: AppCompatTextView
        private val hostView: AppCompatTextView
        private val blankPageHostHandler = Handler(Looper.getMainLooper())
        private val blankPageHostCheck = Runnable { checkBlankPageHost() }
        private var blankPageHostRequestId = 0

        private val isAttachedAndShowing: Boolean
            get() = isShowing && window?.decorView?.isAttachedToWindow == true

        init {
            setContentView(R.layout.dialog_web_popup)
            findViewById<View>(R.id.web_popup_header).background = headerDrawable
            titleView = findViewById(R.id.web_popup_title)
            hostView = findViewById(R.id.web_popup_host)
            hostView.text = BLANK_PAGE_URL
            findViewById<View>(R.id.web_popup_close).setMoonOnClickListener { dismiss() }
            findViewById<FrameLayout>(R.id.web_popup_container).addView(
                webView,
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.web_popup)) { view, insets ->
                val topInsets = insets.getInsets(WindowInsetsCompat.Type.statusBars())
                val bottomInsets = insets.getInsets(WindowInsetsCompat.Type.ime() or WindowInsetsCompat.Type.navigationBars())
                view.updatePadding(top = topInsets.top, bottom = bottomInsets.bottom)
                insets
            }

            webView.setProfileName(profileName)
            webView.addCallback(object : Callback() {
                override fun onWindowClose() {
                    if (isAttachedAndShowing) {
                        dismiss()
                    }
                }

                override fun onReceivedTitle(title: String) {
                    titleView.text = title
                    titleView.isVisible = title.isNotBlank()
                }

                override fun onPageStarted(url: String, favicon: Bitmap?) {
                    updateHost(url)
                }

                override fun onPageFinished(url: String) {
                    updateHost(webView.url ?: url)
                }

                override fun onScroll(y: Int, x: Int) {
                    headerDrawable.setDivider(y > 0)
                }
            })
            window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            matchSystemBarsWithBackground()
        }

        fun postBlankPageHostCheck() {
            blankPageHostHandler.post(blankPageHostCheck)
        }

        private fun updateHost(url: String?) {
            stopBlankPageHostCheck()
            val host = siteHost(url)
            if (host == null) {
                hostView.text = BLANK_PAGE_URL
                checkBlankPageHost()
            } else {
                hostView.text = host
            }
        }

        private fun checkBlankPageHost() {
            val requestId = ++blankPageHostRequestId
            webView.evaluateJavascript("location.href") { value ->
                if (requestId != blankPageHostRequestId) {
                    return@evaluateJavascript
                }
                val host = siteHost(parseJsString(value))
                if (host == null) {
                    blankPageHostHandler.postDelayed(blankPageHostCheck, BLANK_PAGE_HOST_CHECK_INTERVAL_MS)
                } else {
                    hostView.text = host
                }
            }
        }

        private fun stopBlankPageHostCheck() {
            blankPageHostRequestId++
            blankPageHostHandler.removeCallbacks(blankPageHostCheck)
        }

        private fun parseJsString(value: String?): String? {
            return try {
                JSONTokener(value.orEmpty()).nextValue() as? String
            } catch (_: JSONException) {
                null
            }
        }

        private fun siteHost(url: String?): String? {
            val uri = url?.toUri() ?: return null
            return when (val scheme = uri.scheme?.lowercase()) {
                null, "about" -> null
                "blob" -> uri.schemeSpecificPart.toUri().host ?: "$scheme:"
                else -> uri.host ?: "$scheme:"
            }
        }

        private fun matchSystemBarsWithBackground() {
            val dialogWindow = window ?: return
            val isLightBackground = !context.backgroundPageColor.isDark()
            WindowCompat.getInsetsController(dialogWindow, dialogWindow.decorView).apply {
                isAppearanceLightStatusBars = isLightBackground
                isAppearanceLightNavigationBars = isLightBackground
            }
        }

        override fun onStop() {
            super.onStop()
            destroyWebView()
        }

        fun destroy() {
            when {
                isAttachedAndShowing -> dismiss()
                isShowing -> destroyWebView()
            }
        }

        private fun destroyWebView() {
            stopBlankPageHostCheck()
            webView.destroy()
        }
    }

    private fun applyAndroidWebViewBridge() {
        addJavascriptInterface(AndroidWebViewBridge(), "AndroidWebViewBridge")
        executeJS(androidWebViewBridgeJS)
    }

    fun addCallback(callback: Callback) {
        if (!callbacks.contains(callback)) {
            callbacks.add(callback)
        }
    }

    fun removeCallback(callback: Callback) {
        callbacks.remove(callback)
    }

    fun loadUrl(uri: Uri) {
        loadUrl(uri.toString())
    }

    private fun handleDeeplink(uri: Uri): Boolean {
        val scheme = uri.scheme?.lowercase() ?: return false
        if (scheme == "http" || scheme == "https") {
            return false
        }
        return try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }

    override fun hasOverlappingRendering(): Boolean {
        return false
    }

    fun executeJS(code: String) {
        if (isPageLoaded) {
            evaluateJavascript(code)
        } else {
            jsExecuteQueue.add(code)
        }
    }

    private fun executeJsQueue() {
        while (jsExecuteQueue.isNotEmpty()) {
            jsExecuteQueue.poll()?.let { evaluateJavascript(it) }
        }
    }

    private fun evaluateJavascript(code: String) {
        evaluateJavascript(code, null)
    }

    suspend fun getInputBottom(): Float = suspendCancellableCoroutine { continuation ->
        val jsCode =
            "(function() {" + "var focusedElement = document.activeElement;" + "if (focusedElement && (focusedElement.tagName === 'INPUT' || focusedElement.tagName === 'TEXTAREA')) {" + "   var rect = focusedElement.getBoundingClientRect();" + "   return rect.bottom;" + "} else {" + "   return -1;" + "}" + "})()"
        evaluateJavascript(jsCode) { value ->
            val elementBottom = value.toFloatOrNull() ?: -1f
            continuation.resume(elementBottom)
        }
    }

    fun reset() {
        super.loadDataWithBaseURL(null, "", "text/html", "utf-8", null)
        super.clearHistory()
        try {
            (this.parent as ViewGroup).removeView(this)
        } catch (ignored: Throwable) {
        }
    }

    override fun destroy() {
        val openNewWindowDialogs = newWindowDialogs.toList()
        newWindowDialogs.clear()
        openNewWindowDialogs.forEach { it.destroy() }
        reset()
        try {
            removeAllViews()
        } catch (ignored: Throwable) {
        }
        super.destroy()
    }

    private fun onScroll(x: Int, y: Int) {
        removeCallbacks(onScrollRunnable)
        onScrollRunnable = Runnable {
            callbacks.forEach { it.onScroll(y, x) }
        }
        postOnAnimationDelayed(onScrollRunnable, 16)
    }

    private fun onElementBlurred() {
        removeCallbacks(onElementFocusRunnable)
        onElementFocusRunnable = Runnable {
            callbacks.forEach { it.onElementBlurred() }
        }
        postOnAnimationDelayed(onElementFocusRunnable, 16)
    }

    private fun onElementFocused(rect: RectF) {
        removeCallbacks(onElementFocusRunnable)
        onElementFocusRunnable = Runnable {
            callbacks.forEach { it.onElementFocused(rect) }
        }
        postOnAnimationDelayed(onElementFocusRunnable, 16)
    }

    fun setProfileName(name: String) {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
            WebViewCompat.setProfile(this, name)
        }

        getProfile()?.apply {
            cookieManager.setAcceptCookie(true)
            cookieManager.setAcceptThirdPartyCookies(this@WebViewFixed, true)
            cookieManager.flush()
        }
    }

    fun getProfile(): Profile? {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
            return WebViewCompat.getProfile(this)
        }
        return null
    }

    inner class AndroidWebViewBridge {

        @JavascriptInterface
        fun onScroll(x: Int, y: Int) {
            this@WebViewFixed.onScroll(x, y)
        }

        @JavascriptInterface
        fun onElementBlurred() {
            this@WebViewFixed.onElementBlurred()
        }

        @JavascriptInterface
        fun onElementFocused(value: String) {
            val json = JSONObject(value)
            val rect = RectF(
                json.getDouble("left").toFloat(),
                json.getDouble("top").toFloat(),
                json.getDouble("right").toFloat(),
                json.getDouble("bottom").toFloat()
            )
            this@WebViewFixed.onElementFocused(rect)
        }
    }
}


