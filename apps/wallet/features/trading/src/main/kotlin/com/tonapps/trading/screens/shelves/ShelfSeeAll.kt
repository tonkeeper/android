package com.tonapps.trading.screens.shelves

import com.tonapps.core.navigation.PortfolioSearchAssetType
import io.tradingapi.models.AssetsTab

/**
 * The asset type the search opens on for a shelf's see-all route, as on iOS.
 * Null opens it on all assets.
 */
internal fun AssetsTab.toPortfolioSearchAssetType(): PortfolioSearchAssetType? = when (this) {
    AssetsTab.tokens -> PortfolioSearchAssetType.TOKENS
    AssetsTab.stocks -> PortfolioSearchAssetType.STOCKS
    AssetsTab.etfs -> PortfolioSearchAssetType.ETFS
    AssetsTab.all, AssetsTab.commodities, AssetsTab.perpetuals, AssetsTab.unknown -> null
}
