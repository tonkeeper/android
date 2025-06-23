package uikit.widget.webview

import android.app.Dialog
import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Message
import android.util.AttributeSet
import android.util.Log
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.ServiceWorkerController
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebChromeClient.FileChooserParams
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.OutcomeReceiverCompat
import androidx.webkit.PrefetchException
import androidx.webkit.Profile
import androidx.webkit.SpeculativeLoadingConfig
import androidx.webkit.SpeculativeLoadingParameters
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import uikit.R
import uikit.navigation.Navigation
import java.util.LinkedList
import java.util.concurrent.Executor
import kotlin.coroutines.resume

open class WebViewFixed @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = android.R.attr.webViewStyle,
) : WebView(context, attrs, defStyle) {

    open class Callback {
        open fun onScroll(y: Int, x: Int) {  }
        open fun onElementBlurred() {  }
        open fun onElementFocused(rect: RectF) { }
        open fun onPageStarted(url: String, favicon: Bitmap?) { }
        open fun shouldOverrideUrlLoading(request: WebResourceRequest): Boolean { return false }
        open fun onPageFinished(url: String) { }
        open fun onReceivedTitle(title: String) { }
        open fun onProgressChanged(newProgress: Int) { }
        open fun onLoadResource(url: String): Boolean { return true }
        open fun onWindowClose() { }
        open fun onNewTab(url: String) { }
        open fun openFilePicker(fileChooserParams: FileChooserParams) { }
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

    private val callbacks = mutableListOf<Callback>()
    private var onNewTabRunnable: Runnable? = null

    private val jsExecuteQueue = LinkedList<String>()

    init {
        super.setLayerType(LAYER_TYPE_HARDWARE, null)
        isNestedScrollingEnabled = true
        overScrollMode = OVER_SCROLL_NEVER

        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.javaScriptCanOpenWindowsAutomatically = true
        settings.setSupportMultipleWindows(true)
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            super.setRendererPriorityPolicy(RENDERER_PRIORITY_IMPORTANT, false)
        }
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

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                for (callback in callbacks) {
                    if (callback.shouldOverrideUrlLoading(request)) {
                        return true
                    }
                }
                return false
            }

            override fun onPageFinished(view: WebView?, url: String) {
                super.onPageFinished(view, url)
                callbacks.forEach { it.onPageFinished(url) }
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

            /*override fun onPermissionRequest(request: PermissionRequest) {
                val resources = request.resources
                request.grant(resources)
            }*/

            override fun onCreateWindow(
                view: WebView,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: Message?
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
        Log.d("DAppScreenLog", "setFilePickerResult: ${arrays.joinToString(", ")}")
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
        view: WebView,
        resultMsg: Message?,
        callback: (url: String) -> Unit
    ) {
        val extra = view.hitTestResult.extra
        if (extra != null) {
            callback(extra)
        } else {
            getTargetUrlHack(view, resultMsg, callback)
        }
    }

    private fun getTargetUrlHack(
        view: WebView,
        resultMsg: Message?,
        callback: (url: String) -> Unit
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
                    view: WebView?,
                    request: WebResourceRequest?
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
        dialog.show()

        val transport = resultMsg.obj as? WebView.WebViewTransport ?: return false
        transport.webView = dialog.webView
        resultMsg.sendToTarget()
        return true
    }

    private class NewWindowDialog(context: Context, profileName: String): Dialog(context, R.style.Widget_Dialog) {

        val webView = WebViewFixed(context)

        init {
            webView.setProfileName(profileName)
            webView.addCallback(object : Callback() {
                override fun onWindowClose() {
                    dismiss()
                }
            })
            setContentView(webView)
            window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
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
        val jsCode = "(function() {" +
                "var focusedElement = document.activeElement;" +
                "if (focusedElement && (focusedElement.tagName === 'INPUT' || focusedElement.tagName === 'TEXTAREA')) {" +
                "   var rect = focusedElement.getBoundingClientRect();" +
                "   return rect.bottom;" +
                "} else {" +
                "   return -1;" +
                "}" +
                "})()"
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
        } catch (ignored: Throwable) { }
    }

    override fun destroy() {
        reset()
        try {
            removeAllViews()
        } catch (ignored: Throwable) { }
        super.destroy()
    }

    private var onScrollRunnable: Runnable? = null
    private var onElementFocusRunnable: Runnable? = null

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
            val rect = RectF(json.getDouble("left").toFloat(), json.getDouble("top").toFloat(), json.getDouble("right").toFloat(), json.getDouble("bottom").toFloat())
            this@WebViewFixed.onElementFocused(rect)
        }
    }
}



