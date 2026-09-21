package com.tonapps.portfolio.screens.search

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.tonapps.mvi.graph.KResult
import com.tonapps.perps.data.CatalogSearchRepository
import com.tonapps.perps.data.CatalogSearchRow
import com.tonapps.perps.data.CatalogSearchSort
import com.tonapps.perps.data.PerpsError
import com.tonapps.perps.data.PerpsLoadException
import com.tonapps.perps.data.PerpsMarketFilter
import java.util.concurrent.ConcurrentHashMap

internal class SearchPagingSource(
    private val repository: CatalogSearchRepository,
    private val query: String,
    private val filter: PerpsMarketFilter,
    private val sort: CatalogSearchSort,
    private val chain: String?,
    private val showPerps: Boolean,
    private val currency: String,
    private val verifiedOnly: Boolean,
) : PagingSource<String, CatalogSearchRow>() {

    private val loadedIds = ConcurrentHashMap.newKeySet<String>()

    override suspend fun load(params: LoadParams<String>): LoadResult<String, CatalogSearchRow> {
        return when (val result = repository.search(query, filter, sort, chain, showPerps, currency, params.key)) {
            is KResult.Ok -> LoadResult.Page(
                data = result.value.rows.filter { it.isAllowed() && loadedIds.add(it.id) },
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

    private fun CatalogSearchRow.isAllowed(): Boolean {
        if (!verifiedOnly) {
            return true
        }
        return this !is CatalogSearchRow.Spot || item.asset.verification.isVerified
    }

    override fun getRefreshKey(state: PagingState<String, CatalogSearchRow>): String? = null
}
