package com.tonapps.tonkeeper.ui.screen.browser.dapp.earn

import android.content.res.Configuration
import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.bus.generated.Events.EarnFlow.EarnFlowExitMethod
import com.tonapps.extensions.lazyUnsafe
import com.tonapps.tonkeeper.koin.walletViewModel
import com.tonapps.tonkeeper.manager.earn.EarnUrlResolver
import com.tonapps.tonkeeper.ui.component.TonConnectWebView
import com.tonapps.tonkeeper.ui.screen.browser.dapp.inapp.InAppBridgeContract.Effect
import com.tonapps.tonkeeper.ui.screen.browser.dapp.inapp.InAppDAppScreen
import com.tonapps.wallet.api.API
import org.koin.android.ext.android.inject
import org.koin.core.parameter.parametersOf

internal class EarnDAppScreen(wallet: WalletEntity) : InAppDAppScreen(wallet) {

    override val fragmentName: String = "EarnDAppScreen"

    override val dappBridges: EarnDAppViewModel by walletViewModel {
        parametersOf(dappAnalytics.from)
    }

    private val earnUrlResolver: EarnUrlResolver by inject()

    private val api: API by inject()

    override val originRules: Set<String> by lazyUnsafe {
        earnUrlResolver.originRules(startUri)
    }

    private var exitMethod = EarnFlowExitMethod.Other

    override fun onWebViewCreated(webView: TonConnectWebView) {
        super.onWebViewCreated(webView)
        applyUserAgent(webView)
    }

    // Every theme change recreates the activity today, and the new document gets the theme in its
    // config; these only matter if this screen ever outlives one.
    override fun onResume() {
        super.onResume()
        dappBridges.onThemeChanged()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        dappBridges.onThemeChanged()
    }

    // Earn is told which app it runs in; the token goes after the default agent so the page keeps
    // seeing a browser it recognises.
    private fun applyUserAgent(webView: TonConnectWebView) {
        val agent = api.userAgent
        val current = webView.settings.userAgentString.orEmpty()
        if (!current.contains(agent)) {
            webView.settings.userAgentString = "$current $agent".trim()
        }
    }

    override fun onEffect(effect: Effect) {
        if (effect is Effect.Close) {
            exitMethod = EarnFlowExitMethod.Page
        }
        super.onEffect(effect)
    }

    override fun onCloseClick() {
        exitMethod = EarnFlowExitMethod.CloseButton
        super.onCloseClick()
    }

    override fun onBackPressed(): Boolean {
        if (!webView.canGoBack()) {
            exitMethod = EarnFlowExitMethod.SystemBack
        }
        return super.onBackPressed()
    }

    override fun onDestroy() {
        val duration = dappAnalytics.sinceClickMs()
        if (isRemoving && duration != null) {
            dappBridges.analytics.closed(exitMethod, duration)
        }
        super.onDestroy()
    }
}