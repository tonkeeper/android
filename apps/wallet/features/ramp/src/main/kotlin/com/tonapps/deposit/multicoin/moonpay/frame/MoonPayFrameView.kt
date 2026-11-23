package com.tonapps.deposit.multicoin.moonpay.frame

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.net.Uri
import android.net.http.SslError
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.tonapps.deposit.multicoin.moonpay.api.MoonPayEnvironment
import com.tonapps.log.L
import com.tonapps.webview.bridge.InAppBridge
import com.tonapps.webview.bridge.transport.WebMessageTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import java.util.UUID

@SuppressLint("SetJavaScriptEnabled", "ViewConstructor")
class MoonPayFrameView(
    context: Context,
    val frame: MoonPayFrame,
    private val environment: MoonPayEnvironment,
    parentScope: CoroutineScope,
    private val host: Host,
    private val channelId: String = UUID.randomUUID().toString(),
) : WebView(context) {

    interface Host {

        val isListening: Boolean

        fun onEvent(event: MoonPayFrameEvent)

        fun onFailure(error: MoonPayFrameException)

        fun requestPermissions(permissions: Array<String>, onResult: (granted: Boolean) -> Unit)

        fun pickFiles(params: WebChromeClient.FileChooserParams, onResult: (Array<Uri>?) -> Unit)

        fun onPageFinished()
    }

    private val scope = CoroutineScope(
        parentScope.coroutineContext + SupervisorJob(parentScope.coroutineContext.job),
    )

    private val session = MoonPayFrameSession(host::onEvent)

    private val connection = InAppBridge(MoonPayBridgeProtocol(channelId), session).connection(
        WebMessageTransport(this, setOf(environment.frameOrigin), mainFrameOnly = false),
    )

    private var dialog: AlertDialog? = null

    private var destroyed = false

    init {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.mediaPlaybackRequiresUserGesture = false
        settings.javaScriptCanOpenWindowsAutomatically = true
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        // Google Pay in a WebView goes through the Payment Request API, off by default.
        if (frame is MoonPayFrame.BuyButton && WebViewFeature.isFeatureSupported(WebViewFeature.PAYMENT_REQUEST)) {
            WebSettingsCompat.setPaymentRequestEnabled(settings, true)
        }
        webViewClient = FrameClient()
        webChromeClient = FrameChromeClient()
    }

    fun load() {
        val url = try {
            frame.url(environment, channelId)
        } catch (e: MoonPayFrameException) {
            host.onFailure(e)
            return
        }
        scope.launch {
            if (connection.attach(scope)) {
                if (host.isListening) {
                    loadUrl(url)
                }
            } else {
                host.onFailure(MoonPayFrameException.LoadFailed("bridge unavailable"))
            }
        }
    }

    fun send(command: MoonPayFrameCommand) {
        session.send(command)
    }

    override fun destroy() {
        if (destroyed) {
            return
        }
        destroyed = true
        dialog?.dismiss()
        connection.detach()
        scope.cancel()
        stopLoading()
        super.destroy()
    }

    private fun fail(reason: String) {
        L.w("MoonPay frame $frame: $reason")
        host.onFailure(MoonPayFrameException.LoadFailed(reason))
    }

    private fun showDialog(result: JsResult, build: AlertDialog.Builder.() -> Unit): Boolean {
        if (!isAttachedToWindow || destroyed) {
            result.cancel()
            return true
        }
        dialog = AlertDialog.Builder(context)
            .apply(build)
            .setOnCancelListener { result.cancel() }
            .setOnDismissListener { dialog = null }
            .show()
        return true
    }

    private inner class FrameClient : WebViewClient() {

        override fun onPageFinished(view: WebView, url: String?) {
            host.onPageFinished()
        }

        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
            if (request.isForMainFrame) {
                fail("${error.errorCode} ${error.description}")
            }
        }

        override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
            handler.cancel()
            if (environment.isFrameOrigin(error.url)) {
                fail("ssl error ${error.primaryError}")
            }
        }

        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
            fail("render process gone")
            return true
        }
    }

    private inner class FrameChromeClient : WebChromeClient() {

        override fun onJsAlert(view: WebView, url: String?, message: String?, result: JsResult): Boolean {
            return showDialog(result) {
                setMessage(message)
                setPositiveButton(android.R.string.ok) { _, _ -> result.confirm() }
            }
        }

        override fun onJsConfirm(view: WebView, url: String?, message: String?, result: JsResult): Boolean {
            return showDialog(result) {
                setMessage(message)
                setPositiveButton(android.R.string.ok) { _, _ -> result.confirm() }
                setNegativeButton(android.R.string.cancel) { _, _ -> result.cancel() }
            }
        }

        override fun onJsPrompt(
            view: WebView,
            url: String?,
            message: String?,
            defaultValue: String?,
            result: JsPromptResult,
        ): Boolean {
            val input = EditText(context).apply { setText(defaultValue) }
            return showDialog(result) {
                setMessage(message)
                setView(input)
                setPositiveButton(android.R.string.ok) { _, _ -> result.confirm(input.text.toString()) }
                setNegativeButton(android.R.string.cancel) { _, _ -> result.cancel() }
            }
        }

        override fun onPermissionRequest(request: PermissionRequest) {
            val resources = request.resources.filter { it in MEDIA_PERMISSIONS }
            if (!environment.isFrameOrigin(url.orEmpty()) || resources.isEmpty()) {
                request.deny()
                return
            }
            val permissions = resources.map { MEDIA_PERMISSIONS.getValue(it) }.toTypedArray()
            host.requestPermissions(permissions) { granted ->
                when {
                    destroyed -> Unit
                    granted -> request.grant(resources.toTypedArray())
                    else -> request.deny()
                }
            }
        }

        override fun onShowFileChooser(
            webView: WebView,
            filePathCallback: ValueCallback<Array<Uri>>,
            fileChooserParams: FileChooserParams,
        ): Boolean {
            host.pickFiles(fileChooserParams) { filePathCallback.onReceiveValue(it) }
            return true
        }
    }

    private companion object {
        val MEDIA_PERMISSIONS = mapOf(
            PermissionRequest.RESOURCE_VIDEO_CAPTURE to Manifest.permission.CAMERA,
            PermissionRequest.RESOURCE_AUDIO_CAPTURE to Manifest.permission.RECORD_AUDIO,
        )
    }
}
