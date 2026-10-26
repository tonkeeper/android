package com.tonapps.perps.screens.details

import android.content.Context
import com.tonapps.async.Async
import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.blockchain.model.legacy.WalletType
import com.tonapps.chainkit.perpetual.PerpsTradeError
import com.tonapps.log.L
import com.tonapps.mvi.MviRelay
import com.tonapps.mvi.contract.MviAction
import com.tonapps.mvi.contract.MviViewState
import com.tonapps.mvi.flow.transformFirst
import com.tonapps.mvi.flow.withLatestFrom
import com.tonapps.mvi.graph.GraphViewModel
import com.tonapps.mvi.graph.KResult
import com.tonapps.mvi.graph.KState
import com.tonapps.perps.PerpsAnalytics
import com.tonapps.perps.data.PerpMarket
import com.tonapps.perps.data.PerpsAccountRefresh
import com.tonapps.perps.data.PerpsCandle
import com.tonapps.perps.data.PerpsChartChange
import com.tonapps.perps.data.PerpsChartFeedEvent
import com.tonapps.perps.data.PerpsChartFeedState
import com.tonapps.perps.data.PerpsChartMode
import com.tonapps.perps.data.PerpsChartPoint
import com.tonapps.perps.data.PerpsChartSectionState
import com.tonapps.perps.data.PerpsChartSeries
import com.tonapps.perps.data.PerpsChartTimeframe
import com.tonapps.perps.data.PerpsError
import com.tonapps.perps.data.PerpsLimitOrder
import com.tonapps.perps.data.PerpsMarketRules
import com.tonapps.perps.data.PerpsLivePrices
import com.tonapps.perps.data.PerpsOperationFailure
import com.tonapps.perps.data.PerpsOperationPill
import com.tonapps.perps.data.PerpsOperationPillState
import com.tonapps.perps.data.PerpsOrderSide
import com.tonapps.perps.data.PerpsPositionDetail
import com.tonapps.perps.data.PerpsPositionLevels
import com.tonapps.perps.data.PerpsPositionSection
import com.tonapps.perps.data.PerpsRepository
import com.tonapps.perps.data.PerpsSendReason
import com.tonapps.perps.data.PerpsSizeDirection
import com.tonapps.perps.data.PerpsTradingFlags
import com.tonapps.perps.data.PerpsTradingState
import com.tonapps.perps.data.changeFrom
import com.tonapps.perps.data.formatSlippagePercent
import com.tonapps.perps.data.formatUsdAmount
import com.tonapps.perps.data.maskIf
import com.tonapps.perps.data.onChartFeedEvent
import com.tonapps.perps.data.perpsTicker
import com.tonapps.perps.data.toPositionLevels
import com.tonapps.perps.data.withKnownLegs
import com.tonapps.perps.data.withLiveMark
import com.tonapps.perps.data.withLivePrice
import com.tonapps.perps.trade.ORDER_FILLED_STATUS
import com.tonapps.perps.trade.ORDER_GONE_STATUS
import com.tonapps.perps.trade.PerpsAccountService
import com.tonapps.perps.trade.PerpsPlanFailure
import com.tonapps.perps.trade.PerpsPlannerAdapter
import com.tonapps.perps.trade.PerpsProductRules
import com.tonapps.perps.trade.PerpsSubmitStart
import com.tonapps.perps.trade.PerpsTradeExecutor
import com.tonapps.perps.trade.PerpsTradeFailure
import com.tonapps.perps.trade.PerpsTradeRequest
import com.tonapps.perps.trade.PerpsUnlockFailure
import com.tonapps.perps.trade.failure
import com.tonapps.wallet.data.multichain.account.UnifiedAccountRepository
import com.tonapps.wallet.data.multichain.lighter.LighterSigningCredentials
import com.tonapps.wallet.data.multichain.perps.PerpsOperationKind
import com.tonapps.wallet.data.multichain.perps.PerpsOperationRecord
import com.tonapps.wallet.data.multichain.perps.PerpsOperationStatus
import com.tonapps.wallet.data.passcode.PasscodeManager
import com.tonapps.wallet.data.settings.SettingsRepository
import com.tonapps.wallet.localization.Localization
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.flow.runningReduce
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.math.BigDecimal
import java.util.UUID

interface PerpsAssetDetailsViewState : MviViewState {
    val market: StateFlow<PerpMarket?>
    val about: StateFlow<String?>
    val isLoading: StateFlow<Boolean>
    val error: StateFlow<PerpsError?>
    val tradingError: StateFlow<Boolean>
    val tradingRetrying: StateFlow<Boolean>
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
    val positionView: StateFlow<PerpsPositionView>
    val operation: StateFlow<PerpsOperationRecord?>
    val pill: StateFlow<PerpsOperationPill?>
    val failure: StateFlow<PerpsOperationFailure?>
    val flags: StateFlow<PerpsTradingFlags>
    val canTrade: StateFlow<Boolean>
    val canOpen: StateFlow<Boolean>
    val limitOrders: StateFlow<List<PerpsLimitOrder>>
    val rules: StateFlow<PerpsMarketRules?>
    val limitOrderBusy: StateFlow<Boolean>
    val proceeding: StateFlow<PerpsAssetDetailsTarget?>
    val historyRequest: StateFlow<PerpsMarketHistoryRequest?>
}

