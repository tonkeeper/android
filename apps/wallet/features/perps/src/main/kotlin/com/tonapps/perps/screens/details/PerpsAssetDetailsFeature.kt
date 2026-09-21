package com.tonapps.perps.screens.details

import com.tonapps.async.Async
import com.tonapps.blockchain.model.legacy.WalletType
import com.tonapps.mvi.contract.MviAction
import com.tonapps.mvi.contract.MviViewState
import com.tonapps.mvi.graph.GraphViewModel
import com.tonapps.mvi.graph.KResult
import com.tonapps.mvi.graph.KState
import com.tonapps.perps.data.PerpMarket
import com.tonapps.perps.data.PerpsAccountRefresh
import com.tonapps.perps.data.PerpsCandle
import com.tonapps.perps.data.PerpsChartChange
import com.tonapps.perps.data.PerpsChartFeedEvent
import com.tonapps.perps.data.PerpsChartFeedState
import com.tonapps.perps.data.PerpsChartMode
import com.tonapps.perps.data.PerpsChartSectionState
import com.tonapps.perps.data.PerpsChartSeries
import com.tonapps.perps.data.PerpsChartTimeframe
import com.tonapps.perps.data.PerpsError
import com.tonapps.perps.data.PerpsLimitOrder
import com.tonapps.perps.data.PerpsLivePrices
import com.tonapps.perps.data.PerpsPositionDetail
import com.tonapps.perps.data.PerpsPositionLevels
import com.tonapps.perps.data.PerpsPositionSection
import com.tonapps.perps.data.PerpsRepository
import com.tonapps.perps.data.PerpsTradingFlags
import com.tonapps.perps.data.PerpsTradingState
import com.tonapps.perps.data.changeIn
import com.tonapps.perps.data.onChartFeedEvent
import com.tonapps.perps.data.perpsTicker
import com.tonapps.perps.data.toPositionLevels
import com.tonapps.perps.data.withLiveMark
import com.tonapps.perps.data.withLivePrice
import com.tonapps.wallet.data.multichain.account.UnifiedAccountRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.flow.transformLatest

interface PerpsAssetDetailsViewState : MviViewState {
    val market: StateFlow<PerpMarket?>
    val about: StateFlow<String?>
    val isLoading: StateFlow<Boolean>
    val error: StateFlow<PerpsError?>
    val timeframe: StateFlow<PerpsChartTimeframe>
    val chartMode: StateFlow<PerpsChartMode>
    val chartCandles: StateFlow<List<PerpsCandle>>
    val chartState: StateFlow<PerpsChartSectionState>
    val chartStale: StateFlow<Boolean>
    val selectedCandle: StateFlow<PerpsCandle?>
    val selectedChange: StateFlow<PerpsChartChange?>
    val positionLevels: StateFlow<PerpsPositionLevels?>
    val priceDecimals: StateFlow<Int>
    val sizeDecimals: StateFlow<Int?>
    val positionSection: StateFlow<PerpsPositionSection>
    val flags: StateFlow<PerpsTradingFlags>
    val limitOrders: StateFlow<List<PerpsLimitOrder>>
}

sealed interface PerpsAssetDetailsAction : MviAction {
    data object Retry : PerpsAssetDetailsAction
    data class SelectTimeframe(val timeframe: PerpsChartTimeframe) : PerpsAssetDetailsAction
    data object ToggleChartMode : PerpsAssetDetailsAction
    data class SelectChartTime(val timeSec: Long?) : PerpsAssetDetailsAction
    data object RetryChart : PerpsAssetDetailsAction
    data class SetVisible(val visible: Boolean) : PerpsAssetDetailsAction
}

