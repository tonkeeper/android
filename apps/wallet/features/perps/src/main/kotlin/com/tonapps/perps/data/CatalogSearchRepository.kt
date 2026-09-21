package com.tonapps.perps.data

import com.tonapps.async.Async
import com.tonapps.core.helper.EnvironmentHelper
import com.tonapps.log.L
import com.tonapps.mvi.graph.KResult
import com.tonapps.wallet.api.API
import io.tradingapi.models.AssetsOrder
import io.tradingapi.models.AssetsSort
import io.tradingapi.models.AssetsTab
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext

class CatalogSearchRepository(
    private val api: API,
    private val environment: EnvironmentHelper,
) {

    suspend fun search(
        query: String,
        filter: PerpsMarketFilter,
        sort: CatalogSearchSort,
        chain: String?,
        showPerps: Boolean,
        currency: String,
        cursor: String?,
    ): KResult<CatalogSearchPage, PerpsError> = withContext(Async.Io) {
        try {
            val response = api.trading.assets.getAssetsCatalogV2(
                tab = AssetsTab.all,
                q = query.takeIf { it.isNotBlank() },
                showPerps = true.takeIf { showPerps && chain == null },
                chain = chain,
                sort = sort.toApiSort(),
                order = sort.toApiOrder(),
                filter = filter.toApiFilter(),
                cursor = cursor,
                pageSize = CATALOG_SEARCH_PAGE_SIZE,
                currency = currency,
                xLang = environment.locale(),
                storeCountryCode = environment.storeCountry(),
                deviceCountryCode = environment.deviceCountry(),
                simCountry = environment.simCountry(),
                timezone = environment.timezone(),
                isVpnActive = environment.isVpnActive(),
            )
            KResult.Ok(
                CatalogSearchPage(
                    rows = response.items.mapNotNull { it.toCatalogSearchRow(currency) },
                    nextCursor = response.nextCursor?.takeIf { it.isNotBlank() },
                ),
            )
        } catch (e: Throwable) {
            if (e is CancellationException) {
                throw e
            }
            L.e(e)
            KResult.Err(e.toPerpsError())
        }
    }
}

private fun CatalogSearchSort.toApiSort(): AssetsSort {
    return when (this) {
        CatalogSearchSort.MARKET_CAP -> AssetsSort.market_cap
        CatalogSearchSort.VOLUME -> AssetsSort.volume_24h
        CatalogSearchSort.LOSERS, CatalogSearchSort.GAINERS -> AssetsSort.price_24h
    }
}

private fun CatalogSearchSort.toApiOrder(): AssetsOrder {
    return if (this == CatalogSearchSort.LOSERS) {
        AssetsOrder.asc
    } else {
        AssetsOrder.desc
    }
}
