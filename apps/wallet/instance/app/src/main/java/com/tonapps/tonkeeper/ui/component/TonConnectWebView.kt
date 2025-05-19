package com.tonapps.tonkeeper.ui.component

import android.content.Context
import android.net.Uri
import android.util.AttributeSet
import android.util.Log
import com.tonapps.extensions.toUriOrNull
import uikit.widget.webview.bridge.BridgeWebView

class TonConnectWebView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = android.R.attr.webViewStyle,
) : BridgeWebView(context, attrs, defStyle) {

    val uri: Uri?
        get() = url?.toUriOrNull()

    init {
        isVerticalScrollBarEnabled = false
        settings.setSupportZoom(false)
    }
}