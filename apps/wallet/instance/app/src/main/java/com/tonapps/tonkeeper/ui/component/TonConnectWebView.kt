package com.tonapps.tonkeeper.ui.component

import android.content.Context
import android.util.AttributeSet
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import androidx.webkit.WebViewCompat
import com.tonapps.wallet.data.account.entities.WalletEntity
import uikit.widget.webview.bridge.BridgeWebView
import java.io.File

class TonConnectWebView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = android.R.attr.webViewStyle,
) : BridgeWebView(context, attrs, defStyle) {


    fun setWallet(wallet: WalletEntity) {

    }

}