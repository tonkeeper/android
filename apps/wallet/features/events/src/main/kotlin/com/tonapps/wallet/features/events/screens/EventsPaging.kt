package com.tonapps.wallet.features.events.screens

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.tonapps.wallet.features.events.data.HistoryEventEntity
import com.tonapps.wallet.features.events.data.McEventsRepository
import io.walletapi.models.Chain
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId

internal class EventsPagingSource(
    private val repository: McEventsRepository,
    private val walletId: String,
    private val chain: Chain?,
    private val assetId: String?,
    private val typeFilter: EventsTypeFilter,
    private val hideDust: Boolean,
    private val showPerps: Boolean,
    private val onInitialLoad: (hasAnyActivity: Boolean) -> Unit,
    private val onPageLoaded: (activities: List<HistoryEventEntity>, isInitialLoad: Boolean) -> Unit = { _, _ -> },
) : PagingSource<String, HistoryEventEntity>() {

    private val heldPendingActivities = mutableListOf<HistoryEventEntity>()

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
                    showPerps = showPerps,
                )
                if (isInitialLoad && !initialLoadReported && page.activities.isNotEmpty()) {
                    initialLoadReported = true
                    onInitialLoad(true)
                }
                items += page.activities
                cursor = page.cursor?.takeIf { it.isNotBlank() && it != requestCursor }
                pagesConsumed++
            } while (items.all { it.isPending } && cursor != null && pagesConsumed < MAX_PAGES_PER_LOAD)

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
                data = placePendingAtTopOfTheirDay(items, isLastPage = cursor == null),
                prevKey = null,
                nextKey = cursor,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            LoadResult.Error(e)
        }
    }

    private fun placePendingAtTopOfTheirDay(
        activities: List<HistoryEventEntity>,
        isLastPage: Boolean,
    ): List<HistoryEventEntity> {
        val (pending, confirmed) = activities.partition { it.isPending }
        heldPendingActivities += pending
        if (heldPendingActivities.isEmpty()) {
            return activities
        }
        val zone = ZoneId.systemDefault()
        heldPendingActivities.sortByDescending { it.localDay(zone) }
        val placed = ArrayList<HistoryEventEntity>(confirmed.size + heldPendingActivities.size)
        for (activity in confirmed) {
            val day = activity.localDay(zone)
            while (heldPendingActivities.isNotEmpty() && heldPendingActivities[0].localDay(zone) >= day) {
                placed += heldPendingActivities.removeAt(0)
            }
            placed += activity
        }
        if (isLastPage) {
            placed += heldPendingActivities
            heldPendingActivities.clear()
        }
        return placed
    }

    private fun HistoryEventEntity.localDay(zone: ZoneId): LocalDate {
        return blockTime.atZoneSameInstant(zone).toLocalDate()
    }

    override fun getRefreshKey(state: PagingState<String, HistoryEventEntity>): String? = null

    private companion object {
        const val MAX_PAGES_PER_LOAD = 10
    }
}
