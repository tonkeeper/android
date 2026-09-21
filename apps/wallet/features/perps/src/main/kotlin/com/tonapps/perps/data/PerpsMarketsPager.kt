package com.tonapps.perps.data

import androidx.paging.Pager
import androidx.paging.PagingConfig

fun perpsMarketsPager(
    repository: PerpsRepository,
    query: String,
    filter: PerpsMarketFilter,
    sort: PerpsSort,
    currency: String = USD_CURRENCY,
): Pager<String, PerpMarket> = Pager(
    config = PagingConfig(
        pageSize = PERPS_PAGE_SIZE,
        initialLoadSize = PERPS_PAGE_SIZE,
        prefetchDistance = PERPS_PREFETCH_DISTANCE,
        enablePlaceholders = false,
    ),
    pagingSourceFactory = { PerpsMarketsPagingSource(repository, query, filter, sort, currency) },
)

const val PERPS_PAGE_SIZE = 50
const val PERPS_PREFETCH_DISTANCE = 10
