package com.tonapps.perps.data

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.tonapps.mvi.graph.KResult
import java.util.concurrent.ConcurrentHashMap

internal class PerpsMarketsPagingSource(
    private val repository: PerpsRepository,
    private val query: String,
    private val filter: PerpsMarketFilter,
    private val sort: PerpsSort,
    private val currency: String,
) : PagingSource<String, PerpMarket>() {

    private val loadedIndices = ConcurrentHashMap.newKeySet<Int>()

    override suspend fun load(params: LoadParams<String>): LoadResult<String, PerpMarket> {
        return when (val result = repository.getMarkets(query, filter, sort, currency, params.key)) {
            is KResult.Ok -> LoadResult.Page(
                data = result.value.markets.filter { loadedIndices.add(it.marketIndex) },
                prevKey = null,
                nextKey = result.value.nextCursor,
            )

            is KResult.Err -> if (result.value == PerpsError.BadRequest && params.key == null && query.isNotBlank()) {
                LoadResult.Page(data = emptyList(), prevKey = null, nextKey = null)
            } else {
                LoadResult.Error(PerpsLoadException(result.value))
            }
        }
    }

    override fun getRefreshKey(state: PagingState<String, PerpMarket>): String? = null
}
