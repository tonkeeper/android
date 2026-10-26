package com.tonapps.core.deeplink

import androidx.core.net.toUri

fun String.withPerpsBannerEntry(): String {
    val uri = toUri()
    if (DeepLinkRoute.resolve(uri) !is DeepLinkRoute.Perps) {
        return this
    }
    if (uri.getQueryParameter(DeepLinkRoute.Perps.ENTRY_POINT_QUERY) != null) {
        return this
    }
    return uri.buildUpon()
        .appendQueryParameter(DeepLinkRoute.Perps.ENTRY_POINT_QUERY, DeepLinkRoute.Perps.MAIN_BANNER)
        .build()
        .toString()
}
