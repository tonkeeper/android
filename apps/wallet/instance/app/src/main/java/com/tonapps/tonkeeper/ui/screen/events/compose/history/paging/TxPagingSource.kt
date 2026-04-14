package com.tonapps.tonkeeper.ui.screen.events.compose.history.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.tonapps.tonkeeper.ui.screen.events.compose.history.TxUiMapper
import com.tonapps.wallet.api.entity.value.Timestamp
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.wallet.data.events.EventsRepository
import com.tonapps.wallet.data.events.tx.TxFetchQuery
import com.tonapps.wallet.data.events.tx.TxPage
import com.tonapps.wallet.data.events.tx.model.TxEvent
import com.tonapps.wallet.data.settings.SettingsRepository
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ui.components.events.UiEvent
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

internal class TxPagingSource(
    private val wallet: WalletEntity,
    private val accountRepository: AccountRepository,
    private val eventsRepository: EventsRepository,
    private val settingsRepository: SettingsRepository,
    private val uiMapper: TxUiMapper,
): PagingSource<Timestamp, UiEvent.Item>() {

    companion object {
        private val cache = ConcurrentHashMap<String, TxEvent>()

        fun get(id: String) = cache[id]
    }

    private val tronParamsProvider = TxTronParamsProvider(
        wallet = wallet,
        accountRepository = accountRepository,
        settingsRepository = settingsRepository
    )

    private val processedEventIds = mutableSetOf<String>()

    override suspend fun load(
        params: LoadParams<Timestamp>
    ): LoadResult<Timestamp, UiEvent.Item> = withContext(Dispatchers.IO) {
        try {
            val beforeTimestamp = params.key
            val data = if (params is LoadParams.Refresh && beforeTimestamp == null) {
                initialLoad(params.loadSize)
            } else {
                nextLoad(beforeTimestamp ?: Timestamp.now, params.loadSize)
            }
            if (data.isEmpty) {
                LoadResult.Page(emptyList(), null, null)
            } else {
                val uiItems = data.events.map {
                    cache[it.id] = it
                    uiMapper.event(it)
                }

                LoadResult.Page(
                    data = uiItems.toImmutableList(),
                    prevKey = null,
                    nextKey = data.nextKey
                )
            }
        } catch (e: Throwable) {
            LoadResult.Error(e)
        }
    }

    private suspend fun query(
        afterTimestamp: Timestamp? = null,
        beforeTimestamp: Timestamp? = null,
        limit: Int
    ): TxFetchQuery {
        val tronParams = tronParamsProvider.get()
        return TxFetchQuery(
            tonAddress = wallet.blockchainAddress,
            tronAddress = tronParams?.address,
            tonProofToken = tronParams?.tonProofToken,
            beforeTimestamp = beforeTimestamp,
            afterTimestamp = afterTimestamp,
            limit = limit
        )
    }

    private suspend fun nextLoad(beforeTimestamp: Timestamp, limit: Int): TxPage {
        val query = query(
            beforeTimestamp = beforeTimestamp,
            limit = limit
        )
        return processEvents(eventsRepository.fetch(query))
    }

    private suspend fun initialLoad(limit: Int): TxPage {
        val query = query(limit = limit)
        return processEvents(eventsRepository.fetch(query))
    }

    private fun processEvents(page: TxPage): TxPage {
        val events = processEvents(page.events, page.limit)
        return page.copy(
            events = events
        )
    }

    private fun processEvents(events: List<TxEvent>, limit: Int): List<TxEvent> {
        val filtered = events.filter {
            !processedEventIds.contains(it.id)
        }.sortedByDescending {
            it.timestamp
        }.take(limit)
        processedEventIds.addAll(filtered.map { it.id })
        return filtered
    }

    override fun getRefreshKey(state: PagingState<Timestamp, UiEvent.Item>) = null
}
