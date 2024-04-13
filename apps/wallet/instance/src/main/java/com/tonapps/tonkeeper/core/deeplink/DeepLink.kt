package com.tonapps.tonkeeper.core.deeplink

import android.content.Context
import android.net.Uri
import android.util.Log
import com.tonapps.tonkeeperx.R
import com.tonapps.tonkeeper.ui.screen.root.RootActivity
import com.tonapps.tonkeeper.ui.screen.main.MainScreen
import uikit.extensions.activity
import uikit.extensions.findFragment

object DeepLink {

    const val TON_SCHEME = "ton"
    const val TONKEEPER_SCHEME = "tonkeeper"
    const val TONCONNECT_SCHEME = "tc"

    const val TONKEEPER_HOST = "app.tonkeeper.com"

    private val supportedSchemes = listOf(TON_SCHEME, TONKEEPER_SCHEME, TONCONNECT_SCHEME)
    private val supportedHosts = listOf(TONKEEPER_HOST)

    fun isSupportedUri(uri: Uri): Boolean {
        return supportedSchemes.contains(uri.scheme) || supportedHosts.contains(uri.host)
    }

    fun isSupportedUrl(url: String?): Boolean {
        if (url == null) {
            return false
        }
        return isSupportedUri(Uri.parse(url))
    }

    fun isSupportedScheme(url: String?): Boolean {
        if (url == null) {
            return false
        }
        return supportedSchemes.any { url.startsWith("$it://") }
    }

    fun getTonkeeperUriFirstPath(uri: Uri): String {
        val pathSegments = uri.pathSegments.toMutableList()
        if (uri.scheme == TONKEEPER_SCHEME) {
            pathSegments.add(0, uri.host!!)
        }
        if (pathSegments.isEmpty()) {
            return ""
        }
        return pathSegments.first()
    }

    fun isTonkeeperUri(uri: Uri): Boolean {
        return uri.host == TONKEEPER_HOST || uri.scheme == TONKEEPER_SCHEME
    }

    fun isTonConnectUri(uri: Uri): Boolean {
        if (uri.scheme == TONCONNECT_SCHEME) {
            return true
        }
        return getTonkeeperUriFirstPath(uri) == "ton-connect"
    }

    data class Transfer(
        val address: String,
        val amount: Long?,
        val text: String?
    ) {

        constructor(uri: Uri) : this(
            address = uri.host ?: "",
            amount = uri.getQueryParameter("amount")?.toLongOrNull(),
            text = uri.getQueryParameter("text")
        )
    }

}