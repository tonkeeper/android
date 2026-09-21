package com.tonapps.perps.data

import com.tonapps.async.Async
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.util.concurrent.atomic.AtomicLong

@OptIn(FlowPreview::class)
class PerpsLivePrices(private val feed: PerpsPriceFeed) {

    private sealed interface StoreEvent {
        data object Reset : StoreEvent
        data class Feed(val event: PerpsPriceEvent) : StoreEvent
    }

    private val interests = MutableStateFlow(emptyMap<Long, Set<String>>())
    private val nextId = AtomicLong(0L)

    private val union: Flow<Set<String>> = interests
        .map { registered -> registered.values.flatMapTo(mutableSetOf()) { tickers -> tickers } }
        .distinctUntilChanged()
        .debounce(UNION_DEBOUNCE_MS)

    private val shared: StateFlow<Map<String, BigDecimal>> = merge(
        union.map { it.isEmpty() }.distinctUntilChanged().filter { it }.map { StoreEvent.Reset },
        feed.stream(union).map { StoreEvent.Feed(it) },
    )
        .scan(emptyMap<String, BigDecimal>()) { prices, storeEvent ->
            when (storeEvent) {
                StoreEvent.Reset -> emptyMap()
                is StoreEvent.Feed -> when (val event = storeEvent.event) {
                    is PerpsPriceEvent.Snapshot -> event.prices
                    PerpsPriceEvent.Reconnecting -> emptyMap()
                    PerpsPriceEvent.Rejected -> emptyMap()
                }
            }
        }
        .stateIn(Async.ioScope(), SharingStarted.WhileSubscribed(IDLE_TIMEOUT_MS), emptyMap())

    fun observe(tickers: Flow<Set<String>>): Flow<Map<String, BigDecimal>> = channelFlow {
        val id = nextId.incrementAndGet()
        launch {
            tickers.collect { requested -> interests.update { it + (id to requested) } }
        }
        try {
            shared.collect { send(it) }
        } finally {
            interests.update { it - id }
        }
    }

    private companion object {
        const val UNION_DEBOUNCE_MS = 150L
        const val IDLE_TIMEOUT_MS = 2_000L
    }
}
