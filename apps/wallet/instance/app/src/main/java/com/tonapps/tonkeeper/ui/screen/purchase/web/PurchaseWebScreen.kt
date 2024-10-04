package com.tonapps.tonkeeper.ui.screen.purchase.web

import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import com.tonapps.extensions.activity
import com.tonapps.extensions.getParcelableCompat
import com.tonapps.tonkeeper.core.AnalyticsHelper
import com.tonapps.tonkeeper.helper.BrowserHelper
import com.tonapps.tonkeeper.core.entities.WalletPurchaseMethodEntity
import com.tonapps.tonkeeper.ui.base.BaseWalletVM
import com.tonapps.tonkeeper.ui.base.WalletContextScreen
import com.tonapps.tonkeeper.ui.screen.purchase.main.PurchaseScreen
import com.tonapps.tonkeeperx.R
import com.tonapps.wallet.data.account.entities.WalletEntity
import org.koin.androidx.viewmodel.ext.android.viewModel
import uikit.navigation.NavigationActivity
import uikit.widget.HeaderView
import uikit.widget.LoaderView
import uikit.widget.webview.WebViewFixed


class PurchaseWebScreen(wallet: WalletEntity): WalletContextScreen(R.layout.fragment_purchase_web, wallet) {

    override val viewModel: BaseWalletVM.EmptyViewViewModel by viewModel()

    private val method: WalletPurchaseMethodEntity by lazy {
        requireArguments().getParcelableCompat(METHOD_KEY)!!
    }

    private lateinit var headerView: HeaderView
    private lateinit var loaderView: LoaderView
    private lateinit var webView: WebViewFixed

    private val webViewCallback = object : WebViewFixed.Callback() {

        override fun onPageFinished(url: String) {
            super.onPageFinished(url)
            webView.visibility = View.VISIBLE
            loaderView.visibility = View.GONE
        }

        override fun onPageStarted(url: String, favicon: Bitmap?) {
            super.onPageStarted(url, favicon)
            if (url.matches(".*#endsession".toRegex())) {
                finish()
                return
            }

            val successUrlPattern = method.successUrlPattern?.pattern ?: return
            val regexp = Regex(successUrlPattern, RegexOption.IGNORE_CASE)

            regexp.find(url)?.groupValues ?: return
            AnalyticsHelper.trackEvent("buy_crypto")
            finish()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        headerView = view.findViewById(R.id.header)
        headerView.doOnActionClick = { finish() }

        loaderView = view.findViewById(R.id.loader)

        webView = view.findViewById(R.id.web)
        webView.addCallback(webViewCallback)
        webView.loadUrl(method.uri)
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
        navigation?.add(PurchaseScreen.newInstance(screenContext.wallet))
        webView.removeCallback(webViewCallback)
        webView.destroy()
    }

    companion object {
        private const val METHOD_KEY = "method"

        fun open(context: Context, method: WalletPurchaseMethodEntity) {
            val activity = context.activity ?: throw IllegalStateException("Activity not found")
            open(activity, method)
        }

        fun open(activity: NavigationActivity, method: WalletPurchaseMethodEntity) {
            if (method.useCustomTabs) {
                BrowserHelper.open(activity, method.uri)
            } else {
                val fragment = PurchaseWebScreen(method.wallet)
                fragment.putParcelableArg(METHOD_KEY, method)
                activity.add(fragment)
            }
        }
    }
}