package com.tonapps.tonkeeper.ui.screen.purchase.web

import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import android.webkit.WebView
import androidx.lifecycle.lifecycleScope
import com.tonapps.extensions.getParcelableCompat
import com.tonapps.tonkeeperx.R
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.tonkeeper.core.AnalyticsHelper
import com.tonapps.wallet.data.purchase.PurchaseRepository
import com.tonapps.wallet.data.purchase.entity.PurchaseMethodEntity
import com.tonapps.wallet.data.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.koin.android.ext.android.inject
import uikit.base.BaseFragment
import uikit.widget.HeaderView
import uikit.widget.LoaderView
import uikit.widget.webview.WebViewFixed

class PurchaseWebScreen: BaseFragment(R.layout.fragment_purchase_web) {

    private val accountRepository: AccountRepository by inject()
    private val settingsRepository: SettingsRepository by inject()
    private val purchaseRepository: PurchaseRepository by inject()

    private val method: PurchaseMethodEntity by lazy {
        requireArguments().getParcelableCompat(METHOD_KEY)!!
    }

    private lateinit var headerView: HeaderView
    private lateinit var loaderView: LoaderView
    private lateinit var webView: WebViewFixed

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        headerView = view.findViewById(R.id.header)
        headerView.doOnActionClick = { finish() }

        loaderView = view.findViewById(R.id.loader)

        webView = view.findViewById(R.id.web)
        webView.webChromeClient = object : android.webkit.WebChromeClient() {

        }
        webView.webViewClient = object : android.webkit.WebViewClient() {

            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                view.visibility = View.VISIBLE
                loaderView.visibility = View.GONE
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                if (url?.matches(".*#endsession".toRegex()) == true) {
                    finish()
                    return
                }

                val successUrlPattern = method.successUrlPattern?.pattern ?: return
                val regexp = Regex(successUrlPattern, RegexOption.IGNORE_CASE)

                regexp.find(url ?: "")?.groupValues ?: return
                AnalyticsHelper.trackEvent("buy_crypto")
                finish()
            }
        }

        loadUrl()
    }

    private fun loadUrl() {
        combine(
            settingsRepository.countryFlow,
            accountRepository.selectedWalletFlow
        ) { country, wallet ->
            purchaseRepository.replaceUrl(method.actionButton.url, wallet.address, country)
        }.flowOn(Dispatchers.IO).onEach(::loadUrl).flowOn(Dispatchers.Main).launchIn(lifecycleScope)
    }

    private fun loadUrl(url: String) {
        webView.loadUrl(url)
    }

    override fun onPause() {
        super.onPause()
        webView.onPause()
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        webView.destroy()
    }

    companion object {
        private const val METHOD_KEY = "method"

        fun newInstance(method: PurchaseMethodEntity): PurchaseWebScreen {
            val fragment = PurchaseWebScreen()
            fragment.arguments = Bundle().apply {
                putParcelable(METHOD_KEY, method)
            }
            return fragment
        }
    }
}