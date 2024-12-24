package com.tonapps.tonkeeper.ui.component

import android.content.Context
import android.util.AttributeSet
import uikit.widget.webview.bridge.BridgeWebView

class TonConnectWebView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = android.R.attr.webViewStyle,
) : BridgeWebView(context, attrs, defStyle) {

    init {
        isVerticalScrollBarEnabled = false
    }
}