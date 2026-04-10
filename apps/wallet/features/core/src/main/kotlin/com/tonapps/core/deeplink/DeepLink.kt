package com.tonapps.core.deeplink

import android.net.Uri
import androidx.core.net.toUri
import com.tonapps.bus.generated.Events

data class DeepLink(
    val route: DeepLinkRoute,
    val fromQR: Boolean,
    val referrer: Uri?,
    val isTonConnect: Boolean = false,
) {

    enum class Source {
        Deeplink, QR, TonConnect; // TODO refactor

        val analytic: Events.SendNative.SendNativeFrom get() {
            return when (this) {
                Deeplink -> Events.SendNative.SendNativeFrom.DeepLink
                QR -> Events.SendNative.SendNativeFrom.QrCode
                TonConnect -> Events.SendNative.SendNativeFrom.TonconnectLocal
            }
        }
    }

    val source: Source get() {
        return when {
            isTonConnect -> Source.TonConnect
            fromQR -> Source.QR
            else -> Source.Deeplink
        }
    }

    companion object {

        fun fixBadUri(uri: Uri): Uri {
            return fixBadUrl(uri.toString()).toUri()
        }

        fun fixBadUrl(url: String): String {
            var fixedUrl = url.replace("tg:resolve", "tg://resolve")
            fixedUrl = fixedUrl.replace("\\u0026", "&")
            return fixedUrl
        }

        // TODO duplicated
        fun isTonConnectDeepLink(
            uri: Uri
        ): Boolean {
            return uri.scheme?.lowercase() == "tc" || uri.path?.lowercase() == "/ton-connect" || uri.host?.lowercase() == "ton-connect"
        }
    }

    constructor(
        uri: Uri,
        fromQR: Boolean,
        referrer: Uri?
    ): this(
        route = DeepLinkRoute.resolve(uri),
        fromQR = fromQR,
        referrer = referrer,
        isTonConnect = isTonConnectDeepLink(uri)
    )
}
