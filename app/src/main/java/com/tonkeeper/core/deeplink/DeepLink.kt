package com.tonkeeper.core.deeplink

import android.net.Uri
import com.tonkeeper.fragment.root.RootActivity
import com.tonkeeper.R
import com.tonkeeper.fragment.main.MainFragment
import uikit.extensions.findFragment

class DeepLink(private val activity: RootActivity) {

    companion object {
        const val TON_SCHEME = "ton"
        const val TONKEEPER_SCHEME = "tonkeeper"
        const val TONCONNECT_SCHEME = "tc"

        const val TONKEEPER_HOST = "app.tonkeeper.com"

        private val supportedSchemes = listOf(TON_SCHEME, TONKEEPER_SCHEME, TONCONNECT_SCHEME)
        private val supportedHosts = listOf(TONKEEPER_HOST)

        private const val INTERNAL_PATH_WALLET = "wallet"
        private const val INTERNAL_PATH_ACTIVITY = "activity"
        private const val INTERNAL_PATH_SETTINGS = "settings"

        private val internalPathsMain = listOf(INTERNAL_PATH_WALLET, INTERNAL_PATH_ACTIVITY, INTERNAL_PATH_SETTINGS)
    }

    private val fragmentManager = activity.supportFragmentManager

    fun isSupportedUri(uri: Uri): Boolean {
        return supportedSchemes.contains(uri.scheme) || supportedHosts.contains(uri.host)
    }

    fun handle(uri: Uri): Boolean {
        if (uri.host == TONKEEPER_HOST || uri.scheme == TONKEEPER_SCHEME) {
            return handleAppLink(uri)
        }
        return false
    }

    private fun handleAppLink(uri: Uri): Boolean {
        val pathSegments = uri.pathSegments.toMutableList()
        if (uri.scheme == TONKEEPER_SCHEME) {
            pathSegments.add(0, uri.host!!)
        }
        if (pathSegments.isEmpty()) {
            return false
        }
        val firstPath = pathSegments.first()
        if (firstPath in internalPathsMain) {
            return handleAppTab(firstPath)
        }
        return false
    }

    private fun handleAppTab(tab: String): Boolean {
        val fragment = fragmentManager.findFragment<MainFragment>() ?: return false
        val tabId = when (tab) {
            INTERNAL_PATH_WALLET -> R.id.wallet
            INTERNAL_PATH_ACTIVITY -> R.id.activity
            INTERNAL_PATH_SETTINGS -> R.id.settings
            else -> return false
        }
        fragment.forceSelectTab(tabId)
        return true
    }

}