package com.tonapps.tonkeeper.ui.screen.browser.analytics

import android.os.Parcelable
import com.tonapps.bus.generated.Events.DappBrowser.DappAppClickFrom
import com.tonapps.bus.generated.Events.DappBrowser.DappBrowserAssetChain
import com.tonapps.bus.generated.Events.DappBrowser.DappSharingCopyFrom
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import kotlin.concurrent.atomics.AtomicBoolean

@Parcelize
data class DappOpenAnalytics(
    private val from: DappAppClickFrom,
    private val url: String,
    private val assetChain: DappBrowserAssetChain,
    private val appId: String,
    private val bannerId: String?,
    private val location: String,
): Parcelable {

    @IgnoredOnParcel
    private var loadedTracked = AtomicBoolean(false)

    fun click() {
        DappBrowserAnalytics.events.dappAppClick(
            from = from,
            url = url,
            assetChain = assetChain,
            appId = appId,
            bannerId = bannerId,
            location = location
        )
    }

    // The webview reports every navigation as finished, but the ratio with dapp_app_click only
    // holds if a single open produces at most one loaded event.
    fun loaded() {
        if (!loadedTracked.compareAndSet(expectedValue = false, newValue = true)) {
            return
        }

        DappBrowserAnalytics.events.dappAppLoaded(
            from = from,
            url = url,
            assetChain = assetChain,
            appId = appId,
            bannerId = bannerId,
            location = location
        )
    }

    fun sharingCopy(from: DappSharingCopyFrom) {
        DappBrowserAnalytics.events.dappSharingCopy(
            url = url,
            assetChain = assetChain,
            from = from,
            location = location
        )
    }
}
