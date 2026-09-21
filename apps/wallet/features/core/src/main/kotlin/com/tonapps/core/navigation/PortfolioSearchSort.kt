package com.tonapps.core.navigation

/**
 * Sort presets that can be passed when opening the portfolio Crypto search screen.
 *
 * Kept in the core module so it can appear in [NavigationDelegate] and trading signatures.
 * The portfolio module maps it to a `CatalogSearchSort` value.
 */
enum class PortfolioSearchSort {
    MARKET_CAP,
    VOLUME,
    PRICE_DIFF_ASC,
    PRICE_DIFF_DESC,
}
