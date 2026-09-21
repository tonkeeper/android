package com.tonapps.wallet.features.events.screens

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.tonapps.wallet.features.events.data.HistoryEventEntity
import com.tonapps.wallet.features.events.data.McEventsRepository
import io.walletapi.models.Chain
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class EventsPagingSource(
    private val repository: McEventsRepository,
    private val walletId: String,
    private val chain: Chain?,
    private val assetId: String?,
    private val typeFilter: EventsTypeFilter,
    private val hideDust: Boolean,
    private val onInitialLoad: (hasAnyActivity: Boolean) -> Unit,
    private val onPageLoaded: (activities: List<HistoryEventEntity>, isInitialLoad: Boolean) -> Unit = { _, _ -> },
) : PagingSource<String, HistoryEventEntity>() {

    override suspend fun load(
        params: LoadParams<String>,
    ): LoadResult<String, HistoryEventEntity> = withContext(Dispatchers.IO) {
        try {
            val isInitialLoad = params.key == null
            var initialLoadReported = false
            val items = mutableListOf<HistoryEventEntity>()
            var cursor = params.key
            // Client-side primary-TON filtering can empty a whole page when the seed has
            // multiple TON variants under one wallet_id; keep advancing until a visible
            // row appears, the cursor ends, or the page budget runs out.
            var pagesConsumed = 0
            do {
                val requestCursor = cursor
                val page = repository.getWalletActivities(
                    walletId = walletId,
                    limit = params.loadSize,
                    cursor = requestCursor,
                    chain = chain,
                    activityType = typeFilter.apiType,
                    assetId = assetId,
                    isSpam = typeFilter.apiIsSpam,
                    hideDust = hideDust,
                )
                if (isInitialLoad && !initialLoadReported && page.activities.isNotEmpty()) {
                    initialLoadReported = true
                    onInitialLoad(true)
                }
                items += page.activities
                cursor = page.cursor?.takeIf { it.isNotBlank() && it != requestCursor }
                pagesConsumed++
            } while (items.isEmpty() && cursor != null && pagesConsumed < MAX_PAGES_PER_LOAD)

            if (isInitialLoad && !initialLoadReported) {
                onInitialLoad(
                    when {
                        cursor != null -> true
                        typeFilter == EventsTypeFilter.All -> {
                            repository.hasSpamActivities(
                                walletId = walletId,
                                chain = chain,
                                assetId = assetId,
                            )
                        }
                        else -> false
                    },
                )
            }

            if (items.isNotEmpty() || isInitialLoad) {
                onPageLoaded(items, isInitialLoad)
            }

            LoadResult.Page(
                data = items,
                prevKey = null,
                nextKey = cursor,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            LoadResult.Error(e)
        }
    }

    override fun getRefreshKey(state: PagingState<String, HistoryEventEntity>): String? = null

    private companion object {
        const val MAX_PAGES_PER_LOAD = 10
    }
}
