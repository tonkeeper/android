package uikit.widget.webview.bridge

import android.content.Context
import android.graphics.Bitmap
import android.util.AttributeSet
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import uikit.widget.webview.WebViewFixed
import uikit.widget.webview.bridge.message.BridgeMessage
import uikit.widget.webview.bridge.message.FunctionInvokeBridgeMessage
import uikit.widget.webview.bridge.message.FunctionResponseBridgeMessage
import java.util.LinkedList

class BridgeWebView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = android.R.attr.webViewStyle,
) : WebViewFixed(context, attrs, defStyle) {

    var jsBridge: JsBridge? = null
        set(value) {
            field = value
            value?.let {
                executeJS(value.jsInjection())
            }
        }

    private var isPageLoaded = false
        set(value) {
            field = value
            if (value) {
                executeJsQueue()
            }
        }

    private val jsExecuteQueue = LinkedList<String>()
    private val scope: CoroutineScope
        get() = findViewTreeLifecycleOwner()?.lifecycleScope ?: throw IllegalStateException("No lifecycle owner")

    init {
        addJavascriptInterface(this, "ReactNativeWebView")
        webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                isPageLoaded = true
            }
        }
    }

    fun executeJS(code: String) {
        if (isPageLoaded) {
            evaluateJavascript(code, null)
        } else {
            jsExecuteQueue.add(code)
        }
    }

    private fun executeJsQueue() {
        while (jsExecuteQueue.isNotEmpty()) {
            jsExecuteQueue.poll()?.let { evaluateJavascript(it, null) }
        }
    }

    private suspend fun postMessage(
        message: FunctionResponseBridgeMessage
    ) = withContext(Dispatchers.Main) {
        val code = """
            (function() {
                window.dispatchEvent(new MessageEvent('message', {
                    data: ${message.createJSON()}
                }));
            })();
        """
        executeJS(code)
    }

    @JavascriptInterface
    fun postMessage(message: String) {
        val json = JSONObject(message)
        val type = json.getString("type")
        if (type == BridgeMessage.Type.InvokeRnFunc.value) {
            scope.launch {
                invokeFunction(FunctionInvokeBridgeMessage(json))
            }
        }
    }

    private suspend fun invokeFunction(message: FunctionInvokeBridgeMessage) {
        val bridge = jsBridge ?: return
        try {
            val data = bridge.invokeFunction(message.name, message.args) ?: return
            postMessage(FunctionResponseBridgeMessage(
                invocationId = message.invocationId,
                status = "fulfilled",
                data = data,
            ))
        } catch (e: Throwable) {
            postMessage(FunctionResponseBridgeMessage(
                invocationId = message.invocationId,
                error = e,
            ))
        }
    }
}