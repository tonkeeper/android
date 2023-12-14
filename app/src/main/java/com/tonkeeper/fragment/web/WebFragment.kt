package com.tonkeeper.fragment.web

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.widget.ContentLoadingProgressBar
import com.tonkeeper.R
import uikit.base.BaseFragment

class WebFragment: BaseFragment(R.layout.fragment_web) {

    companion object {

        private const val URL_KEY = "url"

        fun newInstance(url: String): WebFragment {
            val fragment = WebFragment()
            fragment.arguments = Bundle().apply {
                putString(URL_KEY, url)
            }
            return fragment
        }
    }

    private val startUrl: String by lazy {
        arguments?.getString(URL_KEY) ?: ""
    }

    private lateinit var backView: View
    private lateinit var titleView: AppCompatTextView
    private lateinit var subtitleView: AppCompatTextView
    private lateinit var progressBar: ContentLoadingProgressBar
    private lateinit var webView: WebView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        backView = view.findViewById(R.id.back)
        backView.setOnClickListener { onBackPressed() }

        titleView = view.findViewById(R.id.title)

        subtitleView = view.findViewById(R.id.subtitle)

        progressBar = view.findViewById(R.id.progress_bar)

        webView = view.findViewById(R.id.web_view)
        webView.settings.javaScriptEnabled = true
        webView.settings.loadWithOverviewMode = true
        webView.settings.useWideViewPort = true
        webView.webViewClient = object : WebViewClient() {

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val uri = request.url
                if (uri.scheme == "https") {
                    return false
                }
                openExternal(uri)
                return true
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                subtitleView.text = Uri.parse(url).host
                checkBack()
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
    }

    private fun openExternal(uri: Uri) {
        val intent = Intent(Intent.ACTION_VIEW, uri)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
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

}