package uikit.widget.webview

import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.RectF
import android.os.Build
import android.util.AttributeSet
import android.util.Log
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.LinkedList
import java.util.Queue
import kotlin.coroutines.resume

open class WebViewFixed @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = android.R.attr.webViewStyle,
) : WebView(context, attrs, defStyle) {

    init {
        super.setLayerType(LAYER_TYPE_HARDWARE, null)
        isNestedScrollingEnabled = true
        overScrollMode = OVER_SCROLL_NEVER

        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.javaScriptCanOpenWindowsAutomatically = true
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        settings.allowFileAccess = false
        settings.cacheMode = WebSettings.LOAD_NO_CACHE

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            super.setRendererPriorityPolicy(RENDERER_PRIORITY_IMPORTANT, false)
        }
        super.setBackgroundColor(Color.TRANSPARENT)
        if (0 != context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) {
            setWebContentsDebuggingEnabled(true)
        }
    }

    override fun hasOverlappingRendering(): Boolean {
        return false
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
}



