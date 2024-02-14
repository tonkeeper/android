package com.tonapps.tonkeeper.core.deeplink

import android.content.Context
import android.net.Uri
import com.tonapps.tonkeeperx.R
import com.tonapps.tonkeeper.fragment.root.RootActivity
import com.tonapps.tonkeeper.fragment.main.MainFragment
import uikit.extensions.activity
import uikit.extensions.findFragment

class DeepLink(private val activity: RootActivity) {

    interface Processor {
        fun openUri(uri: Uri): Boolean
    }

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

        fun openUrl(context: Context, url: String?): Boolean {
            val uri = Uri.parse(url)
            return openUri(context, uri)
        }

        fun openUri(context: Context, uri: Uri): Boolean {
            val processor = context.activity as? Processor ?: return false
            return processor.openUri(uri)
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
    }

    private val fragmentManager = activity.supportFragmentManager

    fun handle(uri: Uri): Boolean {
        if (isTonkeeperUri(uri)) {
            return handleAppLink(uri)
        }
        return false
    }

    private fun handleAppLink(uri: Uri): Boolean {
        val firstPath = getTonkeeperUriFirstPath(uri)
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