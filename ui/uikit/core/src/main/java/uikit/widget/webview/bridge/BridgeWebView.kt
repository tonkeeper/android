package uikit.widget.webview.bridge

import android.content.Context
import android.graphics.Bitmap
import android.util.AttributeSet
import android.webkit.JavascriptInterface
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.tonapps.log.L
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
