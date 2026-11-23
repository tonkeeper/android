package com.tonapps.tonkeeper.ui.screen.browser.dapp

import android.net.Uri
import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.bus.core.contract.UtmParameters
import com.tonapps.tonkeeper.manager.earn.EarnUrlResolver
import com.tonapps.tonkeeper.ui.screen.browser.analytics.DappOpenAnalytics
import com.tonapps.tonkeeper.ui.screen.browser.dapp.earn.EarnDAppScreen
import com.tonapps.tonkeeper.ui.screen.browser.dapp.keeper.KeeperDAppScreen
import com.tonapps.tonkeeper.ui.screen.browser.dapp.keeper.isKeeperDApp
import com.tonapps.wallet.data.dapps.entities.AppEntity

// Every dApp screen is made here: the URL alone decides which one opens, whatever the entry point, as on iOS.
class DAppScreenFactory(private val earnUrlResolver: EarnUrlResolver) {

    fun create(
        wallet: WalletEntity,
        title: String,
        url: Uri,
        iconUrl: String,
        source: String,
        forceConnect: Boolean = false,
        analytics: DappOpenAnalytics? = null,
        utm: UtmParameters = UtmParameters.Empty,
    ): DAppScreen {
        return create(wallet, DAppArgs(title, url, source, iconUrl, forceConnect, analytics, utm))
    }

    fun create(
        wallet: WalletEntity,
        app: AppEntity,
        source: String,
        forceConnect: Boolean = false,
        analytics: DappOpenAnalytics? = null,
    ): DAppScreen {
        return create(wallet, app.name, app.url, app.iconUrl, source, forceConnect, analytics)
    }

    fun hasOwnScreen(url: Uri): Boolean = ownScreen(url) != null

    private fun create(wallet: WalletEntity, args: DAppArgs): DAppScreen {
        val screen = ownScreen(args.url)?.invoke(wallet) ?: DAppScreen(wallet)
        screen.setArgs(args)
        return screen
    }

    private fun ownScreen(url: Uri): ((WalletEntity) -> DAppScreen)? = when {
        earnUrlResolver.isEarn(url) -> ::EarnDAppScreen
        url.isKeeperDApp() -> ::KeeperDAppScreen
        else -> null
    }
}