package uikit.widget

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.webkit.WebView

class WebViewFixed @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = android.R.attr.webViewStyle,
) : WebView(context, attrs, defStyle) {

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
        settings.allowContentAccess = true
        settings.allowFileAccess = true
        settings.cacheMode = android.webkit.WebSettings.LOAD_CACHE_ELSE_NETWORK
        settings.domStorageEnabled = true
        settings.javaScriptEnabled = true
        setBackgroundColor(Color.TRANSPARENT)
    }
}