sealed interface PerpsAssetDetailsTarget {
    data class Order(val side: PerpsOrderSide) : PerpsAssetDetailsTarget
    data class CashOut(val side: PerpsOrderSide) : PerpsAssetDetailsTarget
    data class SizeChange(val side: PerpsOrderSide, val direction: PerpsSizeDirection) : PerpsAssetDetailsTarget
    data class MarginChange(val side: PerpsOrderSide, val add: Boolean) : PerpsAssetDetailsTarget
}

sealed interface PerpsAssetDetailsAction : MviAction {
    data object Retry : PerpsAssetDetailsAction
    data class SelectTimeframe(val timeframe: PerpsChartTimeframe) : PerpsAssetDetailsAction
    data object ToggleChartMode : PerpsAssetDetailsAction
    data class SelectChartPoint(val point: PerpsChartPoint?) : PerpsAssetDetailsAction
    data object RetryChart : PerpsAssetDetailsAction
    data object LoadOlderCandles : PerpsAssetDetailsAction
    data class SetVisible(val visible: Boolean) : PerpsAssetDetailsAction
    data class AcknowledgeFailure(val operationId: String) : PerpsAssetDetailsAction
    data class SetAutoCloseSheetOpen(val open: Boolean) : PerpsAssetDetailsAction
    data class AutoCloseSet(val detail: PerpsPositionDetail) : PerpsAssetDetailsAction

    sealed interface LimitOrderChange : PerpsAssetDetailsAction {
        val context: Context
        val orderIndex: Long
    }

    data class CancelLimitOrder(override val context: Context, override val orderIndex: Long) : LimitOrderChange

    data class ModifyLimitOrder(
        override val context: Context,
        override val orderIndex: Long,
        val price: BigDecimal,
    ) : LimitOrderChange

    data class Proceed(val context: Context, val target: PerpsAssetDetailsTarget) : PerpsAssetDetailsAction
}

