package com.tonapps.core.deeplink

import android.net.Uri
import com.tonapps.bus.core.AnalyticsHelper
import com.tonapps.bus.generated.Events.DeeplinkOpen.DeeplinkOpenFrom
import com.tonapps.bus.generated.Events.DeeplinkOpen.DeeplinkOpenLinkType
import com.tonapps.extensions.hostOrNull
import com.tonapps.extensions.query

object DeeplinkOpenAnalytics {

    private val DeepLinkRoute.WalletConnect.isWakeUp: Boolean
        get() {
            val link = uri.toString()
            val pairingUri = if (link.startsWith("wc:", ignoreCase = true)) {
                link
            } else {
                uri.query("uri")
            }
            return pairingUri == null || !isWalletConnectPairing(pairingUri)
        }

    fun report(link: Uri, isColdStart: Boolean) {
        val deeplink = DeepLink(link, fromQR = false, referrer = null, fromExternal = true)
        val route = deeplink.route
        if (route is DeepLinkRoute.WalletConnect && route.isWakeUp) {
            return
        }
        AnalyticsHelper.Default.eventsTaggedWith(deeplink.utm).deeplinkOpen.deeplinkOpen(
            from = from(link.toString()),
            linkType = linkType(route),
            isColdStart = isColdStart,
        )
    }

    fun isWalletConnectPairing(uri: String): Boolean {
        return uri.substringAfter('?', missingDelimiterValue = "").split('&').any { parameter ->
            parameter.substringBefore('=').equals("symKey", ignoreCase = true) &&
                parameter.substringAfter('=', missingDelimiterValue = "").isNotEmpty()
        }
    }

    fun from(link: String): DeeplinkOpenFrom {
        val scheme = link.substringBefore(':').lowercase()
        return if (scheme == "https" || scheme == "http") {
            DeeplinkOpenFrom.UniversalLink
        } else {
            DeeplinkOpenFrom.AppLink
        }
    }

    fun linkType(route: DeepLinkRoute): DeeplinkOpenLinkType {
        return when (route) {
            is DeepLinkRoute.Transfer,
            is DeepLinkRoute.EvmTransfer,
            is DeepLinkRoute.SolanaTransfer,
            is DeepLinkRoute.Send -> DeeplinkOpenLinkType.Transfer
            DeepLinkRoute.Staking -> DeeplinkOpenLinkType.Staking
            is DeepLinkRoute.StakingPool -> DeeplinkOpenLinkType.Pool
            is DeepLinkRoute.Swap -> DeeplinkOpenLinkType.Swap
            is DeepLinkRoute.Deposit,
            DeepLinkRoute.Purchase,
            is DeepLinkRoute.Exchange -> DeeplinkOpenLinkType.Deposit
            is DeepLinkRoute.Withdraw -> DeeplinkOpenLinkType.Withdraw
            is DeepLinkRoute.AccountEvent -> DeeplinkOpenLinkType.Action
            is DeepLinkRoute.Signer -> DeeplinkOpenLinkType.ExternalSign
            is DeepLinkRoute.TonConnect -> DeeplinkOpenLinkType.Tonconnect
            is DeepLinkRoute.WalletConnect -> DeeplinkOpenLinkType.WalletConnect
            is DeepLinkRoute.DApp -> DeeplinkOpenLinkType.Dapp
            is DeepLinkRoute.Battery -> DeeplinkOpenLinkType.Battery
            is DeepLinkRoute.Tabs.Browser -> DeeplinkOpenLinkType.Browser
            is DeepLinkRoute.Migrate -> DeeplinkOpenLinkType.Migration
            is DeepLinkRoute.Tabs.Trading -> DeeplinkOpenLinkType.Trading
            is DeepLinkRoute.Asset -> DeeplinkOpenLinkType.TradeAsset
            is DeepLinkRoute.Story -> DeeplinkOpenLinkType.Story
            DeepLinkRoute.Receive -> DeeplinkOpenLinkType.Receive
            DeepLinkRoute.Backups -> DeeplinkOpenLinkType.Backup
            is DeepLinkRoute.AddWallet -> DeeplinkOpenLinkType.AddWallet
            is DeepLinkRoute.Tabs.Main -> DeeplinkOpenLinkType.Main
            is DeepLinkRoute.Raffle -> DeeplinkOpenLinkType.Raffle
            is DeepLinkRoute.Unknown -> if (route.uri.hostOrNull == null) {
                DeeplinkOpenLinkType.Main
            } else {
                DeeplinkOpenLinkType.Unknown
            }
            is DeepLinkRoute.Tabs.Activity,
            is DeepLinkRoute.Tabs.Collectibles,
            is DeepLinkRoute.Internal,
            DeepLinkRoute.Camera,
            DeepLinkRoute.DnsRenew,
            is DeepLinkRoute.PickWallet,
            is DeepLinkRoute.Perps,
            is DeepLinkRoute.Jetton -> DeeplinkOpenLinkType.Unknown
        }
    }
}
