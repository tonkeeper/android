package com.tonapps.tonkeeper.manager.tonconnect

import android.annotation.SuppressLint
import android.net.Uri
import android.webkit.CookieManager
import androidx.webkit.ProfileStore
import androidx.webkit.WebViewFeature
import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.log.L
import com.tonapps.tonkeeper.extensions.webViewProfileName
import com.tonapps.wallet.data.dapps.DAppsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Cookies die now, local/sessionStorage on the next load of the origin — a dApp keeping its
// session in either one stays logged in if only the other half runs.
suspend fun DAppsRepository.cleanupDisconnectedOrigin(wallet: WalletEntity, appUrl: Uri) {
    val host = appUrl.host?.lowercase() ?: return
    val profileName = wallet.webViewProfileName()
    markOriginCleanup(profileName, host)
    try {
        expireCookies(profileName, host)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        L.w(e, "Failed to expire cookies for $host")
    }
}

// Cookies with a Path other than "/" survive this: the native store exposes names only.
@SuppressLint("RequiresFeature")
private suspend fun expireCookies(profileName: String, host: String) {
    val cookieManager = withContext(Dispatchers.Main) {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
            ProfileStore.getInstance().getProfile(profileName)?.cookieManager
        } else {
            CookieManager.getInstance()
        }
    } ?: return

    val url = "https://$host"
    val names = cookieManager.getCookie(url)
        .orEmpty()
        .split(';')
        .map { it.substringBefore('=').trim() }
        .filter { it.isNotEmpty() }
        .distinct()

    for (name in names) {
        // Secure is required for Chromium to accept a deletion of a __Secure-/__Host- prefixed name.
        cookieManager.setCookie(url, "$name=; Path=/; Max-Age=0; Secure")
        cookieManager.setCookie(url, "$name=; Path=/; Max-Age=0; Secure; Domain=.$host")
    }
    cookieManager.flush()
}
