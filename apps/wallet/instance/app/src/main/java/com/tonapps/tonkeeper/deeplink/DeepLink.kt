package com.tonapps.tonkeeper.deeplink

import android.net.Uri

data class DeepLink(
    val route: DeepLinkRoute,
    val fromQR: Boolean,
    val referrer: Uri?,
) {

    val isUnknown: Boolean
        get() = route is DeepLinkRoute.Unknown

    constructor(
        uri: Uri,
        fromQR: Boolean,
        referrer: Uri?
    ): this(
        route = DeepLinkRoute.resolve(uri),
        fromQR = fromQR,
        referrer = referrer
    )

}