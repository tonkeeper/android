package com.tonapps.core.deeplink

import android.net.Uri
import androidx.core.net.toUri
import com.tonapps.bus.generated.Events

data class DeepLink(
    val route: DeepLinkRoute,
    val fromQR: Boolean,
    val referrer: Uri?,
    val connect: Connect? = null,
    val fromExternal: Boolean = false,
) {

    enum class Connect {
        WalletConnect, TonConnect
    }

    enum class Source {
        Deeplink, QR, TonConnect, WalletConnect; // TODO refactor

        val analytic: Events.SendNative.SendNativeFrom get() {
            return when (this) {
                Deeplink -> Events.SendNative.SendNativeFrom.DeepLink
                QR -> Events.SendNative.SendNativeFrom.QrCode
                WalletConnect -> Events.SendNative.SendNativeFrom.Walletconnect
                TonConnect -> Events.SendNative.SendNativeFrom.TonconnectLocal
            }
        }
    }

    val source: Source get() {
        return when {
            connect == Connect.TonConnect -> Source.TonConnect
            fromQR -> Source.QR
            connect == Connect.WalletConnect -> Source.WalletConnect
            else -> Source.Deeplink
        }
    }

    constructor(
        uri: Uri,
        fromQR: Boolean,
        referrer: Uri?,
        fromExternal: Boolean = false,
    ): this(
        route = DeepLinkRoute.resolve(uri),
        fromQR = fromQR,
        referrer = referrer,
        connect = when {
            isTonConnectDeepLink(uri) -> Connect.TonConnect
            isWalletConnectDeepLink(uri) -> Connect.WalletConnect
            else -> null
        },
        fromExternal = fromExternal,
    )

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

        fun isWalletConnectDeepLink(
            uri: Uri
        ): Boolean {
            return uri.scheme?.lowercase() == "wc" || uri.host?.lowercase() == "wc"
        }
    }
}
