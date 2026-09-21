package com.tonapps.deposit.multicoin.screens.assets

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.tonapps.deposit.multicoin.data.RampAsset
import com.tonapps.deposit.multicoin.data.RampRepository
import com.tonapps.deposit.screens.ramp.RampType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

internal class AssetsExtendedPagingSource(
    private val rampRepository: RampRepository,
    private val rampType: RampType,
    private val query: String?,
    private val fiat: String?,
    private val chain: String?,
    private val onFirstPageLoaded: (List<RampAsset>) -> Unit = {},
) : PagingSource<String, RampAsset>() {

    private val loadedAssetIds = ConcurrentHashMap.newKeySet<String>()

    override suspend fun load(
        params: LoadParams<String>
    ): LoadResult<String, RampAsset> = withContext(Dispatchers.IO) {
        try {
            val configuration = rampRepository.getConfiguration(
                rampType = rampType,
                chain = chain,
                fiat = fiat,
                query = query,
                cursor = params.key,
                limit = params.loadSize,
            )

            val assets = configuration.assets
                .filter { runCatching { it.asset.value }.isSuccess }
                .dropAlreadyLoaded()
            if (params.key == null) {
                onFirstPageLoaded(assets)
            }

            LoadResult.Page(
                data = assets,
                prevKey = null,
                nextKey = configuration.nextCursor,
            )
        } catch (e: Throwable) {
            LoadResult.Error(e)
        }
    }

    private fun List<RampAsset>.dropAlreadyLoaded(): List<RampAsset> =
        filter { loadedAssetIds.add(it.asset.id) }

    override fun getRefreshKey(state: PagingState<String, RampAsset>): String? = null
}
