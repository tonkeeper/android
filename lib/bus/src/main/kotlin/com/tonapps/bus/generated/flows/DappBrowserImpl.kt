package com.tonapps.bus.generated.flows

import androidx.annotation.AnyThread
import com.tonapps.bus.core.contract.EventExecutor
import com.tonapps.bus.generated.Events
import com.tonapps.bus.generated.Events.DappBrowser.DappAppClickFrom
import com.tonapps.bus.generated.Events.DappBrowser.DappBrowserAssetChain
import com.tonapps.bus.generated.Events.DappBrowser.DappBrowserOpenFrom
import com.tonapps.bus.generated.Events.DappBrowser.DappBrowserType
import com.tonapps.bus.generated.Events.DappBrowser.DappSharingCopyFrom

/**
 * Auto-generated from OpenAPI analytics schemas.
 * Do not edit manually — re-run the generator instead.
 */
class DappBrowserImpl(
    private val eventExecutor: EventExecutor,
) : Events.DappBrowser {

    private fun trackEvent(name: String, params: Map<String, Any>) {
        eventExecutor.trackEvent(name, params)
    }

    /**
     * dapp_browser_open
     *
     * Triggered when user opens the Browser/Discover section
     */
    @AnyThread
    override fun dappBrowserOpen(from: DappBrowserOpenFrom, type: DappBrowserType, location: String) {
        val props = hashMapOf(
            "from" to from.key,
            "type" to type.key,
            "location" to location
        )
        trackEvent("dapp_browser_open", props)
    }

    /**
     * dapp_browser_tab_click
     *
     * Triggered when user clicks a Browser/Discover tab
     */
    @AnyThread
    override fun dappBrowserTabClick(type: DappBrowserType, location: String) {
        trackEvent("dapp_browser_tab_click", hashMapOf("type" to type.key, "location" to location))
    }

    /**
     * dapp_pin
     *
     * A dapp is pinned in the Browser/Discover section.
Emitted only by `pro-mobile`. The native clients ship no pin UI at all, so they emit neither dapp_pin nor dapp_unpin — their absence there is a missing feature, not a drop in pinning, and must not be read as one. Instrument the properties below if a pin UI ever ships on a native client, and drop this note then.

     */
    @AnyThread
    override fun dappPin(url: String, assetChain: DappBrowserAssetChain, location: String) {
        val props = hashMapOf(
            "url" to url,
            "asset_chain" to assetChain.key,
            "location" to location
        )
        trackEvent("dapp_pin", props)
    }

    /**
     * dapp_unpin
     *
     * A previously pinned dapp is unpinned in the Browser/Discover section.
Same platform scope as dapp_pin: emitted only by `pro-mobile`, never by the native clients.

     */
    @AnyThread
    override fun dappUnpin(url: String, assetChain: DappBrowserAssetChain, location: String) {
        val props = hashMapOf(
            "url" to url,
            "asset_chain" to assetChain.key,
            "location" to location
        )
        trackEvent("dapp_unpin", props)
    }

    /** dapp_sharing_copy */
    @AnyThread
    override fun dappSharingCopy(
        url: String,
        assetChain: DappBrowserAssetChain,
        from: DappSharingCopyFrom,
        location: String
    ) {
        val props = hashMapOf(
            "url" to url,
            "asset_chain" to assetChain.key,
            "from" to from.key,
            "location" to location
        )
        trackEvent("dapp_sharing_copy", props)
    }

    /**
     * dapp_app_click
     *
     * Triggered when the user TAPS a dapp to open it — the intent / attempt, NOT a successful open. It always comes first, and the open then ends in dapp_app_loaded when the page loads, in dapp_app_load_failed when it fails to load, or in neither when the user leaves before the page answers; a client that does not emit dapp_app_load_failed yet leaves its failures in the last group. The dapp_app_loaded / dapp_app_click ratio, matched on url / app_id, is the dapp-open success rate.

     */
    @AnyThread
    override fun dappAppClick(
        from: DappAppClickFrom,
        url: String,
        assetChain: DappBrowserAssetChain,
        appId: String,
        bannerId: String?,
        location: String
    ) {
        val props = hashMapOf<String, Any>(
            "from" to from.key,
            "url" to url,
            "asset_chain" to assetChain.key,
            "app_id" to appId,
            "location" to location
        )
        bannerId?.let { props["banner_id"] = it }
        trackEvent("dapp_app_click", props)
    }

    /**
     * dapp_app_loaded
     *
     * Triggered when a dapp has SUCCESSFULLY loaded and is ready in the in-app browser (the webview finished loading). Always preceded by dapp_app_click and emitted only on a successful load: a page the server answers with an HTTP error status is dapp_app_load_failed, not this event, even though the webview finishes rendering the error page. Replaces the legacy dapp_app_open name.

     */
    @AnyThread
    override fun dappAppLoaded(
        from: DappAppClickFrom,
        url: String,
        assetChain: DappBrowserAssetChain,
        appId: String,
        bannerId: String?,
        location: String,
        loadDurationMs: Int
    ) {
        val props = hashMapOf<String, Any>(
            "from" to from.key,
            "url" to url,
            "asset_chain" to assetChain.key,
            "app_id" to appId,
            "location" to location,
            "load_duration_ms" to loadDurationMs
        )
        bannerId?.let { props["banner_id"] = it }
        trackEvent("dapp_app_loaded", props)
    }

    /**
     * dapp_app_load_failed
     *
     * Triggered when the page of a dapp fails to load in the in-app browser — the load dapp_app_click started, not a later navigation inside the dapp. It ends the open in place of dapp_app_loaded, so the two never follow the same click. A page that loads and then fails inside — a script error, its own API down, a blank screen — has loaded as far as the browser can tell and reports dapp_app_loaded; only the page itself can report that.

     */
    @AnyThread
    override fun dappAppLoadFailed(
        from: DappAppClickFrom,
        url: String,
        assetChain: DappBrowserAssetChain,
        appId: String,
        bannerId: String?,
        location: String,
        errorCode: String,
        httpStatus: Int?,
        loadDurationMs: Int
    ) {
        val props = hashMapOf<String, Any>(
            "from" to from.key,
            "url" to url,
            "asset_chain" to assetChain.key,
            "app_id" to appId,
            "location" to location,
            "error_code" to errorCode,
            "load_duration_ms" to loadDurationMs
        )
        bannerId?.let { props["banner_id"] = it }
        httpStatus?.let { props["http_status"] = it }
        trackEvent("dapp_app_load_failed", props)
    }

    /**
     * dapp_browser_search_open
     *
     * Triggered when user makes a search request in the browser
     */
    @AnyThread
    override fun dappBrowserSearchOpen(url: String, location: String) {
        trackEvent("dapp_browser_search_open", hashMapOf("url" to url, "location" to location))
    }

    /**
     * dapp_browser_search_click
     *
     * Triggered when user clicks somewhere from search in a browser session
     */
    @AnyThread
    override fun dappBrowserSearchClick(url: String, location: String) {
        trackEvent("dapp_browser_search_click", hashMapOf("url" to url, "location" to location))
    }
}
