package com.tonapps.perps.data

import com.tonapps.wallet.data.multichain.asset.AssetWithDetails

enum class CatalogSearchSort {
    MARKET_CAP,
    VOLUME,
    LOSERS,
    GAINERS,
}

sealed interface CatalogSearchRow {
    val id: String

    data class Spot(val item: AssetWithDetails) : CatalogSearchRow {
        override val id: String get() = item.asset.id
    }

    data class Perp(val market: PerpMarket, val currencyCode: String) : CatalogSearchRow {
        override val id: String get() = "perp_${market.marketIndex}"
    }
}

data class CatalogSearchPage(
    val rows: List<CatalogSearchRow>,
    val nextCursor: String?,
)

const val CATALOG_SEARCH_PAGE_SIZE = 50
