package com.tonapps.tonkeeper.deeplink

import android.net.Uri

data class DeepLink(
    val route: DeepLinkRoute,
    val fromQR: Boolean,
    val referrer: Uri?,
) {

    companion object {

        fun fixBadUri(uri: Uri): Uri {
            var url = uri.toString()

            // fix for bad tg scheme
            url = url.replace("tg:resolve", "tg://resolve")
            url = url.replace("\\u0026", "&")

            return Uri.parse(url)
        }
    }

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