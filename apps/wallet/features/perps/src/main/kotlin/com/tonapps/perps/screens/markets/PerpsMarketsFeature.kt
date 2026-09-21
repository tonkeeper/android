package com.tonapps.perps.screens.markets

import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.tonapps.async.Async
import com.tonapps.mvi.contract.MviAction
import com.tonapps.mvi.contract.MviViewState
import com.tonapps.mvi.graph.GraphViewModel
import com.tonapps.perps.data.PerpMarket
import com.tonapps.perps.data.PerpsLivePrices
import com.tonapps.perps.data.PerpsMarketFilter
import com.tonapps.perps.data.PerpsRepository
import com.tonapps.perps.data.PerpsSort
import com.tonapps.perps.data.perpsMarketsPager
import com.tonapps.perps.data.perpsTicker
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.runningFold
import java.math.BigDecimal

interface PerpsMarketsViewState : MviViewState {
    val filter: StateFlow<PerpsMarketFilter>
    val debouncedQuery: StateFlow<String>
    val sort: StateFlow<PerpsSort>
    val markets: Flow<PagingData<PerpMarket>>
    val livePrices: StateFlow<Map<String, BigDecimal>>
}

sealed interface PerpsMarketsAction : MviAction {
    data class SetQuery(val value: String) : PerpsMarketsAction
    data class SetFilter(val value: PerpsMarketFilter) : PerpsMarketsAction
    data class SetSort(val value: PerpsSort) : PerpsMarketsAction
    data class SetActive(val active: Boolean) : PerpsMarketsAction
    data class RowVisible(val symbol: String, val visible: Boolean) : PerpsMarketsAction
}

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class PerpsMarketsFeature(
    private val repository: PerpsRepository,
    private val livePriceStore: PerpsLivePrices,
    initialFilter: PerpsMarketFilter,
) : GraphViewModel<PerpsMarketsViewState, PerpsMarketsAction>(), PerpsMarketsViewState {

    private val query = actions
        .on<PerpsMarketsAction.SetQuery>()
        .map { it.value }
        .cacheState(initialValue = "")

    override val debouncedQuery = query
        .debounce { value ->
            if (value.isBlank()) {
                0L
            } else {
                QUERY_DEBOUNCE_MS
            }
        }
        .distinctUntilChanged()
        .cacheState(initialValue = "")

    private val active = actions
        .on<PerpsMarketsAction.SetActive>()
        .map { it.active }
        .distinctUntilChanged()
        .cacheState(initialValue = false)

    private val visibleSymbols = actions
        .on<PerpsMarketsAction.RowVisible>()
        .runningFold(emptySet<String>()) { visible, action ->
            if (action.visible) {
                visible + action.symbol
            } else {
                visible - action.symbol
            }
        }
        .cacheState(initialValue = emptySet<String>())

    override val filter = actions
        .on<PerpsMarketsAction.SetFilter>()
        .map { it.value }
        .cacheState(initialValue = initialFilter)

    override val sort = actions
        .on<PerpsMarketsAction.SetSort>()
        .map { it.value }
        .cacheState(initialValue = PerpsSort.VOLUME)

    override val markets = combine(
        debouncedQuery,
        filter,
        sort,
        transform = { query, filter, sort -> Triple(query, filter, sort) }
    )
        .distinctUntilChanged()
        .flatMapLatest { (query, filter, sort) -> perpsMarketsPager(repository, query, filter, sort).flow }
        .cachedIn(viewModelScope)

    private val tickers = combine(
        visibleSymbols,
        active,
        transform = { symbols, isActive ->
            if (isActive) {
                symbols.mapTo(mutableSetOf(), ::perpsTicker)
            } else {
                null
            }
        }
    )
        .runningFold(emptySet<String>()) { previous, next ->
            when {
                next == null -> emptySet()
                next.isEmpty() -> previous
                else -> next
            }
        }

    override val livePrices = livePriceStore
        .observe(tickers)
        .flowOn(Async.Io)
        .cacheState(initialValue = emptyMap<String, BigDecimal>())

    private companion object {
        const val QUERY_DEBOUNCE_MS = 300L
    }
}

/*
 *   SetQuery ──► query ──► [debounce, distinct] debouncedQuery ──┐
 *                                    SetFilter ──► filter ───────┼──► [distinct, latest] markets
 *                                        SetSort ──► sort ───────┘
 *
 *   RowVisible ──► [fold] visibleSymbols ──┐
 *                    SetActive ──► active ─┴──► [fold] tickers ──► livePrices
 */
