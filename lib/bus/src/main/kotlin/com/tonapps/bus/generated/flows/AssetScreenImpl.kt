package com.tonapps.bus.generated.flows

import androidx.annotation.AnyThread
import com.tonapps.bus.core.contract.EventExecutor
import com.tonapps.bus.generated.Events
import com.tonapps.bus.generated.Events.AssetScreen.AssetScreenButton
import com.tonapps.bus.generated.Events.AssetScreen.AssetScreenFrom
import com.tonapps.bus.generated.Events.AssetScreen.AssetScreenWalletMode

/**
 * Auto-generated from OpenAPI analytics schemas.
 * Do not edit manually — re-run the generator instead.
 */
class AssetScreenImpl(
    private val eventExecutor: EventExecutor,
) : Events.AssetScreen {

    private fun trackEvent(name: String, params: Map<String, Any>) {
        eventExecutor.trackEvent(name, params)
    }

    /**
     * asset_view
     *
     * User opened an asset detail screen. Replaces the legacy token_open event, which should be removed from the existing codebase.

     */
    @AnyThread
    override fun assetView(from: AssetScreenFrom, asset: String, walletMode: AssetScreenWalletMode) {
        val props = hashMapOf(
            "from" to from.key,
            "asset" to asset,
            "wallet_mode" to walletMode.key
        )
        trackEvent("asset_view", props)
    }

    /**
     * asset_button_click
     *
     * User tapped an action button on the asset detail screen
     */
    @AnyThread
    override fun assetButtonClick(button: AssetScreenButton, asset: String) {
        trackEvent("asset_button_click", hashMapOf("button" to button.key, "asset" to asset))
    }
}