@OptIn(ExperimentalCoroutinesApi::class)
class PerpsAssetDetailsFeature(
    private val marketIndex: Int,
    private val initialSymbol: String?,
    private val repository: PerpsRepository,
    private val livePrices: PerpsLivePrices,
    accountRefresh: PerpsAccountRefresh,
    private val executor: PerpsTradeExecutor,
    private val adapter: PerpsPlannerAdapter,
    private val accountService: PerpsAccountService,
    unifiedAccountRepository: UnifiedAccountRepository,
    passcodeManager: PasscodeManager,
    private val settingsRepository: SettingsRepository,
) : GraphViewModel<PerpsAssetDetailsViewState, PerpsAssetDetailsAction>(),
    PerpsAssetDetailsViewState {

    private data class TradingContext(
        val walletId: String,
        val isMultichain: Boolean,
        val attempt: Int,
    )

    private data class TradingRead(
        val result: KResult<PerpsTradingState, PerpsError>,
        val needsActivation: Boolean,
    )

    private data class ChartContext(
        val timeframe: PerpsChartTimeframe,
        val attempt: Int,
    )

    private val createdAtMs = System.currentTimeMillis()

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

    override val historyRequest = combine(
        wallet,
        reloadAttempt,
        transform = { wallet, attempt ->
            wallet
                ?.takeIf { it.type == WalletType.Multichain }
                ?.let { PerpsMarketHistoryRequest(it.id, marketIndex, attempt) }
        }
    )
        .cacheState(initialValue = null)

    private val hasRestingOrders = MutableStateFlow(false)

    private val positionTick = combine(
        visible,
        reloadAttempt,
        hasRestingOrders,
        transform = { isVisible, _, resting ->
            when {
                !isVisible -> null
                resting -> RESTING_REFRESH_MS
                else -> SILENT_REFRESH_MS
            }
        }
    )
        .flatMapLatest { period ->
            if (period != null) {
                flow {
                    while (true) {
                        delay(period)
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

    val symbol: String
        get() = initialSymbol ?: details.value?.market?.symbol.orEmpty()

    private val symbolFlow = initialSymbol?.let(::flowOf)
        ?: details.mapNotNull { it?.market?.symbol }.take(1)

    private val tradingLoad = combine(
        context,
        positionTick,
        transform = { context, tick -> context to tick }
    )
        .distinctUntilChanged()
        .runningFold(null as Pair<TradingContext, Boolean>?) { previous, (context, _) ->
            context to (previous?.first != context)
        }
        .filterNotNull()
        .transformStateLatest { (context, reissue) ->
            val read = if (context.isMultichain) {
                coroutineScope {
                    val orders = async { repository.getOpenOrders(marketIndex, context.walletId).getOrNull() }
                    // The trading screen's open orders flap empty while an order rests; /orders does not.
                    when (val state = adapter.tradingState(marketIndex, context.walletId, reissue = reissue)) {
                        is KResult.Ok -> TradingRead(
                            result = KResult.Ok(state.value.copy(limitOrders = orders.await() ?: state.value.limitOrders)),
                            needsActivation = false,
                        )
                        is KResult.Err -> {
                            val unactivated = state.value == PerpsError.RoTokenUnavailable &&
                                !accountService.isActivated(context.walletId)
                            TradingRead(
                                result = if (unactivated) {
                                    KResult.Ok(PerpsTradingState.PUBLIC)
                                } else {
                                    state
                                },
                                needsActivation = unactivated,
                            )
                        }
                    }
                }
            } else {
                TradingRead(KResult.Ok(PerpsTradingState.PUBLIC), needsActivation = false)
            }
            context to read
        }
        .runningReduce { previous, next ->
            val last = previous.state
            when {
                next is KState.Loading -> KState.Loading(last)
                last?.second?.result is KResult.Ok && last.first == next.state?.first && next.state?.second?.result is KResult.Err ->
                    KState.Data(last)
                else -> next
            }
        }
        .cacheStateWithLoading()

    private val tradingState = tradingLoad
        .map { load ->
            when (load) {
                is KState.Loading -> KState.Loading(load.state?.second?.result)
                is KState.Data -> KState.Data(load.state.second.result)
            }
        }
        .onEach { load ->
            load.state?.getOrNull()?.let { hasRestingOrders.value = it.limitOrders.isNotEmpty() }
        }
        .cacheStateWithLoading()

    private val needsActivation = tradingLoad
        .map { load -> load.state?.second?.needsActivation == true }
        .cacheState(initialValue = false)

    private val trading = tradingState
        .map { it.state?.getOrNull() }
        .cacheState(initialValue = null)

    private val positionState = context
        .transformLatest<TradingContext, KState<KResult<PerpsPositionDetail?, PerpsError>>> { context ->
            emit(KState.Loading())
            if (!context.isMultichain) {
                emit(KState.Data(KResult.Ok(null)))
                return@transformLatest
            }
            var last = repository.getOpenPosition(marketIndex, context.walletId)
            emit(KState.Data(last))
            var failures = 0
            while (true) {
                delay(POSITION_REFRESH_MS shl failures.coerceAtMost(POSITION_BACKOFF_MAX_SHIFT))
                visible.first { it }
                val next = repository.getOpenPosition(marketIndex, context.walletId)
                failures = if (next is KResult.Ok) {
                    0
                } else {
                    failures + 1
                }
                if (next is KResult.Ok || last is KResult.Err) {
                    emit(KState.Data(next))
                    last = next
                }
            }
        }
        .runningReduce { prev, next ->
            if (next is KState.Loading) {
                KState.Loading(prev.state)
            } else {
                next
            }
        }
        .cacheStateWithLoading()

    private val tickerInterest = combine(
        visible,
        symbolFlow,
        transform = { isVisible, symbol ->
            if (isVisible) {
                setOf(perpsTicker(symbol))
            } else {
                emptySet()
            }
        }
    )

    private val liveMarketPrice = combine(
        livePrices.observe(tickerInterest),
        symbolFlow,
        transform = { prices, symbol -> prices[perpsTicker(symbol)] }
    )
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
        .mapStateDataValueOrNull()
        .mapResultErrorOrNull()
        .map { it?.value }
        .cacheState(initialValue = null)

    private val errorReport = error
        .filterNotNull()
        .onEach { PerpsAnalytics.tradingDataError(marketIndex) }
        .cacheState(initialValue = null)

    override val tradingError = tradingState
        .map { load -> load.state is KResult.Err }
        .cacheState(initialValue = false)

    override val tradingRetrying = combine(
        tradingLoad,
        context,
        transform = { load, context ->
            val failed = load.state
            load is KState.Loading && failed?.second?.result is KResult.Err && failed.first.attempt != context.attempt
        }
    )
        .cacheState(initialValue = false)

    private val operationRecords = wallet
        .map { wallet -> wallet?.takeIf { it.type == WalletType.Multichain }?.id }
        .distinctUntilChanged()
        .flatMapLatest { walletId ->
            if (walletId != null) {
                executor.observe(walletId)
            } else {
                flowOf(emptyList())
            }
        }
        .map { records -> records.filter { it.marketIndex == marketIndex } }
        .cacheState(initialValue = emptyList<PerpsOperationRecord>())

    private val autoCloseApplied = operationRecords
        .mapNotNull { records ->
            records
                .filter { it.kind == PerpsOperationKind.AUTO_CLOSE && it.status == PerpsOperationStatus.SUCCEEDED }
                .maxOfOrNull { it.updatedAt }
                ?.takeIf { it >= createdAtMs }
        }
        .distinctUntilChanged()

    private val knownPosition = merge(
        positionState
            .filterAndMapStateData()
            .mapNotNull { it as? KResult.Ok }
            .map { KnownLegsEvent.Fetched(it.value) },
        autoCloseApplied.map { KnownLegsEvent.Reset },
        actions
            .on<PerpsAssetDetailsAction.AutoCloseSet>()
            .map { KnownLegsEvent.Seeded(it.detail) },
        context.map { it.walletId }.distinctUntilChanged().map { KnownLegsEvent.Reset },
    )
        .runningFold(null) { last: PerpsPositionDetail?, event ->
            when (event) {
                KnownLegsEvent.Reset -> null
                is KnownLegsEvent.Seeded -> event.detail
                is KnownLegsEvent.Fetched -> {
                    val detail = event.detail
                    if (detail?.autoCloseKnown != false) {
                        detail
                    } else {
                        last?.takeIf { it.position.id == detail.position.id }
                    }
                }
            }
        }
        .cacheState(initialValue = null)

    // A succeeded pill hides itself after TERMINAL_TTL_MS, so the list is re-read once that passes.
    private val operationTick = operationRecords
        .transformLatest { records ->
            emit(Unit)
            val terminal = records
                .filter { it.status == PerpsOperationStatus.SUCCEEDED }
                .maxOfOrNull { it.updatedAt }
            if (terminal != null) {
                val delayMs = terminal + TERMINAL_TTL_MS - System.currentTimeMillis()
                if (delayMs > 0) {
                    delay(delayMs)
                    emit(Unit)
                }
            }
        }

    override val operation = combine(
        operationRecords,
        operationTick,
        transform = { records, _ ->
            val now = System.currentTimeMillis()
            records
                .filter { it.isActive() || it.updatedAt >= now - TERMINAL_TTL_MS }
                .maxByOrNull { it.createdAt }
        }
    )
        .cacheState(initialValue = null)

    override val pill = combine(
        operation,
        symbolFlow,
        settingsRepository.hiddenBalancesFlow,
        transform = { operation, symbol, _ -> operation?.toPill(symbol, settingsRepository.hiddenBalances) }
    )
        .cacheState(initialValue = null)

    private val autoCloseSheetOpen = actions
        .on<PerpsAssetDetailsAction.SetAutoCloseSheetOpen>()
        .map { it.open }
        .cacheState(initialValue = false)

    override val failure = combine(
        operationRecords,
        executor.unnotifiedFailures,
        executor.unnotifiedUnknowns,
        combine(
            visible,
            passcodeManager.lockscreenHiddenFlow,
            autoCloseSheetOpen,
            transform = { isVisible, unlocked, sheetOpen -> isVisible && unlocked && !sheetOpen },
        ),
        transform = { records, failures, unknowns, shown ->
            if (shown) {
                records
                    .filter {
                        (it.status == PerpsOperationStatus.FAILED && it.operationId in failures) ||
                            (it.status == PerpsOperationStatus.UNKNOWN && it.operationId in unknowns)
                    }
                    .minByOrNull { it.updatedAt }
                    ?.let {
                        if (it.status == PerpsOperationStatus.FAILED) {
                            it.toFailure()
                        } else {
                            PerpsOperationFailure(
                                operationId = it.operationId,
                                text = Localization.perps_operation_status_unknown_toast,
                            )
                        }
                    }
            } else {
                null
            }
        }
    )
        .cacheState(initialValue = null)

    private val failureAck = actions
        .on<PerpsAssetDetailsAction.AcknowledgeFailure>()
        .onEach { executor.acknowledgeFailure(it.operationId) }
        .map { it.operationId }
        .cacheState(initialValue = null)

    override val positionSection = combine(
        positionState,
        liveMarketPrice,
        market,
        operation,
        knownPosition,
        transform = { state, live, market, operation, known ->
            val mark = live ?: market?.price
            when (val result = state.state) {
                is KResult.Ok -> {
                    val detail = result.value?.withKnownLegs(known)
                    if (detail != null) {
                        PerpsPositionSection.Open(
                            detail = detail.copy(position = detail.position.withLiveMark(mark)),
                            pnlPending = operation?.status == PerpsOperationStatus.IN_PROGRESS &&
                                operation.kind in PNL_PENDING_KINDS,
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

    private val actionFailureRelay = MviRelay<Int>()

    val actionFailures = actionFailureRelay.events

    private val limitOrderState = actions
        .on<PerpsAssetDetailsAction.LimitOrderChange>()
        .withLatestFrom(wallet) { action, wallet -> action to wallet }
        .transformStateFirst { (action, wallet) -> changeLimitOrder(action, wallet)?.let { actionFailureRelay.emit(it) } }
        .cacheStateWithEmpty()

    override val limitOrderBusy = limitOrderState
        .isLoading()
        .cacheState(initialValue = false)

    private val proceedRelay = MviRelay<PerpsAssetDetailsTarget>()

    val proceedEvents = proceedRelay.events

    private val proceedState = actions
        .on<PerpsAssetDetailsAction.Proceed>()
        .withLatestFrom(wallet) { action, wallet -> action to wallet }
        .withLatestFrom(needsActivation) { (action, wallet), needsActivation -> Triple(action, wallet, needsActivation) }
        .transformStateFirst { (action, wallet, needsActivation) ->
            proceed(action, wallet, needsActivation)?.let { actionFailureRelay.emit(it) }
        }
        .cacheStateWithEmpty()

    private val proceedTarget = actions
        .on<PerpsAssetDetailsAction.Proceed>()
        .map { it.target }
        .cacheState(initialValue = null)

    override val proceeding = combine(
        proceedTarget,
        proceedState.isLoading(),
        transform = { target, busy -> target.takeIf { busy } }
    )
        .cacheState(initialValue = null)

    override val canTrade = combine(
        context,
        operation,
        proceeding,
        transform = { context, operation, proceeding ->
            context.isMultichain && operation?.status != PerpsOperationStatus.IN_PROGRESS && proceeding == null
        }
    )
        .cacheState(initialValue = false)

    override val canOpen = combine(
        canTrade,
        tradingState,
        transform = { canTrade, load ->
            val allowed = when (val result = load.state) {
                is KResult.Ok -> !result.value.hasAccount || result.value.flags.openEnabled
                is KResult.Err -> false
                null -> true
            }

            canTrade && allowed
        }
    )
        .cacheState(initialValue = false)

    override val limitOrders = trading
        .map { it?.limitOrders.orEmpty() }
        .cacheState(initialValue = emptyList<PerpsLimitOrder>())

    override val rules = trading
        .map { it?.rules }
        .cacheState(initialValue = null)

    override val priceDecimals = details
        .map { it?.market?.priceDecimals ?: DEFAULT_PRICE_DECIMALS }
        .cacheState(initialValue = DEFAULT_PRICE_DECIMALS)

    override val sizeDecimals = details
        .map { it?.market?.sizeDecimals }
        .cacheState(initialValue = null)

    override val positionView = combine(
        positionSection,
        market,
        sizeDecimals,
        settingsRepository.hiddenBalancesFlow,
        transform = { section, market, decimals, _ ->
            PerpsPositionView(
                section = section,
                card = (section as? PerpsPositionSection.Open)?.detail?.toCardUi(
                    symbol = market?.symbol ?: initialSymbol.orEmpty(),
                    sizeDecimals = decimals,
                    hiddenBalance = settingsRepository.hiddenBalances,
                ),
            )
        }
    )
        .cacheState(
            initialValue = PerpsPositionView(
                section = PerpsPositionSection.Loading,
                card = null,
            ),
        )

    override val timeframe = actions
        .on<PerpsAssetDetailsAction.SelectTimeframe>()
        .map { it.timeframe }
        .cacheState(initialValue = PerpsChartTimeframe.M5)

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

    private val selectedPoint: StateFlow<PerpsChartPoint?> = merge(
        actions.on<PerpsAssetDetailsAction.SelectChartPoint>().map { it.point },
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
            val symbol = symbolFlow.first()
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
                        actions
                            .on<PerpsAssetDetailsAction.LoadOlderCandles>()
                            .transformFirst {
                                val oldest = state.held?.firstOrNull()?.time
                                if (oldest != null && !state.historyExhausted) {
                                    val older = repository.olderCandles(symbol, context.timeframe, oldest)
                                    if (older != null) {
                                        emit(PerpsChartFeedEvent.Older(older, endTimeSeconds = oldest))
                                    }
                                }
                            },
                    )
                } else {
                    emptyFlow()
                }
            }
            merge(
                frames,
                selectedPoint.map { PerpsChartFeedEvent.Selection(it != null) }.distinctUntilChanged(),
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
        selectedPoint,
        transform = { candles, point -> candles.firstOrNull { it.time == point?.timeSec } }
    )
        .cacheState(initialValue = null)

    override val selectedChange = combine(
        chartCandles,
        selectedCandle,
        selectedPoint,
        chartMode,
        transform = { candles, candle, point, mode ->
            candle?.changeFrom(
                when (mode) {
                    PerpsChartMode.CANDLE -> candle.open
                    PerpsChartMode.LINE -> (candles.firstOrNull { it.time == point?.baseTimeSec } ?: candle).close
                }
            )
        }
    )
        .cacheState(initialValue = null)

    private suspend fun proceed(
        action: PerpsAssetDetailsAction.Proceed,
        wallet: WalletEntity?,
        needsActivation: Boolean,
    ): Int? {
        if (needsActivation) {
            wallet ?: return Localization.something_went_wrong
            when (val result = accountService.unlock(wallet, action.context)) {
                is KResult.Ok -> {
                    val reloaded = withTimeoutOrNull(FLAGS_RELOAD_TIMEOUT_MS) { reloadTrading() }
                        ?: return Localization.something_went_wrong
                    val flags = reloaded.getOrNull()?.flags ?: return null
                    if (!action.target.allowedBy(flags)) {
                        return null
                    }
                }

                is KResult.Err -> return if (result.value == PerpsUnlockFailure.CANCELLED) {
                    null
                } else {
                    Localization.something_went_wrong
                }
            }
        }
        proceedRelay.emit(action.target)
        return null
    }

    // The public read guessed ALL_ENABLED; the activated read carries the real flags.
    private suspend fun reloadTrading(): KResult<PerpsTradingState, PerpsError> {
        val attempt = reloadAttempt.value
        sendAction(PerpsAssetDetailsAction.Retry)
        val reloaded = tradingLoad
            .filterIsInstance<KState.Data<Pair<TradingContext, TradingRead>>>()
            .first { it.state.first.attempt > attempt }
        return reloaded.state.second.result
    }

    private suspend fun changeLimitOrder(action: PerpsAssetDetailsAction.LimitOrderChange, wallet: WalletEntity?): Int? {
        wallet ?: return Localization.perps_review_failed
        val credentials = when (val result = accountService.unlock(wallet, action.context)) {
            is KResult.Ok -> result.value
            is KResult.Err -> return if (result.value == PerpsUnlockFailure.CANCELLED) {
                null
            } else {
                Localization.perps_review_failed
            }
        }
        val request = when (action) {
            is PerpsAssetDetailsAction.CancelLimitOrder -> PerpsTradeRequest.OrderCancel(
                walletId = wallet.id,
                marketIndex = marketIndex,
                orderIndex = action.orderIndex,
            )

            is PerpsAssetDetailsAction.ModifyLimitOrder -> PerpsTradeRequest.OrderModify(
                walletId = wallet.id,
                marketIndex = marketIndex,
                orderIndex = action.orderIndex,
                price = action.price,
            )
        }

        return try {
            withContext(Async.Io) { planAndStart(request, credentials) }
        } catch (t: Throwable) {
            verifyError(t)
            L.e(t)
            Localization.perps_review_failed
        }
    }

    private suspend fun planAndStart(request: PerpsTradeRequest, credentials: LighterSigningCredentials): Int? {
        val operationId = UUID.randomUUID().toString()
        var result = adapter.plan(request, credentials, mark = null, operationId = operationId)
        // The trading screen intermittently answers with no open orders while the order rests.
        if (((result as? KResult.Err)?.value as? PerpsPlanFailure.Planner)?.kind == PerpsTradeError.OrderNotFound) {
            delay(ORDER_MISSING_DELAY_MS)
            result = adapter.plan(request, credentials, mark = null, operationId = operationId)
        }
        val planned = when (result) {
            is KResult.Ok -> result.value
            is KResult.Err -> return result.value.limitOrderFailureText()
        }

        return when (val start = executor.start(planned)) {
            is PerpsSubmitStart.Started -> null
            is PerpsSubmitStart.Blocked -> Localization.perps_operation_in_progress
            is PerpsSubmitStart.Failed -> if (start.failure == PerpsTradeFailure.OperationInProgress) {
                Localization.perps_operation_in_progress
            } else {
                Localization.perps_review_failed
            }

            is PerpsSubmitStart.Requote -> Localization.perps_review_failed
        }
    }

    private companion object {
        const val DEFAULT_PRICE_DECIMALS = 2
        const val ORDER_MISSING_DELAY_MS = 1_500L
        const val TERMINAL_TTL_MS = 5_000L
        const val FIRST_FRAME_TIMEOUT_MS = 15_000L
        const val FLAGS_RELOAD_TIMEOUT_MS = 15_000L
        const val SILENT_REFRESH_MS = 120_000L
        const val RESTING_REFRESH_MS = 5_000L
        const val POSITION_REFRESH_MS = 2_000L
        const val POSITION_BACKOFF_MAX_SHIFT = 4

        // Must outlast the socket's 30 s reconnect cap: a quiet market's reconnect pushes no frame.
        const val STALE_LINGER_MS = 90_000L
    }
}

private sealed interface KnownLegsEvent {
    data class Fetched(val detail: PerpsPositionDetail?) : KnownLegsEvent
    data class Seeded(val detail: PerpsPositionDetail) : KnownLegsEvent
    data object Reset : KnownLegsEvent
}

private val ORDER_KINDS = setOf(
    PerpsOperationKind.OPEN,
    PerpsOperationKind.OPEN_LIMIT,
    PerpsOperationKind.CLOSE,
    PerpsOperationKind.ADD,
    PerpsOperationKind.REDUCE,
)

private val VENUE_REFUSED_REASONS = setOf(
    PerpsSendReason.LIGHTER_REJECTED,
    PerpsSendReason.INTEGRATOR_NOT_APPROVED,
)

private val PNL_PENDING_KINDS = setOf(
    PerpsOperationKind.CLOSE,
    PerpsOperationKind.REDUCE,
    PerpsOperationKind.ADD,
    PerpsOperationKind.MARGIN_ADD,
    PerpsOperationKind.MARGIN_REMOVE,
)

private fun PerpsOperationRecord.isActive(): Boolean {
    return status == PerpsOperationStatus.IN_PROGRESS || status == PerpsOperationStatus.UNKNOWN
}

private fun PerpsOperationRecord.toPill(symbol: String, hiddenBalance: Boolean): PerpsOperationPill? {
    val state = when (status) {
        PerpsOperationStatus.IN_PROGRESS -> PerpsOperationPillState.IN_PROGRESS
        PerpsOperationStatus.SUCCEEDED -> PerpsOperationPillState.DONE
        PerpsOperationStatus.UNKNOWN,
        PerpsOperationStatus.FAILED,
        PerpsOperationStatus.PARTIALLY_FILLED -> return null
    }

    return PerpsOperationPill(
        kind = kind,
        state = state,
        symbol = symbol,
        side = PerpsOrderSide.entries.firstOrNull { it.name == side } ?: PerpsOrderSide.LONG,
        amount = maskIf(hiddenBalance) { formatUsdAmount(marginQuote.toBigDecimalOrNull() ?: BigDecimal.ZERO) },
        leverage = leverage,
    )
}

private fun PerpsOperationRecord.toFailure(): PerpsOperationFailure {
    val failure = failure()
    val order = kind in ORDER_KINDS

    return when {
        order && failure is PerpsTradeFailure.OrderRejected && failure.status.startsWith(SLIPPAGE_STATUS_PREFIX) ->
            PerpsOperationFailure(
                operationId = operationId,
                text = Localization.perps_review_order_not_placed_text,
                textArg = formatSlippagePercent(PerpsProductRules.PRICE_SLIPPAGE_PPM),
            )

        order && failure is PerpsTradeFailure.SendRefused && failure.reason in VENUE_REFUSED_REASONS ->
            PerpsOperationFailure(operationId = operationId, text = Localization.perps_operation_rejected_toast)

        else -> PerpsOperationFailure(operationId = operationId, text = failureText())
    }
}

internal fun PerpsOperationRecord.failureText(): Int {
    val generic = when (kind) {
        PerpsOperationKind.OPEN -> Localization.perps_operation_failed_toast

        PerpsOperationKind.OPEN_LIMIT -> Localization.perps_operation_place_failed_toast

        PerpsOperationKind.CLOSE -> Localization.perps_operation_close_failed_toast

        PerpsOperationKind.ADD,
        PerpsOperationKind.REDUCE,
        PerpsOperationKind.MARGIN_ADD,
        PerpsOperationKind.MARGIN_REMOVE,
        PerpsOperationKind.AUTO_CLOSE -> Localization.perps_operation_adjust_failed_toast

        PerpsOperationKind.ORDER_CANCEL -> Localization.perps_operation_cancel_failed_toast

        PerpsOperationKind.ORDER_MODIFY -> Localization.perps_operation_update_failed_toast
    }

    return when (val failure = failure()) {
        is PerpsTradeFailure.OrderRejected -> when {
            failure.status == ORDER_GONE_STATUS -> Localization.perps_limit_order_gone
            failure.status == ORDER_FILLED_STATUS -> Localization.perps_limit_order_filled_before_cancel
            else -> generic
        }

        PerpsTradeFailure.StaleInput -> Localization.perps_operation_price_changed_toast
        else -> generic
    }
}

private fun PerpsPlanFailure.limitOrderFailureText(): Int {
    if (this is PerpsPlanFailure.ClockSkew) {
        return Localization.perps_review_wrong_device_time
    }
    if (this !is PerpsPlanFailure.Planner) {
        return Localization.perps_review_failed
    }

    return when (kind) {
        PerpsTradeError.OrderNotFound -> Localization.perps_limit_order_gone

        PerpsTradeError.InvalidInput -> if (field == BASE_AMOUNT_FIELD) {
            Localization.perps_limit_order_too_small
        } else {
            Localization.perps_review_failed
        }

        PerpsTradeError.ExceedsPosition -> if (field == BASE_AMOUNT_FIELD) {
            Localization.perps_limit_order_too_large
        } else {
            Localization.perps_review_failed
        }

        PerpsTradeError.TradingDisabled -> Localization.perps_order_trading_disabled

        else -> Localization.perps_review_failed
    }
}

private const val SLIPPAGE_STATUS_PREFIX = "canceled-too-much-slippage"
private const val BASE_AMOUNT_FIELD = "baseAmount"

/*
 *                      Retry ──┐
 *      accountRefresh.events ──┼──► [fold] reloadAttempt
 *   SetVisible ──► visible ────┘
 *
 *                     visible ──┐
 *               reloadAttempt ──┼──► [flatMapLatest, fold] positionTick
 *            hasRestingOrders ──┘
 *
 *   selectedTonWalletFlow ──► wallet? ──┐
 *                       reloadAttempt ──┴──► [distinct] context
 *
 *         wallet? ──┐
 *   reloadAttempt ──┴──► historyRequest?
 *
 *                     reloadAttempt ──► [latest] detailsState
 *
 *                             context ──┐
 *                        positionTick ──┴──► [distinct, fold reissue, latest, keep last Ok] tradingLoad
 *
 *   tradingLoad [drop context] ──► tradingState ╌► hasRestingOrders
 *   tradingLoad ──► needsActivation
 *
 *                 tradingLoad ──┐
 *                     context ──┴──► tradingRetrying
 *
 *                             context ──┐
 *                            visible ⋯──┴──► [distinct, latest, poll 2 s + backoff, keep last Ok] positionState
 *
 *   detailsState ──► details? ──┬──► about?
 *                               ├──► priceDecimals
 *                               └──► sizeDecimals?
 *   detailsState [dataOrNull, errOrNull] ──► error? ──► errorReport
 *
 *                detailsState ──┐
 *                tradingState ──┴──► isLoading
 *
 *   tradingState ──┬──► flags
 *                  ├──► tradingError
 *                  └──► trading? ──┬──► limitOrders
 *                                  └──► rules?
 *
 *   LimitOrderChange, wallet? ⋯ ──► [first] limitOrderState ──┬──► limitOrderBusy
 *                                                            └──► actionFailures
 *
 *   Proceed ──► proceedTarget? ──────────────────────────────────────┐
 *   Proceed, wallet? ⋯, needsActivation ⋯ ──► [first] proceedState ──┼──► proceeding?
 *                                                                   ├──► proceedEvents
 *                                                                   ├──► actionFailures
 *                                                                   └╌► Retry
 *
 *   visible ──► tickerInterest ──► liveMarketPrice? ──┐
 *                                          details? ──┴──► market?
 *
 *   wallet? ──► operationRecords ──┬──► [latest] operationTick ──┐
 *                                  └─────────────────────────────┴──► operation?
 *
 *   operationRecords ──► [distinct] autoCloseApplied
 *
 *   positionState [dataOnly, okOnly] ──┐
 *                  autoCloseApplied ──┤
 *                      AutoCloseSet ──┤
 *          context [distinct walletId] ──┴──► [merge, fold] knownPosition?
 *
 *                  operation? ──┐
 *                  symbolFlow ──┼──► pill?
 *          hiddenBalancesFlow ──┘
 *
 *   SetAutoCloseSheetOpen ──► autoCloseSheetOpen
 *
 *             operationRecords ──┐
 *           unnotifiedFailures ──┤
 *           unnotifiedUnknowns ──┤
 *                      visible ──┼──► failure?
 *         lockscreenHiddenFlow ──┤
 *           autoCloseSheetOpen ──┘
 *
 *   AcknowledgeFailure ──► failureAck? ╌► unnotifiedFailures, unnotifiedUnknowns
 *
 *                        context ──┐
 *                     operation? ──┼──► canTrade ──┐
 *                    proceeding? ──┘ tradingState ──┴──► canOpen
 *
 *                  positionState ──┐
 *               liveMarketPrice? ──┤
 *                        market? ──┼──► positionSection ──► positionLevels?
 *                     operation? ──┤
 *                 knownPosition? ──┘
 *
 *          positionSection ──┐
 *                  market? ──┤
 *            sizeDecimals? ──┼──► positionView
 *       hiddenBalancesFlow ──┘
 *
 *              SelectTimeframe ──► timeframe ──┐
 *   RetryChart ──► [fold] chartRetryAttempt ───┼──► [distinct, latest] candlesState
 *                                    visible ──┤
 *                             selectedPoint? ──┤
 *                  LoadOlderCandles [first] ───┘
 *
 *   candlesState ──┬──► chartCandles
 *                  ├──► chartState
 *                  └──► chartStale
 *
 *   SelectChartPoint / SelectTimeframe ──► selectedPoint? ──┐
 *                                            chartCandles ──┴──► selectedCandle?
 *
 *                                            chartCandles ──┐
 *                                         selectedCandle? ──┤
 *                                          selectedPoint? ──┼──► selectedChange?
 *                                               chartMode ──┘
 *
 *   ToggleChartMode ──► [fold] chartMode
 */

private fun PerpsAssetDetailsTarget.allowedBy(flags: PerpsTradingFlags): Boolean {
    return when (this) {
        is PerpsAssetDetailsTarget.Order -> flags.openEnabled
        is PerpsAssetDetailsTarget.CashOut -> flags.closeEnabled
        is PerpsAssetDetailsTarget.SizeChange -> when (direction) {
            PerpsSizeDirection.ADD -> flags.openEnabled
            PerpsSizeDirection.REDUCE -> flags.closeEnabled
        }

        is PerpsAssetDetailsTarget.MarginChange -> if (add) {
            flags.addMarginEnabled
        } else {
            flags.removeMarginEnabled
        }
    }
}