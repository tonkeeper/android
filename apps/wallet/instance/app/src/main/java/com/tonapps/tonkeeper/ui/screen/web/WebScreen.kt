package com.tonapps.tonkeeper.ui.screen.web

import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.widget.ContentLoadingProgressBar
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.tonapps.tonkeeper.core.deeplink.DeepLink
import com.tonapps.tonkeeperx.R
import com.tonapps.uikit.color.tabBarActiveIconColor
import uikit.base.BaseFragment
import uikit.widget.webview.WebViewFixed

class WebScreen: BaseFragment(R.layout.fragment_web) {

    private val startUrl: String by lazy {
        arguments?.getString(URL_KEY) ?: ""
    }

    private lateinit var backView: View
    private lateinit var closeView: View
    private lateinit var titleView: AppCompatTextView
    private lateinit var subtitleView: AppCompatTextView
    private lateinit var progressBar: ContentLoadingProgressBar
    private lateinit var refreshView: SwipeRefreshLayout
    private lateinit var webView: WebViewFixed

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        backView = view.findViewById(R.id.back)
        backView.setOnClickListener { onBackPressed() }

        closeView = view.findViewById(R.id.close)
        closeView.setOnClickListener { finish() }

        titleView = view.findViewById(R.id.title)

        subtitleView = view.findViewById(R.id.subtitle)

        progressBar = view.findViewById(R.id.progress_bar)

        refreshView = view.findViewById(R.id.refresh)
        refreshView.setColorSchemeColors(requireContext().tabBarActiveIconColor)

        webView = view.findViewById(R.id.web_view)
        webView.webViewClient = object : WebViewClient() {

            override fun onLoadResource(view: WebView?, url: String?) {
                if (DeepLink.isSupportedUrl(url)) {
                    return
                }
                super.onLoadResource(view, url)
            }

            /*override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val uri = request.url
                return DeepLink.openUri(requireContext(), uri)
            }*/

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                refreshView.isRefreshing = true
                subtitleView.text = Uri.parse(url).host
                checkBack()
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                refreshView.isRefreshing = false
            }

        }
        webView.webChromeClient = object : WebChromeClient() {
            override fun onReceivedTitle(view: WebView?, title: String?) {
                titleView.text = title
            }

            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
                progressBar.progress = newProgress.coerceIn(0, 100)
            }
        }
        webView.loadUrl(startUrl)

        refreshView.setOnRefreshListener {
            webView.reload()
        }
    }

    private fun checkBack() {
        if (webView.canGoBack()) {
            backView.isClickable = true
            backView.alpha = 1f
        } else {
            backView.isClickable = false
            backView.alpha = 0f
        }
    }

    override fun onBackPressed(): Boolean {
        return if (webView.canGoBack()) {
            webView.goBack()
            false
        } else {
            true
        }
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
    }

    override fun onPause() {
        super.onPause()
        webView.onPause()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        webView.destroy()
    }

    companion object {

        private const val URL_KEY = "url"

        fun newInstance(url: String): WebScreen {
            val fragment = WebScreen()
            fragment.arguments = Bundle().apply {
                putString(URL_KEY, url)
            }
            return fragment
        }

        fun newInstance(uri: Uri): WebScreen {
            return newInstance(uri.toString())
        }
    }
}