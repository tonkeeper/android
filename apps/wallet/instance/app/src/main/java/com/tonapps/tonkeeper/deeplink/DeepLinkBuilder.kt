package com.tonapps.tonkeeper.deeplink

import android.net.Uri
import com.tonapps.extensions.toUriOrNull

object DeepLinkBuilder {

    private const val prefix = "https://app.tonkeeper.com/"

    private val dAppIsSpecialHosts = arrayOf(
        "tonviewer.com", "tronscan.org",
        "testnet.tonviewer.com", "test.tronscan.org"
    )

    fun dAppIsSpecialUrl(appUrl: String): Boolean {
        val uri = appUrl.toUriOrNull() ?: return false
        return dAppIsSpecialUrl(uri)
    }

    fun dAppIsSpecialUrl(appUri: Uri): Boolean {
        val host = appUri.host ?: return false
        return dAppIsSpecialHosts.any { host.contains(it) }
    }

    fun dAppShare(appUrl: String): String {
        if (dAppIsSpecialUrl(appUrl)) {
            return appUrl
        }
        return "${prefix}dapp/${Uri.encode(appUrl)}"
    }
}