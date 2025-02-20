package com.tonapps.tonkeeper.deeplink

import android.net.Uri
import androidx.core.net.toUri

data class DeepLink(
    val route: DeepLinkRoute,
    val fromQR: Boolean,
    val referrer: Uri?,
) {

    companion object {

        fun fixBadUri(uri: Uri): Uri {
            return fixBadUrl(uri.toString()).toUri()
        }

        fun fixBadUrl(url: String): String {
            var fixedUrl = url.replace("tg:resolve", "tg://resolve")
            fixedUrl = fixedUrl.replace("\\u0026", "&")
            return fixedUrl
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