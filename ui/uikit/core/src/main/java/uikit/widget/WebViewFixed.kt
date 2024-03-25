package uikit.widget

import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import android.util.AttributeSet
import android.util.Log
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import java.util.LinkedList
import java.util.Queue

class WebViewFixed @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = android.R.attr.webViewStyle,
) : WebView(context, attrs, defStyle) {

    class JsExecutor(private val callback: (String) -> Unit) {

        private var ready = false
        private val jsExecuteQueue = LinkedList<String>()

        fun execute(code: String) {
            if (ready) {
                callback(code)
            } else {
                jsExecuteQueue.add(code)
            }
        }

        fun ready() {
            ready = true
            while (jsExecuteQueue.isNotEmpty()) {
                jsExecuteQueue.poll()?.let { callback(it) }
            }
        }
    }

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)
        isNestedScrollingEnabled = true
        overScrollMode = OVER_SCROLL_NEVER

        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.javaScriptCanOpenWindowsAutomatically = true
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        settings.allowFileAccess = false
        settings.cacheMode = WebSettings.LOAD_NO_CACHE

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            setRendererPriorityPolicy(RENDERER_PRIORITY_IMPORTANT, false)
        }
        setBackgroundColor(Color.TRANSPARENT)

        if (0 != context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) {
            setWebContentsDebuggingEnabled(true)
        }
    }

    override fun hasOverlappingRendering(): Boolean {
        return false
    }

    override fun destroy() {
        super.loadDataWithBaseURL(null, "", "text/html", "utf-8", null)
        super.clearHistory()
        try {
            (this.parent as ViewGroup).removeView(this)
        } catch (ignored: Throwable) { }
        try {
            removeAllViews()
        } catch (ignored: Throwable) { }
        super.destroy()
    }
}



