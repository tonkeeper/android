package com.tonapps.bus.generated.flows

import androidx.annotation.AnyThread
import com.tonapps.bus.core.contract.EventExecutor
import com.tonapps.bus.generated.Events
import com.tonapps.bus.generated.Events.TradeUiFlow.TradeFavoriteAddFrom
import com.tonapps.bus.generated.Events.TradeUiFlow.TradeFavoriteRemoveFrom
import com.tonapps.bus.generated.Events.TradeUiFlow.TradeStartedFrom

/**
 * Auto-generated from OpenAPI analytics schemas.
 * Do not edit manually — re-run the generator instead.
 */
class TradeUiFlowImpl(
    private val eventExecutor: EventExecutor,
) : Events.TradeUiFlow {

    private fun trackEvent(name: String, params: Map<String, Any>) {
        eventExecutor.trackEvent(name, params)
    }

    /**
     * trade_started
     *
     * User entered the Trade page
     */
    @AnyThread
    override fun tradeStarted(from: TradeStartedFrom) {
        trackEvent("trade_started", hashMapOf("from" to from.key))
    }

    /**
     * trade_click_asset
     *
     * User clicked an asset from the Trade page
     */
    @AnyThread
    override fun tradeClickAsset(from: TradeStartedFrom, asset: String) {
        trackEvent("trade_click_asset", hashMapOf("from" to from.key, "asset" to asset))
    }

    /**
     * trade_search
     *
     * User searched for an asset in the Trade section. Must be sent at the same time and with the same query as the API request.

     */
    @AnyThread
    override fun tradeSearch(from: TradeStartedFrom, query: String?) {
        val props = hashMapOf<String, Any>("from" to from.key)
        query?.let { props["query"] = it }
        trackEvent("trade_search", props)
    }

    /**
     * trade_search_click
     *
     * User clicked on an asset in the Trade search results
     */
    @AnyThread
    override fun tradeSearchClick(from: TradeStartedFrom, query: String?, asset: String) {
        val props = hashMapOf<String, Any>("from" to from.key, "asset" to asset)
        query?.let { props["query"] = it }
        trackEvent("trade_search_click", props)
    }

    /**
     * trade_favorite_add
     *
     * User added an asset to favorites
     */
    @AnyThread
    override fun tradeFavoriteAdd(from: TradeFavoriteAddFrom, asset: String) {
        trackEvent("trade_favorite_add", hashMapOf("from" to from.key, "asset" to asset))
    }

    /**
     * trade_favorite_remove
     *
     * User removed an asset from favorites
     */
    @AnyThread
    override fun tradeFavoriteRemove(from: TradeFavoriteRemoveFrom, asset: String) {
        trackEvent("trade_favorite_remove", hashMapOf("from" to from.key, "asset" to asset))
    }

    /**
     * trade_favorite_click
     *
     * User clicked an asset in the Trade favorites section
     */
    @AnyThread
    override fun tradeFavoriteClick(asset: String, position: Int) {
        trackEvent("trade_favorite_click", hashMapOf("asset" to asset, "position" to position))
    }
}
