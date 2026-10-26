package com.tonapps.tonkeeper.ui.screen.browser.analytics

import android.net.Uri
import com.tonapps.bus.core.AnalyticsHelper
import com.tonapps.bus.core.contract.UtmParameters
import com.tonapps.bus.generated.Events
import com.tonapps.bus.generated.Events.DappBrowser.DappAppClickFrom
import com.tonapps.bus.generated.Events.DappBrowser.DappBrowserAssetChain
import com.tonapps.bus.generated.Events.DappBrowser.DappBrowserOpenFrom
import com.tonapps.bus.generated.Events.DappBrowser.DappBrowserType
import com.tonapps.wallet.data.browser.entities.BrowserAppEntity

object DappBrowserAnalytics {

    const val WALLET_TAB_SOURCE = "wallet"
    const val IN_DAPP_LINK_SOURCE = "dapp"
    const val SHORTCUT_DAPP_SOURCE = "shortcut"
    const val EXTERNAL_LINK_SOURCE = "external_link"

    private const val UNKNOWN_LOCATION = "ZZ"
    private const val UNKNOWN_DOMAIN = "unknown"

    internal val events: Events.DappBrowser
        get() = AnalyticsHelper.Default.events.dappBrowser

    internal fun eventsTaggedWith(utm: UtmParameters): Events.DappBrowser {
        return AnalyticsHelper.Default.eventsTaggedWith(utm).dappBrowser
    }

    fun browserOpen(
        from: String,
        type: DappBrowserType,
        country: String?,
        utm: UtmParameters = UtmParameters.Empty,
    ) {
        eventsTaggedWith(utm).dappBrowserOpen(
            from = openFrom(from),
            type = type,
            location = location(country)
        )
    }

    fun tabClick(type: DappBrowserType, country: String?) {
        events.dappBrowserTabClick(
            type = type,
            location = location(country)
        )
    }

    fun searchOpen(country: String?) {
        events.dappBrowserSearchOpen(
            url = UNKNOWN_DOMAIN,
            location = location(country)
        )
    }

    fun searchClick(url: Uri?, country: String?) {
        events.dappBrowserSearchClick(
            url = domain(url),
            location = location(country)
        )
    }

    fun catalogContext(
        source: String,
        app: BrowserAppEntity,
        fallbackChain: DappBrowserAssetChain,
        country: String?,
    ): DappOpenAnalytics {
        val from = appClickFrom(source)
        val urlDomain = domain(app.url)
        val bannerId = app.bannerId?.trim()?.takeIf { it.isNotEmpty() }
        return DappOpenAnalytics(
            from = from,
            url = urlDomain,
            assetChain = assetChain(app.chains, fallbackChain),
            appId = app.id.trim().ifEmpty { urlDomain },
            bannerId = if (from == DappAppClickFrom.Banner) {
                bannerId
            } else {
                null
            },
            location = location(country)
        )
    }

    fun directContext(
        source: String,
        url: Uri,
        chain: String? = null,
        country: String?,
        utm: UtmParameters = UtmParameters.Empty,
    ): DappOpenAnalytics {
        val from = appClickFrom(source)
        val urlDomain = domain(url)

        return DappOpenAnalytics(
            from = from,
            url = urlDomain,
            assetChain = assetChain(chain) ?: DappBrowserAssetChain.Multichain,
            appId = urlDomain,
            bannerId = null,
            location = location(country),
            utm = utm,
        )
    }

    private fun openFrom(source: String): DappBrowserOpenFrom {
        return when (source.lowercase()) {
            WALLET_TAB_SOURCE -> DappBrowserOpenFrom.Wallet
            "story", "stories" -> DappBrowserOpenFrom.Story
            "history", "activity" -> DappBrowserOpenFrom.History
            "connected_apps" -> DappBrowserOpenFrom.ConnectedApps
            EXTERNAL_LINK_SOURCE -> DappBrowserOpenFrom.DeepLink
            else -> DappBrowserOpenFrom.Other
        }
    }

    private fun appClickFrom(source: String): DappAppClickFrom {
        return when (source.lowercase()) {
            "banner" -> DappAppClickFrom.Banner
            "browser", "browser_all", "ads" -> DappAppClickFrom.Browser
            "browser_search", "browser_search_direct" -> DappAppClickFrom.BrowserSearch
            "browser_connected" -> DappAppClickFrom.BrowserConnected
            "push" -> DappAppClickFrom.Push
            "sidebar" -> DappAppClickFrom.Sidebar
            "deep-link", "deep_link", "deeplink" -> DappAppClickFrom.DeepLink
            "history", "activity" -> DappAppClickFrom.History
            "collectibles", "nft" -> DappAppClickFrom.Collectibles
            "earn_featured" -> DappAppClickFrom.EarnFeatured
            "earn_your_vaults" -> DappAppClickFrom.EarnYourVaults
            "earn_apy" -> DappAppClickFrom.EarnApy
            IN_DAPP_LINK_SOURCE -> DappAppClickFrom.Dapp
            SHORTCUT_DAPP_SOURCE -> DappAppClickFrom.Shortcut
            else -> DappAppClickFrom.Other
        }
    }

    private fun assetChain(
        chains: List<String>,
        fallback: DappBrowserAssetChain
    ): DappBrowserAssetChain {
        if (chains.isEmpty()) {
            return fallback
        }
        val chain = chains.singleOrNull() ?: return DappBrowserAssetChain.Multichain
        return assetChain(chain) ?: fallback
    }

    private fun assetChain(chain: String?): DappBrowserAssetChain? {
        return when (chain?.trim()?.lowercase()) {
            "ton" -> DappBrowserAssetChain.Ton
            "eth", "ethereum" -> DappBrowserAssetChain.Eth
            "base" -> DappBrowserAssetChain.Base
            "arb", "arbitrum" -> DappBrowserAssetChain.Arb
            "bsc", "bnb" -> DappBrowserAssetChain.Bnb
            "pol", "polygon", "matic" -> DappBrowserAssetChain.Pol
            "sol", "solana" -> DappBrowserAssetChain.Sol
            "tron" -> DappBrowserAssetChain.Tron
            "btc", "bitcoin" -> DappBrowserAssetChain.Btc
            "ltc", "litecoin" -> DappBrowserAssetChain.Ltc
            "doge", "dogecoin" -> DappBrowserAssetChain.Doge
            "bch" -> DappBrowserAssetChain.Bch
            else -> null
        }
    }

    private fun location(country: String?): String {
        val value = country?.trim()?.uppercase() ?: return UNKNOWN_LOCATION
        if (value.length != 2) {
            return UNKNOWN_LOCATION
        }
        return value
    }

    private fun domain(url: Uri?): String {
        val host = url?.host?.trim()?.lowercase()
        if (host.isNullOrEmpty()) {
            return UNKNOWN_DOMAIN
        }
        return host
    }
}
