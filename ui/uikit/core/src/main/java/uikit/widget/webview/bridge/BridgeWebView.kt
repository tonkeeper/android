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

    private var isPageLoaded = false
        set(value) {
            field = value
            if (value) {
                executeJsQueue()
            }
        }

    private val _inputFocusFlow = MutableStateFlow(RectF())
    private val _scrollFlow = MutableStateFlow(0)

    @OptIn(FlowPreview::class)
    val inputFocusFlow = _inputFocusFlow.asStateFlow().debounce(32)

    val scrollFlow = _scrollFlow.asSharedFlow()

    private val clientCallbacks = mutableListOf<WebViewClient>()
    private val jsExecuteQueue = LinkedList<String>()
    private val scope: CoroutineScope
        get() = findViewTreeLifecycleOwner()?.lifecycleScope ?: throw IllegalStateException("No lifecycle owner")

    init {
        addJavascriptInterface(this, "ReactNativeWebView")
        webViewClient = object : WebViewClient() {

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                isPageLoaded = true
                initBridge()
                clientCallbacks.forEach { it.onPageStarted(view, url, favicon) }
            }

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                return clientCallbacks.map { it.shouldOverrideUrlLoading(view, request) }.firstOrNull() ?: false
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                clientCallbacks.forEach { it.onPageFinished(view, url) }
            }
        }
        applyInputFocusHandler()
        applyScrollListener()
        initBridge()
    }

    @SuppressLint("JavascriptInterface")
    private fun applyInputFocusHandler() {
        val interfaceName = "AndroidInputFocusHandler"
        val script = """
            (function() {
                document.addEventListener('focusin', function(event) {
                    if (event.target.tagName === 'INPUT' || event.target.tagName === 'TEXTAREA') {
                        const info = event.target.getBoundingClientRect();
                        window.$interfaceName.onElementFocused(JSON.stringify(info));
                    }
                });

                document.addEventListener('focusout', function(event) {
                    if (event.target.tagName === 'INPUT' || event.target.tagName === 'TEXTAREA') {
                        window.$interfaceName.onElementBlurred();
                    }
                });
            })();
        """.trimIndent()

        addJavascriptInterface(object {
            @JavascriptInterface
            fun onElementFocused(value: String) {
                val json = JSONObject(value)
                val rect = RectF(json.getDouble("left").toFloat(), json.getDouble("top").toFloat(), json.getDouble("right").toFloat(), json.getDouble("bottom").toFloat())
                _inputFocusFlow.value = rect
            }

            @JavascriptInterface
            fun onElementBlurred() {
                _inputFocusFlow.value = RectF()
            }
        }, interfaceName)
        executeJS(script)
    }

    private fun applyScrollListener() {
        val interfaceName = "AndroidScrollHandler"
        val script = """
            (function() {
                window.addEventListener('scroll', function(e) {
                    window.$interfaceName.onScroll(e.target.scrollTop);
                }, true);
            })();
        """.trimIndent()

        addJavascriptInterface(object {
            @JavascriptInterface
            fun onScroll(value: Int) {
                _scrollFlow.value = value
            }
        }, interfaceName)
        executeJS(script)
    }

    fun addClientCallback(callback: WebViewClient) {
        clientCallbacks.add(callback)
    }

    fun removeClientCallback(callback: WebViewClient) {
        clientCallbacks.remove(callback)
    }

    fun executeJS(code: String) {
        if (isPageLoaded) {
            evaluateJavascript(code)
        } else {
            jsExecuteQueue.add(code)
        }
    }

    private fun initBridge() {
        val value = jsBridge ?: return
        executeJS(value.jsInjection())
    }

    private fun executeJsQueue() {
        while (jsExecuteQueue.isNotEmpty()) {
            jsExecuteQueue.poll()?.let { evaluateJavascript(it) }
        }
    }

    private fun evaluateJavascript(code: String) {
        evaluateJavascript(code, null)
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