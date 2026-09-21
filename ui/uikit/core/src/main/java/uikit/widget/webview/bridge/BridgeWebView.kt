package uikit.widget.webview.bridge

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.AttributeSet
import android.webkit.JavascriptInterface
import androidx.annotation.MainThread
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.tonapps.log.L
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import uikit.widget.webview.WebViewFixed
import uikit.widget.webview.bridge.message.BridgeMessage
import uikit.widget.webview.bridge.message.FunctionInvokeBridgeMessage
import uikit.widget.webview.bridge.message.FunctionResponseBridgeMessage

open class BridgeWebView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = android.R.attr.webViewStyle,
) : WebViewFixed(context, attrs, defStyle) {

    private var jsBridge: JsBridge? = null

    private val scope: CoroutineScope?
        get() = findViewTreeLifecycleOwner()?.lifecycleScope

    private val webViewCallback = object : Callback() {
        override fun onPageStarted(url: String, favicon: Bitmap?) {
            super.onPageStarted(url, favicon)
            initBridge()
        }
    }

    init {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            WebViewCompat.addWebMessageListener(
                this,
                "ReactNativeWebView",
                setOf("*")
            ) { _, message, _, isMainFrame, _ ->
                if (isMainFrame) {
                    message.data?.let {
                        postMessage(it)
                    }
                }
            }
        } else {
            addJavascriptInterface(this, "ReactNativeWebView")
        }
        addCallback(webViewCallback)
        initBridge()
    }

    @SuppressLint("JavascriptInterface")
    @MainThread
    fun setJavascriptInterface(value: Any) {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            WebViewCompat.removeWebMessageListener(this, "ReactNativeWebView")
        }

        removeJavascriptInterface("ReactNativeWebView")
        addJavascriptInterface(value, "ReactNativeWebView")
    }

    fun setJsBridge(value: JsBridge) {
        if (jsBridge == null) {
            jsBridge = value
            registerInjection(value)
            executeJS(value.jsInjection())
        }
    }

    /**
     * Register the bridge JS to run at document start, before any page scripts.
     * This ensures window.tonkeeper exists when the dApp's @tonconnect/sdk initializes.
     */
    private fun registerInjection(bridge: JsBridge) {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            try {
                WebViewCompat.addDocumentStartJavaScript(this, bridge.jsInjection(), setOf("*"))
            } catch (e: Throwable) {
                L.e(e)
            }
        } else {
            executeJS(bridge.jsInjection())
        }
    }

    private fun initBridge() {
        val value = jsBridge ?: return
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            executeJS(value.jsInjection())
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

    @MainThread
    fun postMessage(
        message: JSONObject,
        onResult: ((String?) -> Unit)? = null,
    ) {
        evaluateJavascript("""
            (function() {
                window.postMessage($message);
                return 'posted';
            })();
        """.trimIndent()) { result ->
            onResult?.invoke(result)
        }
    }

    fun emitEvent(event: JSONObject) {
        val message = JSONObject()
        message.put("type", BridgeMessage.Type.Event.value)
        message.put("event", event)
        val code = """
            (function() {
                window.dispatchEvent(new MessageEvent('message', {
                    data: $message
                }));
            })();
        """
        executeJS(code)
    }

    @JavascriptInterface
    fun postMessage(message: String) {
        val invokeMessage = try {
            val json = JSONObject(message)
            if (json.optString("type") != BridgeMessage.Type.InvokeRnFunc.value) {
                return
            }
            FunctionInvokeBridgeMessage(json)
        } catch (e: JSONException) {
            L.e(e)
            return
        }
        val scope = scope
        if (scope == null) {
            L.w("bridge message dropped: no lifecycle owner")
            return
        }
        scope.launch {
            invokeFunction(invokeMessage)
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
