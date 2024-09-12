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
import com.tonapps.tonkeeper.koin.walletViewModel
import com.tonapps.tonkeeper.ui.base.BaseWalletScreen
import com.tonapps.tonkeeper.ui.base.BaseWalletVM
import com.tonapps.tonkeeper.ui.base.ScreenContext
import com.tonapps.tonkeeper.ui.screen.purchase.main.PurchaseScreen
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.purchase.PurchaseRepository
import com.tonapps.wallet.data.purchase.entity.PurchaseMethodEntity
import com.tonapps.wallet.data.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.parameter.parametersOf
import uikit.base.BaseFragment
import uikit.navigation.Navigation.Companion.navigation
import uikit.widget.HeaderView
import uikit.widget.LoaderView
import uikit.widget.webview.WebViewFixed

class PurchaseWebScreen(wallet: WalletEntity): BaseWalletScreen<ScreenContext.Wallet>(R.layout.fragment_purchase_web, ScreenContext.Wallet(wallet)) {

    override val viewModel: PurchaseWebViewModel by walletViewModel()

    private val method: PurchaseMethodEntity by lazy {
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
        webView.loadUrl(viewModel.replaceUrl(method.actionButton.url))
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

        fun newInstance(wallet: WalletEntity, method: PurchaseMethodEntity): PurchaseWebScreen {
            val fragment = PurchaseWebScreen(wallet)
            fragment.putParcelableArg(METHOD_KEY, method)
            return fragment
        }
    }
}