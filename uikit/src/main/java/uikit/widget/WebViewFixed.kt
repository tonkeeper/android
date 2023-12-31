package uikit.widget

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.webkit.WebSettings
import android.webkit.WebView
import java.io.File

class WebViewFixed @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = android.R.attr.webViewStyle,
) : WebView(context, attrs, defStyle) {

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)

        settings.allowContentAccess = true
        settings.allowFileAccess = true
        settings.cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK

        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.setSupportMultipleWindows(true)

        setBackgroundColor(Color.TRANSPARENT)
    }
}


