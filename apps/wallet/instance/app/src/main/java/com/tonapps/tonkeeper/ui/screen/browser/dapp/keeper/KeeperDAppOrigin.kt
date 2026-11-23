package com.tonapps.tonkeeper.ui.screen.browser.dapp.keeper

import android.net.Uri
import com.tonapps.core.loyalty.LOYALTY_HOST

private const val KEEPER_DAPP_HOST_SUFFIX = ".keeperwallet.com"
private const val HTTPS_DEFAULT_PORT = 443

internal val keeperDAppOriginRules: Set<String> = setOf("https://*$KEEPER_DAPP_HOST_SUFFIX")

internal fun isKeeperDAppHost(host: String?): Boolean {
    return host != null && host.lowercase().endsWith(KEEPER_DAPP_HOST_SUFFIX)
}

internal fun Uri.isKeeperDApp(): Boolean {
    return scheme.equals("https", ignoreCase = true) &&
        (port == -1 || port == HTTPS_DEFAULT_PORT) &&
        isKeeperDAppHost(host)
}

internal fun Uri.isLoyaltyPage(): Boolean {
    return isKeeperDApp() && host.equals(LOYALTY_HOST, ignoreCase = true)
}
