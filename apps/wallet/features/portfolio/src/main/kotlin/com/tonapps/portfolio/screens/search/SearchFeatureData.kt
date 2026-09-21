package com.tonapps.portfolio.screens.search

import android.os.Bundle
import com.tonapps.chainkit.core.chain.model.account.Network
import com.tonapps.core.navigation.PortfolioSearchSort
import com.tonapps.perps.data.CatalogSearchSort

data class SearchFeatureData(
    val initialSort: CatalogSearchSort? = null,
    val initialNetwork: Network.Type? = null,
) {

    companion object {
        const val ARG_INITIAL_SORT = "initial_sort"
        const val ARG_INITIAL_NETWORK = "initial_network"

        fun from(arguments: Bundle?): SearchFeatureData = SearchFeatureData(
            initialSort = arguments?.getString(ARG_INITIAL_SORT)
                ?.let { name -> runCatching { PortfolioSearchSort.valueOf(name) }.getOrNull() }
                ?.toCatalogSearchSort(),
            initialNetwork = arguments?.getString(ARG_INITIAL_NETWORK)?.toNetworkTypeOrNull(),
        )
    }
}

private fun String.toNetworkTypeOrNull(): Network.Type? =
    runCatching { Network.Type.valueOf(this) }.getOrNull()

private fun PortfolioSearchSort.toCatalogSearchSort(): CatalogSearchSort = when (this) {
    PortfolioSearchSort.MARKET_CAP -> CatalogSearchSort.MARKET_CAP
    PortfolioSearchSort.VOLUME -> CatalogSearchSort.VOLUME
    PortfolioSearchSort.PRICE_DIFF_ASC -> CatalogSearchSort.LOSERS
    PortfolioSearchSort.PRICE_DIFF_DESC -> CatalogSearchSort.GAINERS
}