@OptIn(ExperimentalCoroutinesApi::class)
class PerpsAssetDetailsFeature(
    private val marketIndex: Int,
    val symbol: String,
    private val repository: PerpsRepository,
    private val livePrices: PerpsLivePrices,
    accountRefresh: PerpsAccountRefresh,
    unifiedAccountRepository: UnifiedAccountRepository,
) : GraphViewModel<PerpsAssetDetailsViewState, PerpsAssetDetailsAction>(),
    PerpsAssetDetailsViewState {

    private data class TradingContext(
        val walletId: String,
        val isMultichain: Boolean,
        val attempt: Int,
    )

    private data class PositionContext(
        val walletId: String,
        val isMultichain: Boolean,
        val attempt: Int,
        val tick: Int,
    )

    private data class ChartContext(
        val timeframe: PerpsChartTimeframe,
        val attempt: Int,
    )

    private val wallet = unifiedAccountRepository.selectedTonWalletFlow
        .cacheState()

    private val visible = actions
        .on<PerpsAssetDetailsAction.SetVisible>()
        .map { it.visible }
        .cacheState(initialValue = true)

    private val reloadAttempt = merge(
        actions.on<PerpsAssetDetailsAction.Retry>().map { },
        accountRefresh.events,
        visible.filter { it }.drop(1).map { },
    )
        .runningFold(0) { attempt, _ -> attempt + 1 }
        .cacheState(initialValue = 0)

    private val positionTick = combine(
        visible,
        reloadAttempt,
        transform = { isVisible, _ -> isVisible }
    )
        .flatMapLatest { isVisible ->
            if (isVisible) {
                flow {
                    while (true) {
                        delay(SILENT_REFRESH_MS)
                        emit(Unit)
                    }
                }
            } else {
                emptyFlow()
            }
        }
        .runningFold(0) { tick, _ -> tick + 1 }
        .cacheState(initialValue = 0)

    private val context: Flow<TradingContext> = combine(
        wallet,
        reloadAttempt,
        transform = { wallet, attempt ->
            val walletId = wallet?.id ?: return@combine null
            TradingContext(
                walletId = walletId,
                isMultichain = wallet.type == WalletType.Multichain,
                attempt = attempt,
            )
        }
    )
        .filterNotNull()
        .distinctUntilChanged()

    private val detailsState = reloadAttempt
        .transformStateLatest { repository.getMarketDetails(marketIndex) }
        .cacheStateWithLoading()

    private val details = detailsState
        .map { it.state?.getOrNull() }
        .cacheState(initialValue = null)

    private val tradingState = context
        .transformStateLatest { context ->
            if (context.isMultichain) {
                repository.getTradingState(marketIndex, context.walletId)
            } else {
                KResult.Ok<PerpsTradingState, PerpsError>(PerpsTradingState.PUBLIC)
            }
        }
        .cacheStateWithLoading()

    private val trading = tradingState
        .map { it.state?.getOrNull() }
        .cacheState(initialValue = null)

    private val positionState = combine(
        context,
        positionTick,
        transform = { context, tick ->
            PositionContext(
                walletId = context.walletId,
                isMultichain = context.isMultichain,
                attempt = context.attempt,
                tick = tick,
            )
        }
    )
        .distinctUntilChanged()
        .transformStateLatest { position ->
            if (position.isMultichain) {
                repository.getOpenPosition(marketIndex, position.walletId)
            } else {
                KResult.Ok<PerpsPositionDetail?, PerpsError>(null)
            }
        }
        .cacheStateWithLoading()

    private val tickerInterest = visible
        .map { isVisible ->
            if (isVisible) {
                setOf(perpsTicker(symbol))
            } else {
                emptySet()
            }
        }

    private val liveMarketPrice = livePrices
        .observe(tickerInterest)
        .map { it[perpsTicker(symbol)] }
        .cacheState(initialValue = null)

    override val market = combine(
        details,
        liveMarketPrice,
        transform = { value, live -> value?.market?.withLivePrice(live) }
    )
        .flowOn(Async.Io)
        .cacheState(initialValue = null)

    override val about = details
        .map { it?.about }
        .cacheState(initialValue = null)

    override val isLoading = combine(
        detailsState,
        tradingState,
        transform = { detailsLoad, tradingLoad ->
            (detailsLoad is KState.Loading && detailsLoad.state?.getOrNull() == null) ||
                (tradingLoad is KState.Loading && tradingLoad.state == null)
        }
    )
        .cacheState(initialValue = true)

    override val error = detailsState
        .map { ((it as? KState.Data)?.state as? KResult.Err)?.value }
        .cacheState(initialValue = null)

    override val positionSection = combine(
        positionState,
        liveMarketPrice,
        market,
        transform = { state, live, market ->
            val mark = live ?: market?.price
            when (val result = state.state) {
                is KResult.Ok -> {
                    val detail = result.value
                    if (detail != null) {
                        PerpsPositionSection.Open(
                            detail.copy(position = detail.position.withLiveMark(mark))
                        )
                    } else {
                        PerpsPositionSection.Hidden
                    }
                }

                is KResult.Err -> PerpsPositionSection.Failed(result.value)
                null -> PerpsPositionSection.Loading
            }
        }
    )
        .flowOn(Async.Io)
        .cacheState(initialValue = PerpsPositionSection.Loading)

    override val positionLevels = positionSection
        .map { (it as? PerpsPositionSection.Open)?.detail?.toPositionLevels() }
        .cacheState(initialValue = null)

    override val flags = tradingState
        .map { load ->
            when (val result = load.state) {
                is KResult.Err -> PerpsTradingFlags.NONE
                is KResult.Ok -> result.value.flags
                null -> PerpsTradingFlags.ALL_ENABLED
            }
        }
        .cacheState(initialValue = PerpsTradingFlags.ALL_ENABLED)

    override val limitOrders = trading
        .map { it?.limitOrders.orEmpty() }
        .cacheState(initialValue = emptyList<PerpsLimitOrder>())

    override val priceDecimals = details
        .map { it?.market?.priceDecimals ?: DEFAULT_PRICE_DECIMALS }
        .cacheState(initialValue = DEFAULT_PRICE_DECIMALS)

    override val sizeDecimals = details
        .map { it?.market?.sizeDecimals }
        .cacheState(initialValue = null)

    override val timeframe = actions
        .on<PerpsAssetDetailsAction.SelectTimeframe>()
        .map { it.timeframe }
        .cacheState(initialValue = PerpsChartTimeframe.H1)

    override val chartMode = actions
        .on<PerpsAssetDetailsAction.ToggleChartMode>()
        .runningFold(PerpsChartMode.CANDLE) { mode, _ ->
            if (mode == PerpsChartMode.CANDLE) {
                PerpsChartMode.LINE
            } else {
                PerpsChartMode.CANDLE
            }
        }
        .cacheState(initialValue = PerpsChartMode.CANDLE)

    private val selectedTime: StateFlow<Long?> = merge(
        actions.on<PerpsAssetDetailsAction.SelectChartTime>().map { it.timeSec },
        actions.on<PerpsAssetDetailsAction.SelectTimeframe>().map { null },
    )
        .cacheState(initialValue = null)

    private val chartRetryAttempt = actions
        .on<PerpsAssetDetailsAction.RetryChart>()
        .runningFold(0) { attempt, _ -> attempt + 1 }
        .cacheState(initialValue = 0)

    private val candlesState: StateFlow<KState<KResult<PerpsChartSeries, PerpsError>>> = combine(
        timeframe,
        chartRetryAttempt,
        transform = { timeframe, attempt -> ChartContext(timeframe, attempt) }
    )
        .distinctUntilChanged()
        .transformLatest { context ->
            emit(KState.Loading())
            var state = PerpsChartFeedState()
            // The verdict timer lives with the feed: time spent hidden must not count against it.
            val frames = visible.flatMapLatest { isVisible ->
                if (isVisible) {
                    merge(
                        repository.candles(symbol, context.timeframe),
                        flow<PerpsChartFeedEvent> {
                            delay(FIRST_FRAME_TIMEOUT_MS)
                            emit(PerpsChartFeedEvent.Timeout)
                        },
                    )
                } else {
                    emptyFlow()
                }
            }
            merge(
                frames,
                selectedTime.map { PerpsChartFeedEvent.Selection(it != null) }.distinctUntilChanged(),
            ).collect { event ->
                // Nothing here may suspend: superseded snapshots would queue up behind the wait.
                val step = state.onChartFeedEvent(event)
                state = step.state
                step.emission?.let { emit(KState.Data(it)) }
            }
        }
        .cacheStateWithLoading()

    override val chartCandles = candlesState
        .mapStateDataValueOrNull()
        .map { it?.getOrNull()?.candles.orEmpty() }
        .cacheState(initialValue = emptyList<PerpsCandle>())

    override val chartState = candlesState
        .map { state ->
            when (state) {
                is KState.Loading -> PerpsChartSectionState.LOADING
                is KState.Data -> when (val result = state.state) {
                    is KResult.Ok -> if (result.value.candles.isNotEmpty()) {
                        PerpsChartSectionState.READY
                    } else {
                        PerpsChartSectionState.EMPTY
                    }

                    is KResult.Err -> PerpsChartSectionState.FAILED
                }
            }
        }
        .cacheState(initialValue = PerpsChartSectionState.LOADING)

    override val chartStale = candlesState
        .mapStateDataValueOrNull()
        .map { it?.getOrNull()?.staleEpoch }
        .distinctUntilChanged()
        .transformLatest { epoch ->
            if (epoch == null) {
                emit(false)
            } else {
                emit(true)
                delay(STALE_LINGER_MS)
                emit(false)
            }
        }
        .cacheState(initialValue = false)

    override val selectedCandle = combine(
        chartCandles,
        selectedTime,
        transform = { candles, time -> candles.firstOrNull { it.time == time } }
    )
        .cacheState(initialValue = null)

    override val selectedChange = combine(
        chartCandles,
        selectedCandle,
        chartMode,
        transform = { candles, candle, mode -> candle?.changeIn(candles = candles, mode = mode) }
    )
        .cacheState(initialValue = null)

    private companion object {
        const val DEFAULT_PRICE_DECIMALS = 2
        const val FIRST_FRAME_TIMEOUT_MS = 15_000L
        const val SILENT_REFRESH_MS = 120_000L

        // Must outlast the socket's 30 s reconnect cap: a quiet market's reconnect pushes no frame.
        const val STALE_LINGER_MS = 90_000L
    }
}
