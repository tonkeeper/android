package com.tonapps.portfolio.screens.search

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.tonapps.perps.data.CatalogSearchSort
import com.tonapps.wallet.localization.Localization

@Composable
fun CatalogSearchSort.displayName(): String {
    return when (this) {
        CatalogSearchSort.MARKET_CAP -> stringResource(Localization.market_cap)
        CatalogSearchSort.VOLUME -> stringResource(Localization.volume)
        CatalogSearchSort.LOSERS -> stringResource(Localization.top_losers)
        CatalogSearchSort.GAINERS -> stringResource(Localization.top_gainers)
    }
}
