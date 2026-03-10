package com.tonapps.tonkeeper.ui.screen.swap

import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import com.tonapps.extensions.appVersionName
import com.tonapps.extensions.locale
import com.tonapps.tonkeeper.helper.BrowserHelper
import com.tonapps.tonkeeper.ui.base.BaseWalletVM
import com.tonapps.tonkeeper.ui.base.WalletContextScreen
import com.tonapps.tonkeeper.ui.screen.send.transaction.SendTransactionScreen
import com.tonapps.tonkeeper.ui.screen.swap.omniston.OmnistonScreen
import com.tonapps.tonkeeperx.R
import com.tonapps.wallet.api.entity.TokenEntity
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.core.entity.SignRequestEntity
import com.tonapps.wallet.data.settings.BatteryTransaction
import com.tonapps.wallet.data.settings.SettingsRepository
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import uikit.base.BaseFragment
import uikit.extensions.applyNavBottomPadding
import uikit.extensions.getDimensionPixelSize
import uikit.widget.webview.WebViewFixed
import uikit.widget.webview.bridge.BridgeWebView
import androidx.core.view.isGone
import com.google.firebase.Firebase
import com.google.firebase.perf.performance
import com.tonapps.blockchain.ton.extensions.equalsAddress
import com.tonapps.wallet.data.core.currency.WalletCurrency
import org.ton.contract.wallet.WalletMessage

class SwapScreen(wallet: WalletEntity): WalletContextScreen(R.layout.fragment_swap, wallet), BaseFragment.BottomSheet {

    override val fragmentName: String = "SwapScreen"

    private val args: SwapArgs by lazy { SwapArgs(requireArguments()) }

    override val viewModel: BaseWalletVM.EmptyViewViewModel by viewModel()

    private val settingsRepository: SettingsRepository by inject()

    private lateinit var closeView: View
    private lateinit var webView: BridgeWebView

    private val webViewCallback = object : WebViewFixed.Callback() {

        private val swapTrace = Firebase.performance.newTrace("swap_webview")
        private var isAlreadySendTrace = false

        override fun onPageFinished(url: String) {
            super.onPageFinished(url)
            if (!isAlreadySendTrace) {
                swapTrace.stop()
                isAlreadySendTrace = true
            }
            hideCloseView()
        }

        override fun onPageStarted(url: String, favicon: Bitmap?) {
            super.onPageStarted(url, favicon)
            if (!isAlreadySendTrace) {
                swapTrace.start()
            }
        }

        override fun onNewTab(url: String) {
            super.onNewTab(url)
            BrowserHelper.open(requireContext(), url)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        analytics?.swapOpen(args.uri, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        closeView = view.findViewById(R.id.close)
        closeView.setOnClickListener { finish() }

        webView = view.findViewById(R.id.web)
        webView.addCallback(webViewCallback)
        webView.clipToPadding = false
        webView.applyNavBottomPadding(requireContext().getDimensionPixelSize(uikit.R.dimen.offsetMedium))
        webView.loadUrl(getUri().toString())
        webView.jsBridge = StonfiBridge2(
            address = args.address,
            close = ::finish,
            sendTransaction = ::sing
        )

        ViewCompat.setOnApplyWindowInsetsListener(webView) { _, insets ->
            val navInsets = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            webView.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = navInsets.bottom
            }
            insets
        }
    }

    private fun hideCloseView() {
        if (closeView.isGone) {
            return
        }
        closeView.postDelayed({
            closeView.visibility = View.GONE
        }, 1000)
    }

    private fun getUri(): Uri {
        val builder = args.uri.buildUpon()
        builder.appendQueryParameter("clientVersion", requireContext().appVersionName)
        builder.appendQueryParameter("ft", args.fromToken)
        builder.appendQueryParameter("lang", requireContext().locale.toString())
        args.toToken?.let {
            builder.appendQueryParameter("tt", it)
        }
        val theme = settingsRepository.theme
        builder.appendQueryParameter("theme", if (theme.isSystem) "dark" else theme.key)
        return builder.build()
    }

    private suspend fun sing(
        request: SignRequestEntity
    ): String {
        analytics?.simpleTrackEvent("swap_click")
        return try {
            val boc = SendTransactionScreen.run(requireContext(), wallet, request, BatteryTransaction.SWAP)
            if (boc.isNotBlank()) {
                analytics?.simpleTrackEvent("swap_success")
            }
            boc
        } catch (e: Throwable) {
            ""
        }
    }

    override fun onDestroyView() {
        webView.removeCallback(webViewCallback)
        webView.destroy()
        super.onDestroyView()
    }

    companion object {

        fun bestToToken(fromToken: String): WalletCurrency {
            if (fromToken.equalsAddress(WalletCurrency.USDE_TON_ETHENA_ADDRESS)) {
                return WalletCurrency.USDT_TON
            } else if (fromToken.equalsAddress(WalletCurrency.TS_USDE_TON_ETHENA_ADDRESS)) {
                return WalletCurrency.USDE_TON_ETHENA
            }
            return WalletCurrency.TON
        }

        fun newInstance(
            wallet: WalletEntity,
            fromToken: WalletCurrency = WalletCurrency.TON,
            toToken: WalletCurrency? = null,
            nativeSwap: Boolean,
            uri: Uri,
        ): BaseFragment {
            if (nativeSwap) {
                return OmnistonScreen.newInstance(
                    wallet = wallet,
                    fromToken = fromToken,
                    toToken = toToken ?: bestToToken(fromToken.address)
                )
            }
            val screen = SwapScreen(wallet)
            screen.setArgs(SwapArgs(
                uri = uri,
                address = wallet.address,
                fromToken = fromToken.address,
                toToken = (toToken ?: bestToToken(fromToken.address)).address
            ))
            return screen
        }
    }
}