package uikit.widget.webview.bridge

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.os.Build
import android.util.AttributeSet
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.annotation.RequiresApi
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
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

    private val scope: CoroutineScope
        get() = findViewTreeLifecycleOwner()?.lifecycleScope ?: throw IllegalStateException("No lifecycle owner")

    private val webViewCallback = object : Callback() {
        override fun onPageStarted(url: String, favicon: Bitmap?) {
            super.onPageStarted(url, favicon)
            initBridge()
        }
    }

    init {
        addJavascriptInterface(this, "ReactNativeWebView")
        addCallback(webViewCallback)
        initBridge()
    }

    private fun initBridge() {
        val value = jsBridge ?: return
        executeJS(value.jsInjection())
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
        Log.d("DAppBridgeLog", "postMessage: $message")
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