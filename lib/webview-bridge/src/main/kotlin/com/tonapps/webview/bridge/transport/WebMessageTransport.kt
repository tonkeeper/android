package com.tonapps.webview.bridge.transport

import android.webkit.WebView
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.ScriptHandler
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.tonapps.log.L

/**
 * Script and listener are both bound to [allowedOriginRules]: a third-party page opened from the
 * page stays in this same WebView (InjectedTonConnectScreen.overrideUrlLoading for dApps) and must
 * not inherit either.
 */
class WebMessageTransport(
    private val webView: WebView,
    private val allowedOriginRules: Set<String>,
    private val mainFrameOnly: Boolean = true,
) : InAppTransport {

    private var script: ScriptHandler? = null
    private var listenerName: String? = null

    override fun attach(
        endpoint: InAppTransport.Endpoint,
        receiver: InAppTransport.Receiver,
    ): Boolean {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            return false
        }
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            return false
        }
        return try {
            WebViewCompat.addWebMessageListener(
                webView,
                endpoint.name,
                allowedOriginRules,
            ) { _, message, sourceOrigin, isMainFrame, replyProxy ->
                val data = message.data
                if ((isMainFrame || !mainFrameOnly) && data != null) {
                    receiver.onMessage(data, sourceOrigin.host.orEmpty(), webView.url, ProxyReply(replyProxy))
                }
            }
            listenerName = endpoint.name
            script = WebViewCompat.addDocumentStartJavaScript(
                webView,
                endpoint.script,
                allowedOriginRules,
            )
            true
        } catch (e: Throwable) {
            L.e(e)
            detach()
            false
        }
    }

    override fun detach() {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            script?.let { handler ->
                runCatching { handler.remove() }.onFailure { L.e(it) }
            }
        }
        script = null
        val name = listenerName ?: return
        listenerName = null
        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            runCatching { WebViewCompat.removeWebMessageListener(webView, name) }
                .onFailure { L.e(it) }
        }
    }

    private class ProxyReply(private val proxy: JavaScriptReplyProxy) : InAppTransport.Reply {
        override fun post(data: String) {
            runCatching { proxy.postMessage(data) }
                .onFailure { L.e(it) }
        }
    }
}