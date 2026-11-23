package com.tonapps.tonkeeper.manager.push

import com.tonapps.core.deeplink.DeepLinkRoute

private const val TYPE_KEY = "type"
private const val KIND_KEY = "kind"
private const val PERPS_TYPE_PREFIX = "perps_"
private const val LOYALTY_KIND_PREFIX = "loyalty_"

internal object PushDisplayFilter {

    fun isPerpsOrLoyalty(route: DeepLinkRoute?, data: (String) -> String?): Boolean {
        return route is DeepLinkRoute.Perps ||
            data(TYPE_KEY)?.startsWith(PERPS_TYPE_PREFIX) == true ||
            data(KIND_KEY)?.startsWith(LOYALTY_KIND_PREFIX) == true
    }
}
